package boneless

import boneless.Tokenizer.Token

// Priority * 80
// Priority + 40
enum class UnaryOperators(val token: Tokenizer.Keyword, val rewrite: String) {
    Minus(Tokenizer.Keyword.Minus, "minus"),
}

enum class BinaryOperators(val token: Tokenizer.Keyword, val priority: Int, val rewrite: String) {
    Plus(Tokenizer.Keyword.Plus, 40, "plus"),
    Minus(Tokenizer.Keyword.Minus, 40, "minus"),
    Multiply(Tokenizer.Keyword.Multiply, 80, "multiply"),
    Divide(Tokenizer.Keyword.Divide, 80, "divide"),
    Modulo(Tokenizer.Keyword.Modulo, 80, "modulo"),

    InfEq(Tokenizer.Keyword.InfEq, 20, "infeq"),
    Inf(Tokenizer.Keyword.Inf, 20, "inf"),
    Eq(Tokenizer.Keyword.Eq, 20, "eq"),
}

class Parser(val inputAsText: String, val tokens: List<Token>) {
    var i = 0

    fun unexpectedToken(expected: String? = null): Nothing {
        throw Exception("Unexpected token '${front.tokenName}' at $here" + (expected?.let { ", expected $it" } ?: ""))
    }

    fun expected(expected: String): Nothing {
        throw Exception("Expected token '${expected}' at $here but got $front")
    }

    val pos: Tokenizer.Pos
        get() = front.pos

    val here: String
        get() = "${front.pos}, starting with \"${
            inputAsText.substring(
                front.pos.absolute,
                Math.min(inputAsText.length, front.pos.absolute + 10)
            )
        }...\""
    //get() = "${front.pos}, starting with \"${inputAsText.substring(front.pos.absolute, Math.min(inputAsText.length, front.pos.absolute + 10))}...\""

    val front: Token
        get() = tokens[i]

    fun accept(tokenName: String): Boolean {
        if (i >= tokens.size) return false
        if (tokens[i].tokenName == tokenName) {
            i++
            return true
        }
        return false
    }

    fun eatIdentifier(): String {
        val id = eat("Identifier")
        return id.payload!!
    }

    fun eat(tokenName: String): Token {
        if (!accept(tokenName)) {
            throw Exception("expected '$tokenName' at $here but got '${front.tokenName}'")
        }
        return tokens[i - 1]
    }

    fun eat(): Token {
        i++
        return tokens[i - 1]
    }

    fun parseProgram(): Expression.Sequence {
        val p = parseInstructionSequence()
        if (front.tokenName != "EOF")
            expected("EOF")
        return p
    }

    fun parseInstructionSequence(): Expression.Sequence {
        val instructions = mutableListOf<Instruction>()
        while (true) {
            instructions += parseInstruction() ?: break
        }
        val yieldValue = parseExpression(0)
        return Expression.Sequence(instructions, yieldValue)
    }

    fun parseInstruction(): Instruction? {
        when {
            accept("def") -> {
                val identifier = eatIdentifier()
                val type = parseTypeAnnotation()
                eat("::")
                val body = parseExpression(0) ?: unexpectedToken("expression")
                eat(";")
                return Instruction.Def(identifier, emptyList(), type, body)
            }
            accept("let") -> {
                val identifier = eatIdentifier()
                val type = parseTypeAnnotation()
                eat("=")
                val rhs = parseExpression(0) ?: unexpectedToken("expression")
                eat(";")
                return Instruction.Let(identifier, false, type, rhs)
            }
            accept("var") -> {
                val identifier = eatIdentifier()
                val type = parseTypeAnnotation()
                val defaultValue = if (accept("=")) parseExpression(0) else null
                eat(";")
                return Instruction.Var(identifier, type, defaultValue)
            }
        }
        return null
    }

    fun parseTypeAnnotation(): Expression? {
        if (accept(":")) {
            return parseExpression(0)
        }
        return null
    }

    fun parseExpressionHead(): Expression? {
        val ret = when {
            front.tokenName == "StringLit" -> {
                val nom = eat(); return Expression.StringLit(nom.payload!!); }
            front.tokenName == "NumLit" -> {
                val nom = eat(); return Expression.NumLit(nom.payload!!); }
            front.tokenName == "Identifier" -> {
                val id = eatIdentifier()
                Expression.RefSymbol(id)
            }
            accept("-") -> {
                // Minus is bullshit
                if (front.tokenName == "NumLit") {
                    val nom = eat()
                    return Expression.NumLit("-" + nom.payload!!)
                }
                else throw Exception("This is explicitly disallowed, '-' is not a valid identifier.")
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
                if (inside == null) {
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
                seq
            }
            else -> return null
        }
        return ret
    }

    fun parseUnaryOp(): Expression? {
        for (unop in UnaryOperators.values()) {
            if (unop.token == Tokenizer.Keyword.Minus && i + 1 < tokens.size && tokens[i+1].tokenName == "NumLit")
                continue
            if (accept(unop.token.symbol)) {
                return Expression.Invocation(Expression.RefSymbol(unop.rewrite), listOf(parseUnaryOp()!!))
            }
        }
        return parseExpressionHead()
    }

    // fn_a arg0 arg1
    fun parseExpression(priority: Int): Expression? {
        var first = parseUnaryOp() ?: return null

        outerBinop@
        while (true) {
            for (binop in BinaryOperators.values()) {
                if ((binop.priority >= priority) && accept(binop.token.symbol)) {
                    val rhs = parseExpression(binop.priority)!!
                    first = Expression.Invocation(Expression.RefSymbol(binop.rewrite), listOf(first, rhs))
                    continue@outerBinop
                }
            }
            break
        }

        val following = mutableListOf<Expression>()
        // You either have an invocation with parameters such as
        // foo a b c
        // Or a parameter-less invoke with a type annotation
        // foo: I32 -> I32 -> I32 -> I32
        // if you want both you do
        // (foo: I32 -> I32 -> I32 -> I32) a b c
        val typeAnnotation = if (priority == 0) parseTypeAnnotation() else null
        if (typeAnnotation == null) {
            while (true) {
                val arg = parseExpressionHead() ?: break
                following += arg
            }
        } else {
            first = Expression.Ascription(first, typeAnnotation)
        }

        if (accept("=>")) {
            val body = parseExpression(0)!!
            return Expression.Function(listOf(first) + following, body)
        }

        if (following.isEmpty())
            return first
        return Expression.Invocation(first, following)
    }
}