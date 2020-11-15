package boneless

enum class UnaryOperators(val token: Keyword, val rewrite: String) {
    Minus(Keyword.Minus, "minus"),

    Not(Keyword.Not, "not"),
    Reference(Keyword.Reference, "ref"),
    Dereference(Keyword.Dereference, "deref"),
}

enum class BinaryOperators(val token: Keyword, val priority: Int, val rewrite: String?) {
    Map(Keyword.Map, 0, null),
    Ascription(Keyword.TypeAnnotation, 80, null),
    Application(Keyword.None, 100, null),

    Plus(Keyword.Plus, 40, "plus"),
    Minus(Keyword.Minus, 40, "minus"),
    Multiply(Keyword.Multiply, 80, "multiply"),
    Divide(Keyword.Divide, 80, "divide"),
    Modulo(Keyword.Modulo, 80, "modulo"),

    InfEq(Keyword.InfEq, 20, "infeq"),
    Inf(Keyword.Inf, 20, "inf"),
    Eq(Keyword.Eq, 20, "eq"),

    And(Keyword.And, 80, "and"),
    Or(Keyword.Or, 40, "or"),
}

class Parser(private val inputAsText: String, private val tokens: List<Tokenizer.Token>) {
    private var i = 0

    private fun unexpectedToken(expected: String? = null): Nothing {
        throw Exception("Unexpected token '${front.tokenName}' at $here" + (expected?.let { ", expected $it" } ?: ""))
    }

    private fun expectedToken(expected: String): Nothing {
        throw Exception("Expected token '$expected' at $here but got $front")
    }

    private fun expected(expected: String): Nothing {
        throw Exception("Expected $expected at $here but got $front")
    }

    private val pos: Tokenizer.Pos
        get() = front.pos

    private val here: String
        get() = "${front.pos}, starting with \"${
            inputAsText.substring(
                front.pos.absolute,
                Math.min(inputAsText.length, front.pos.absolute + 10)
            )
        }...\""

    private val front: Tokenizer.Token
        get() = tokens[i]

    private fun accept(tokenName: String): Boolean {
        if (i >= tokens.size) return false
        if (tokens[i].tokenName == tokenName) {
            i++
            return true
        }
        return false
    }

    private fun eatIdentifier(): String {
        val id = eat("Identifier")
        return id.payload!!
    }

    private fun eat(tokenName: String): Tokenizer.Token {
        if (!accept(tokenName)) {
            throw Exception("expected '$tokenName' at $here but got '${front.tokenName}'")
        }
        return tokens[i - 1]
    }

    private fun eat(): Tokenizer.Token {
        i++
        return tokens[i - 1]
    }

    fun parseProgram(): Expression.Sequence {
        val p = parseInstructionSequence()
        if (front.tokenName != "EOF")
            expectedToken("EOF")
        return p
    }

    private fun parseInstructionSequence(): Expression.Sequence {
        val instructions = mutableListOf<Instruction>()
        while (true) {
            instructions += parseInstruction() ?: break
        }
        val yieldValue = parseExpression(0)
        return Expression.Sequence(instructions, yieldValue)
    }

    private fun parseInstruction(): Instruction? {
        when {
            accept("def") -> {
                val identifier = eatIdentifier()
                val type = acceptTypeAnnotation()
                eat("::")
                val body = parseExpression(0) ?: unexpectedToken("expression")
                eat(";")
                return Instruction.Def(identifier, emptyList(), type, body)
            }
            accept("let") -> {
                val identifier = eatIdentifier()
                val type = acceptTypeAnnotation()
                eat("=")
                val rhs = parseExpression(0) ?: unexpectedToken("expression")
                eat(";")
                return Instruction.Let(identifier, false, type, rhs)
            }
            accept("var") -> {
                val identifier = eatIdentifier()
                val type = acceptTypeAnnotation()
                val defaultValue = if (accept("=")) parseExpression(0) else null
                eat(";")
                return Instruction.Var(identifier, type, defaultValue)
            }
        }
        return null
    }

    private fun acceptTypeAnnotation(): Expression? {
        if (accept(":")) {
            return parseExpression(0)
        }
        return null
    }

    private fun parsePrimaryExpression(): Expression? {
        when {
            front.tokenName == "StringLit" -> {
                val nom = eat(); return Expression.StringLit(nom.payload!!); }
            front.tokenName == "NumLit" -> {
                val nom = eat(); return Expression.NumLit(nom.payload!!); }
            front.tokenName == "Identifier" -> {
                val id = eatIdentifier()
                return Expression.RefSymbol(id)
            }
            accept("-") -> {
                // Minus is bullshit
                if (front.tokenName == "NumLit") {
                    val nom = eat()
                    return Expression.NumLit("-" + nom.payload!!)
                } else throw Exception("This is explicitly disallowed, '-' is not a valid identifier.")
            }
            accept("if") -> {
                val condition = parseExpression(0)!!
                eat("then")
                val ifTrue = parseExpression(0)!!
                eat("else")
                val ifFalse = parseExpression(0)!!
                return Expression.Conditional(condition, ifTrue, ifFalse)
            }
            accept("(") -> {
                val inside = parseExpression(0)
                return if (inside == null) {
                    eat(")")
                    return Expression.Unit
                } else {
                    if (accept(")")) {
                        inside
                    } else {
                        val tuple = mutableListOf<Expression>()
                        tuple += inside
                        while (true) {
                            if (accept(")")) {
                                return Expression.Tuple(tuple)
                            }
                            eat(",")
                            tuple += parseExpression(0)!!
                        }
                        null // don't worry about it
                    }
                }
            }
            accept("{") -> {
                val seq = parseInstructionSequence()
                eat("}")
                return seq
            }
            else -> return null
        }
    }

    private fun parseUnaryOp(): Expression? {
        for (unop in UnaryOperators.values()) {
            if (unop.token == Keyword.Minus && i + 1 < tokens.size && tokens[i + 1].tokenName == "NumLit")
                continue
            if (accept(unop.token.symbol)) {
                return Expression.Invocation(listOf(Expression.RefSymbol(unop.rewrite), parseUnaryOp()!!))
            }
        }
        return parsePrimaryExpression()
    }

    private fun Expression.canBePattern() = when(this) {
        Expression.Unit -> true
        is Expression.StringLit -> true
        is Expression.NumLit -> true
        is Expression.RefSymbol -> true
        is Expression.Tuple -> true
        is Expression.Invocation -> true
        is Expression.Function -> false
        is Expression.Ascription -> true
        is Expression.Sequence -> false
        is Expression.Conditional -> false
    }

    private fun parseExpression(priority: Int): Expression? {
        var first = parseUnaryOp() ?: return null

        outerBinop@
        while (true) {
            for (binop in BinaryOperators.values()) {
                if ((binop.priority >= priority)) {
                    if (binop == BinaryOperators.Application && binop.priority > priority) {
                        val following = parseExpression(binop.priority) ?: continue
                        first = when (val oldfirst = first) {
                            is Expression.Invocation -> Expression.Invocation(oldfirst.arguments + listOf(following))
                            else -> Expression.Invocation(listOf(first, following))
                        }
                        continue@outerBinop
                    }
                    else if (accept(binop.token.symbol)) {
                        val rhs = parseExpression(binop.priority)!!
                        when (binop) {
                            BinaryOperators.Ascription -> {
                                first = Expression.Ascription(first, rhs)
                            }
                            BinaryOperators.Map -> {
                                val oldfirst = first
                                first = when {
                                    oldfirst is Expression.Invocation -> Expression.Function(oldfirst.arguments, rhs)
                                    oldfirst.canBePattern() -> Expression.Function(listOf(oldfirst), rhs)
                                    else -> expected("Expected operands before =>")
                                }
                            }
                            else -> {
                                first = Expression.Invocation(listOf(Expression.RefSymbol(binop.rewrite!!), first, rhs))
                            }
                        }
                        continue@outerBinop
                    }
                }
            }
            break
        }

        return first
    }
}