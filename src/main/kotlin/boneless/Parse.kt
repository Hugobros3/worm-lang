package boneless

enum class PrefixSymbol(val token: Keyword, val rewrite: String) {
    Minus(Keyword.Minus, "minus"),

    Not(Keyword.Not, "not"),
    Reference(Keyword.Reference, "ref"),
    Dereference(Keyword.Dereference, "deref"),
}

enum class InfixSymbol(val token: Keyword, val priority: Int, val rewrite: String?) {
    // Map(Keyword.Map, 0, null),
    Ascription(Keyword.TypeAnnotation, 80, null),
    Cast(Keyword.As, 80, null),

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
    // no speculative parsing yay :)
    //private val stack = mutableListOf<Int>()
    //fun push() { stack.add(0, i) }
    //fun pop() { i = stack.removeAt(0) }

    private fun unexpectedToken(expected: String? = null): Nothing {
        throw Exception("Unexpected token '${front.tokenName}' at $here" + (expected?.let { ", expected $it" } ?: ""))
    }

    private fun expectedToken(expected: String): Nothing {
        throw Exception("Expected token '$expected' at $here but got $front")
    }

    private fun expected(expected: String): Nothing {
        throw Exception("Expected $expected at $here")
    }

    //private val pos: Tokenizer.Pos
    //    get() = front.pos

    private val here: String
        get() = front.pos.toString() + ", before \"" + inputAsText.substring(
            front.pos.absolute,
            Math.min(inputAsText.length, front.pos.absolute + 10)
        ) + if (front.pos.absolute + 10 < inputAsText.length) "...\"" else "\""

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

    private fun acceptToken(tokenName: String): Tokenizer.Token? {
        if (!accept(tokenName)) {
            return null
        }
        return tokens[i - 1]
    }

    private fun expect(tokenName: String): Tokenizer.Token {
        if (!accept(tokenName)) {
            throw Exception("expected '$tokenName' at $here but got '${front.tokenName}'")
        }
        return tokens[i - 1]
    }

    private fun eat(tokenName: String): Tokenizer.Token {
        if (!accept(tokenName)) {
            throw Exception("PARSER ISSUE: asserted '$tokenName' at $here but got '${front.tokenName}'")
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

    private fun expectNumericalLiteral(): String {
        val id = expect("NumLit")
        return id.payload!!
    }

    private fun eatIdentifier(): String {
        val id = eat("Identifier")
        return id.payload!!
    }

    private fun acceptIdentifier(): String? {
        val id = acceptToken("Identifier")
        return id?.payload
    }

    internal fun parseSequenceContents(): Expression.Sequence {
        val instructions = mutableListOf<Instruction>()
        var yieldValue: Expression? = null
        while (true) {
            when {
                accept("let") -> {
                    val identifier = expectIdentifier()
                    val type = acceptTypeAnnotation()
                    expect("=")
                    val rhs = acceptExpression(0) ?: unexpectedToken("expression")
                    expect(";")
                    instructions += Instruction.Let(identifier, false, type, rhs)
                }
                else -> {
                    yieldValue = acceptExpression(0) ?: break
                    if (accept(";")) {
                        instructions += Instruction.Evaluate(yieldValue)
                        yieldValue = null
                    } else
                        break
                }
            }
        }
        return Expression.Sequence(instructions, yieldValue)
    }

    private fun eatModule(): Module {
        val defs = mutableListOf<Def>()
        while (accept("def")) {
            val identifier = expectIdentifier()
            val annotatedType = acceptTypeAnnotation()
            expect("::")

            val body = when {
                accept("data") -> { Def.DefBody.DataCtor(expectType()) }
                accept("type") -> { Def.DefBody.TypeAlias(expectType()) }
                else -> { Def.DefBody.ExprBody(acceptExpression(0) ?: unexpectedToken("expression")) }
            }

            expect(";")
            defs += Def(identifier, emptyList(), annotatedType, body)
        }
        return Module(defs.toSet())
    }

    private fun acceptTypeAnnotation(): Type? {
        if (accept(":")) {
            return expectType()
        }
        return null
    }

    fun expectType(): Type {
        val id = acceptIdentifier() ?: return eatTypeBrackets()

        val prim_type = PrimitiveType.values().find { it.name == id }
        if (prim_type != null)
            return Type.PrimitiveType(prim_type)

        val ops = mutableListOf<Expression>()
        while (true) {
            ops += acceptExpression(0) ?: break
        }

        return Type.TypeApplication(id, ops)
    }

    fun eatTypeBrackets(): Type {
        expect("[")
        if (accept("]"))
            return unit_type()

        try {
            val base = expectType()
            when {
                base is Type.TypeApplication && base.ops.isEmpty() && accept("::") -> {
                    val elems = mutableListOf(Pair(base.name, expectType()))
                    when (front.tokenName) {
                        "," -> {
                            while (accept(",")) {
                                val id = expectIdentifier()
                                expect("::")
                                val type = expectType()
                                elems += Pair(id, type)
                            }
                            return Type.RecordType(elems)
                        }
                        "|" -> {
                            while (accept("|")) {
                                val id = expectIdentifier()
                                expect("::")
                                val type = expectType()
                                elems += Pair(id, type)
                            }
                            return Type.EnumType(elems)
                        }
                        else -> {
                            expected("',' or '|'")
                        }
                    }
                }
                front.tokenName == "," -> {
                    val elems = mutableListOf(base)
                    while (accept(",")) {
                        val type = expectType()
                        elems += type
                    }
                    return Type.TupleType(elems)
                }
                accept("..") -> {
                    return Type.ArrayType(base, 0)
                }
                accept("^") -> {
                    val size = expectNumericalLiteral().toIntOrNull()
                    if (size == null || size <= 0)
                        expected("non-zero integer number")
                    return Type.ArrayType(base, size)
                }
            }

            return base
        } finally {
            expect("]")
        }
    }

    // TODO maybe allow trailing ',' ?
    private fun eatExpressionParenthesisInsides(endToken: String): Expression {
        // This is the empty tuple
        if (accept(endToken))
            return Expression.QuoteValue(Value.ListLiteral(emptyList()))

        val firstExpression = acceptExpression(0)!!
        if (firstExpression is Expression.IdentifierRef && accept("=")) {
            val firstId = firstExpression.id
            // this is a dictionary !
            val dict = mutableMapOf<Identifier, Expression>(firstId to acceptExpression(0)!!)
            while (true) {
                if (accept(endToken)) {
                    return Expression.RecordExpression(dict)
                } else {
                    expect(",")
                    val id = expectIdentifier()
                    expect("=")
                    val expr = expectExpression(0)
                    if (dict.contains(id))
                        throw Exception("identifier $id given two values $here")
                    dict[id] = expr
                }
            }
        } else {
            if (accept(endToken)) {
                return firstExpression // This was just a parenthesized expression...
            } else {
                // Otherwise it must be a list !
                val tuple = mutableListOf<Expression>()
                tuple += firstExpression
                while (true) {
                    if (accept(endToken)) {
                        return Expression.ListExpression(tuple)
                    }
                    expect(",")
                    tuple += acceptExpression(0)!!
                }
            }
        }
    }

    private fun acceptPrimaryExpression(): Expression? {
        when {
            front.tokenName == "StringLit" -> {
                val nom = eat(); return Expression.QuoteValue(Value.StrLiteral(nom.payload!!)); }
            front.tokenName == "NumLit" -> {
                val nom = eat(); return Expression.QuoteValue(Value.NumLiteral(nom.payload!!)); }
            front.tokenName == "Identifier" -> {
                val id = eatIdentifier()
                val prim_type = PrimitiveType.values().find { it.name == id }
                if (prim_type != null)
                    return Expression.QuoteType(Type.PrimitiveType(prim_type))
                return Expression.IdentifierRef(id)
            }
            accept("if") -> {
                val condition = acceptExpression(0)!!
                expect("then")
                val ifTrue = acceptExpression(0)!!
                expect("else")
                val ifFalse = acceptExpression(0)!!
                return Expression.Conditional(condition, ifTrue, ifFalse)
            }
            accept("fn") -> {
                //val lhs = eatExpressionParenthesisInsides("=>")
                val lhs = eatPattern()
                eat("=>")
                val rhs = expectExpression(0)
                return when {
                    !lhs.isRefutable -> Expression.Function(lhs, rhs)
                    else -> expected("Expected non-refutable pattern")
                }
            }
            accept("(") -> {
                return eatExpressionParenthesisInsides(")")
            }
            front.tokenName == "[" -> {
                //accept("[") -> {
                val type = eatTypeBrackets()
                val e = Expression.QuoteType(type)
                //eat("]")
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
                    Expression.IdentifierRef(prefix.rewrite),
                    listOf(
                        acceptPrefixedPrimaryExpr()!!
                    )
                )
            }
        }
        return acceptPrimaryExpression()
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
                            is Expression.Invocation -> Expression.Invocation(
                                oldfirst.target,
                                oldfirst.args + listOf(following)
                            )
                            else -> Expression.Invocation(accumulator, listOf(following))
                        }
                        continue@outerBinop
                    } else if (accept(infix.token.str)) {
                        when (infix) {
                            InfixSymbol.Ascription -> {
                                val type = expectType()
                                accumulator = Expression.Ascription(accumulator, type)
                            }
                            InfixSymbol.Cast -> {
                                val type = expectType()
                                accumulator = Expression.Cast(accumulator, type)
                            }
                            else -> {
                                val rhs = expectExpression(infix.priority)
                                accumulator = Expression.Invocation(
                                    Expression.IdentifierRef(infix.rewrite!!),
                                    listOf(
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

    // TODO maybe allow trailing ',' ?
    private fun eatPatternParenthesisInsides(endToken: String): Pattern {
        // This is the empty tuple
        if (accept(endToken))
            return Pattern.Literal(Value.ListLiteral(emptyList()))

        val firstExpression = eatPattern()
        if (firstExpression is Pattern.Binder && accept("=")) {
            val firstId = firstExpression.id
            // this is a dictionary !
            val dict = mutableMapOf(firstId to eatPattern())
            while (true) {
                if (accept(endToken)) {
                    return Pattern.RecordPattern(dict)
                } else {
                    expect(",")
                    val id = expectIdentifier()
                    expect("=")
                    val expr = eatPattern()
                    if (dict.contains(id))
                        throw Exception("identifier $id given two values $here")
                    dict[id] = expr
                }
            }
        } else {
            if (accept(endToken)) {
                return firstExpression // This was just a parenthesized expression...
            } else {
                // Otherwise it must be a list !
                val tuple = mutableListOf<Pattern>()
                tuple += firstExpression
                while (true) {
                    if (accept(endToken)) {
                        return Pattern.ListPattern(tuple)
                    }
                    expect(",")
                    tuple += eatPattern()
                }
            }
        }
    }

    private fun acceptPatternBasic(): Pattern? = when {
        front.tokenName == "StringLit" -> {
            val nom = eat(); Pattern.Literal(Value.StrLiteral(nom.payload!!)); }
        front.tokenName == "NumLit" -> {
            val nom = eat(); Pattern.Literal(Value.NumLiteral(nom.payload!!)); }
        front.tokenName == "Identifier" -> {
            val id = eatIdentifier()
            Pattern.Binder(id)
        }
        accept("(") -> {
            eatPatternParenthesisInsides(")")
        }

        else -> null
    }

    private fun eatPattern(): Pattern {
        val pattern = acceptPatternBasic() ?: expected("literal or list or dictionary or ctor pattern")
        if (pattern is Pattern.Binder) {
            val args = mutableListOf<Pattern>()
            while (true) {
                val arg = acceptPatternBasic() ?: break
                args += arg
            }
            if (args.isNotEmpty())
                return Pattern.CtorPattern(pattern.id, args)
        }
        if (accept(":")) {
            val typeAnnotation = expectType()
            return Pattern.TypeAnnotatedPattern(pattern, typeAnnotation)
        }
        return pattern
    }

    fun parseModule(): Module {
        val module = eatModule()
        if (front.tokenName != "EOF")
            expectedToken("EOF")
        return module
    }
}