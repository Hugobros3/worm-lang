package boneless

enum class PrefixSymbol(val token: Keyword, val rewrite: String) {
    Minus(Keyword.Minus, "minus"),

    Not(Keyword.Not, "not"),
    Reference(Keyword.Reference, "ref"),
    Dereference(Keyword.Dereference, "deref"),
}

enum class InfixSymbol(val token: Keyword, val priority: Int, val rewrite: String?) {
    Map(Keyword.Map, 0, null),
    Ascription(Keyword.TypeAnnotation, 80, null),

    Plus(Keyword.Plus, 40, "plus"),
    Minus(Keyword.Minus, 40, "minus"),
    Multiply(Keyword.Multiply, 60, "multiply"),
    Divide(Keyword.Divide, 60, "divide"),
    Modulo(Keyword.Modulo, 60, "modulo"),

    InfEq(Keyword.InfEq, 20, "infeq"),
    Inf(Keyword.Inf, 20, "inf"),
    Eq(Keyword.Eq, 20, "eq"),

    And(Keyword.And, 80, "and"),
    Or(Keyword.Or, 40, "or"),

    Application(Keyword.None, 100, null),
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

    private fun expect(tokenName: String): Tokenizer.Token {
        if (!accept(tokenName)) {
            throw Exception("expected '$tokenName' at $here but got '${front.tokenName}'")
        }
        return tokens[i - 1]
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

    private fun expectIdentifier(): String {
        val id = expect("Identifier")
        return id.payload!!
    }

    private fun eatIdentifier(): String {
        val id = eat("Identifier")
        return id.payload!!
    }

    private fun parseSequenceContents(): Expression.Sequence {
        val instructions = mutableListOf<Instruction>()
        while (true) {
            instructions += acceptInstruction() ?: break
        }
        val yieldValue = acceptExpression(0)
        return Expression.Sequence(instructions, yieldValue)
    }

    private fun acceptInstruction(): Instruction? {
        when {
            accept("def") -> {
                val identifier = expectIdentifier()
                val type = acceptTypeAnnotation()
                expect("::")
                val body = acceptExpression(0) ?: unexpectedToken("expression")
                expect(";")
                return Instruction.Def(identifier, emptyList(), type, body)
            }
            accept("let") -> {
                val identifier = expectIdentifier()
                val type = acceptTypeAnnotation()
                expect("=")
                val rhs = acceptExpression(0) ?: unexpectedToken("expression")
                expect(";")
                return Instruction.Let(identifier, false, type, rhs)
            }
            accept("var") -> {
                val identifier = expectIdentifier()
                val type = acceptTypeAnnotation()
                val defaultValue = if (accept("=")) acceptExpression(0) else null
                expect(";")
                return Instruction.Var(identifier, type, defaultValue)
            }
        }
        return null
    }

    private fun acceptTypeAnnotation(): Type? {
        if (accept(":")) {
            return eatType()
        }
        return null
    }

    fun eatType(insideBrackets: Boolean = false, insideTuple: Boolean = false): Type {
        val un_normalized = if (accept("[")) {
            if (accept("]"))
                Type.TupleType(emptyList(), null)
            else {
                val inside = eatType(insideBrackets = true)
                eat("]")
                inside
            }
        } else if (front.tokenName == "Identifier") {
            val id = eatIdentifier()
            if (insideBrackets && accept("::")) {
                val type = eatType()
                // Either a struct/enum type
                if (front.tokenName == "|") {
                    // Is an enum type
                    val elements = mutableListOf(type)
                    val names = mutableListOf(id)
                    while (accept("|")) {
                        names += eatIdentifier()
                        eat("::")
                        elements += eatType()
                    }
                    Type.EnumType(elements, names)
                } else {
                    val elements = mutableListOf(type)
                    val names = mutableListOf(id)
                    while (accept(",")) {
                        names += eatIdentifier()
                        eat("::")
                        elements += eatType()
                    }
                    Type.TupleType(elements, names)
                }
            } else if (insideBrackets && accept("^")) {
                // definite array
                val type = Type.TypeApplication(id, emptyList())
                val size = eat("NumLit").payload!!.toInt()
                if (size == 0)
                    throw Exception("Zero sized arrays are not supported")
                Type.ArrayType(type, size)
            } else if (insideBrackets && accept("..")) {
                // indefinite array
                val type = Type.TypeApplication(id, emptyList())
                Type.ArrayType(type, -1)
            } else if (front.tokenName == "*" && !insideTuple) {
                // tuple type
                val type = Type.TypeApplication(id, emptyList())
                val elements = mutableListOf<Type>(type)
                while (accept("*")) {
                    elements += eatType(insideTuple = true)
                }
                Type.TupleType(elements, null)
            } else {
                // type application
                val ops = mutableListOf<Expression>()
                while (true) {
                    val expr = acceptExpression(0) ?: break
                    ops += expr
                }
                Type.TypeApplication(id, ops)
            }
        } else {
            throw Exception("Not a type")
        }
        return un_normalized.normalize()
    }

    private fun acceptPrimaryExpression(): Expression? {
        when {
            front.tokenName == "StringLit" -> {
                val nom = eat(); return Expression.StringLit(nom.payload!!); }
            front.tokenName == "NumLit" -> {
                val nom = eat(); return Expression.NumLit(nom.payload!!); }
            front.tokenName == "Identifier" -> {
                val id = eatIdentifier()
                return Expression.RefSymbol(id)
            }
            accept("if") -> {
                val condition = acceptExpression(0)!!
                expect("then")
                val ifTrue = acceptExpression(0)!!
                expect("else")
                val ifFalse = acceptExpression(0)!!
                return Expression.Conditional(condition, ifTrue, ifFalse)
            }
            accept("(") -> {
                val inside = acceptExpression(0)
                return if (inside == null) {
                    expect(")")
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
                            expect(",")
                            tuple += acceptExpression(0)!!
                        }
                        null // don't worry about it
                    }
                }
            }
            accept("[") -> {
                val type = eatType(insideBrackets = true)
                val e = Expression.QuoteType(type)
                eat("]")
                return e
            }
            accept("{") -> {
                val seq = parseSequenceContents()
                expect("}")
                return seq
            }
            else -> return null
        }
    }

    private fun acceptPrefixedPrimaryExpr(): Expression? {
        for (prefix in PrefixSymbol.values()) {
            //if (prefix.token == Keyword.Minus && i + 1 < tokens.size && tokens[i + 1].tokenName == "NumLit")
            //    continue
            if (accept(prefix.token.str)) {
                return Expression.Invocation(
                    listOf(
                        Expression.RefSymbol(prefix.rewrite),
                        acceptPrefixedPrimaryExpr()!!
                    )
                )
            }
        }
        return acceptPrimaryExpression()
    }

    private fun Expression.canBePattern() = when (this) {
        Expression.Unit -> true
        is Expression.StringLit -> true
        is Expression.NumLit -> true
        is Expression.QuoteType -> true
        is Expression.RefSymbol -> true
        is Expression.Tuple -> true
        is Expression.Invocation -> true
        is Expression.Function -> false
        is Expression.Ascription -> true
        is Expression.Sequence -> false
        is Expression.Conditional -> false
    }

    private fun expectExpression(priority: Int) = acceptExpression(priority) ?: expected("expression")

    private fun acceptExpression(priority: Int): Expression? {
        var accumulator = acceptPrefixedPrimaryExpr() ?: return null

        outerBinop@
        while (true) {
            for (infix in InfixSymbol.values()) {
                if ((infix.priority >= priority)) {
                    if (infix == InfixSymbol.Application) {
                        val following = acceptPrimaryExpression() ?: continue
                        accumulator = when (val oldfirst = accumulator) {
                            is Expression.Invocation -> Expression.Invocation(oldfirst.arguments + listOf(following))
                            else -> Expression.Invocation(listOf(accumulator, following))
                        }
                        continue@outerBinop
                    } else if (accept(infix.token.str)) {
                        when (infix) {
                            InfixSymbol.Ascription -> {
                                val type = eatType()
                                accumulator = Expression.Ascription(accumulator, type)
                            }
                            InfixSymbol.Map -> {
                                val rhs = expectExpression(infix.priority)
                                val oldfirst = accumulator
                                accumulator = when {
                                    oldfirst is Expression.Invocation -> Expression.Function(oldfirst.arguments, rhs)
                                    oldfirst.canBePattern() -> Expression.Function(listOf(oldfirst), rhs)
                                    else -> expected("Expected operands before =>")
                                }
                            }
                            else -> {
                                val rhs = expectExpression(infix.priority)
                                accumulator = Expression.Invocation(
                                    listOf(
                                        Expression.RefSymbol(infix.rewrite!!),
                                        accumulator,
                                        rhs
                                    )
                                )
                            }
                        }
                        continue@outerBinop
                    }
                }
            }
            break
        }

        return accumulator
    }

    fun parseProgram(): Expression.Sequence {
        val p = parseSequenceContents()
        if (front.tokenName != "EOF")
            expectedToken("EOF")
        return p
    }
}