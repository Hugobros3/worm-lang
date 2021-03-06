package boneless.parse

import boneless.*
import boneless.bind.BindPoint
import boneless.type.PrimitiveTypeEnum

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

    private fun acceptNumericalLiteral(): String? {
        val id = acceptToken("NumLit")
        return id?.payload
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
                    val ptrn = eatPattern()
                    expect("=")
                    val rhs = acceptExpression(0) ?: unexpectedToken("expression")
                    expect(";")
                    instructions += Instruction.Let(ptrn, rhs)
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

    private fun expectModule(moduleName: String): Module {
        val defs = mutableListOf<Def>()
        while (true) {
            val typeParams = mutableListOf<Identifier>()
            if (accept("forall")) {
                typeParams.add(expectIdentifier())
                while (accept(",")) {
                    typeParams.add(expectIdentifier())
                }
            }

            if (accept("data")) {
                val identifier = expectIdentifier()
                expect("=")
                val body = Def.DefBody.DataCtor(expectType())
                defs += Def(identifier, body, typeParams)
                expect(";")
            } else if (accept("type")) {
                val identifier = expectIdentifier()
                expect("=")
                val body = Def.DefBody.TypeAlias(expectType())
                defs += Def(identifier, body, typeParams)
                expect(";")
            } else if (accept("def")) {
                val identifier = expectIdentifier()
                val annotatedType = acceptTypeAnnotation()
                expect("=")
                val body = Def.DefBody.ExprBody(
                    acceptExpression(0) ?: unexpectedToken("expression"), annotatedType
                )
                defs += Def(identifier, body, typeParams)
                expect(";")
            } else if (accept("fn")) {
                val identifier = expectIdentifier()
                val lhs = eatPattern()
                val returnTypeAnnotation = if (accept("->")) {
                    val t = expectType()
                    expect("=")
                    t
                } else {
                    expect("=>")
                    null
                }
                val rhs = expectExpression(0)
                val fn = when {
                    !lhs.isRefutable -> Expression.Function(
                        lhs,
                        rhs,
                        returnTypeAnnotation
                    )
                    else -> expected("Expected non-refutable pattern")
                }
                val body = Def.DefBody.FnBody(fn, returnTypeAnnotation)
                defs += Def(identifier, body, typeParams)
                expect(";")
            } else if (accept("contract")) {
                val identifier = expectIdentifier()
                expect("=")
                val body = Def.DefBody.Contract(expectType())
                defs += Def(identifier, body, typeParams)
                expect(";")
            } else if (accept("instance")) {
                val ctIdentifier = expectIdentifier()
                expect("::")
                val args = expectSpecializationArgs()
                expect("=")
                val body = Def.DefBody.Instance(BindPoint.new(ctIdentifier), args, expectExpression(0))
                // instances don't get referred to by name
                defs += Def("__impl_${ctIdentifier}_$args", body, typeParams)
                expect(";")
            } else break
        }
        return Module(moduleName, defs.toSet())
    }

    private fun acceptTypeAnnotation(): TypeExpr? {
        if (accept(":")) {
            return expectType()
        }
        return null
    }

    fun expectType(): TypeExpr {
        if (accept("fn")) {
            val dom = expectType()
            eat("->")
            val codom = expectType()
            return TypeExpr.FnType(dom, codom)
        }
        if (accept("Top")) {
            return TypeExpr.Top
        }

        val calleeId = acceptIdentifier() ?: return eatTypeBrackets()

        val prim_type = PrimitiveTypeEnum.values().find { it.name == calleeId }
        if (prim_type != null)
            return TypeExpr.PrimitiveType(prim_type)

        val ref = TypeExpr.TypeNameRef(BindPoint.new(calleeId))

        if (accept("::")) {
            return TypeExpr.TypeSpecialization(ref, expectSpecializationArgs())
        }

        return ref
    }

    fun unit_type_expr() = TypeExpr.TupleType(emptyList())

    fun eatTypeBrackets(): TypeExpr {
        expect("[")
        if (accept("]"))
            return unit_type_expr()

        try {
            val base = expectType()
            when {
                base is TypeExpr.TypeNameRef && accept("=") -> {
                    val elems = mutableListOf(Pair(base.callee.identifier, expectType()))
                    when (front.tokenName) {
                        ",", "]" -> {
                            while (accept(",")) {
                                val id = expectIdentifier()
                                expect("=")
                                val type = expectType()
                                elems += Pair(id, type)
                            }
                            return TypeExpr.RecordType(elems)
                        }
                        "|" -> {
                            while (accept("|")) {
                                val id = expectIdentifier()
                                expect("=")
                                val type = expectType()
                                elems += Pair(id, type)
                            }
                            return TypeExpr.EnumType(elems)
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
                    return TypeExpr.TupleType(elems)
                }
                accept("..") -> {
                    val num = acceptNumericalLiteral() ?: return TypeExpr.ArrayType(base, 0)
                    val size = num.toIntOrNull()
                    if (size == null || size <= 0)
                        expected("non-zero integer number")
                    return TypeExpr.ArrayType(base, size)
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
            return Expression.QuoteLiteral(
                Literal.ListLiteral(
                    emptyList()
                )
            )

        val firstExpression = acceptExpression(0)!!
        if (firstExpression is Expression.IdentifierRef && accept("=")) {
            val firstId = firstExpression.id.identifier
            // this is a record !
            val fields = mutableListOf(Pair(firstId, acceptExpression(0)!!))
            while (true) {
                if (accept(endToken)) {
                    return Expression.RecordExpression(fields)
                } else {
                    expect(",")
                    val id = expectIdentifier()
                    expect("=")
                    val expr = expectExpression(0)
                    if (fields.any { it.first == id })
                        throw Exception("identifier $id given two values $here")
                    fields += Pair(id, expr)
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
                val nom = eat(); return Expression.QuoteLiteral(
                    Literal.StrLiteral(
                        nom.payload!!
                    )
                ); }
            front.tokenName == "NumLit" -> {
                val nom = eat(); return Expression.QuoteLiteral(
                    Literal.NumLiteral(
                        nom.payload!!
                    )
                ); }
            front.tokenName == "Identifier" -> {
                val id = eatIdentifier()
                val ref =  Expression.IdentifierRef(
                    BindPoint.new(
                        id
                    )
                )

                if (accept("::")) {
                    return Expression.ExprSpecialization(ref, expectSpecializationArgs())
                }

                return ref
            }
            accept("true") -> return Expression.QuoteLiteral(Literal.BoolLiteral(true))
            accept("false") -> return Expression.QuoteLiteral(Literal.BoolLiteral(false))
            accept("builtin_undef") -> return Expression.QuoteLiteral(Literal.Undef())
            accept("if") -> {
                val condition = acceptExpression(0)!!
                expect("then")
                val ifTrue = acceptExpression(0)!!
                expect("else")
                val ifFalse = acceptExpression(0)!!
                return Expression.Conditional(condition, ifTrue, ifFalse)
            }
            accept("fn") -> {
                val lhs = eatPattern()
                val returnTypeAnnotation = if (accept("->")) {
                    expectType()
                } else {
                    eat("=>")
                    null
                }
                val rhs = expectExpression(0)
                return when {
                    !lhs.isRefutable -> Expression.Function(
                        lhs,
                        rhs,
                        returnTypeAnnotation = returnTypeAnnotation
                    )
                    else -> expected("Expected non-refutable pattern")
                }
            }
            accept("while") -> {
                val loopCondition = acceptExpression(0)!!
                expect("do")
                val body = acceptExpression(0)!!
                return Expression.WhileLoop(loopCondition, body)
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
        for (prefix in PrefixOperator.values()) {
            //if (prefix.token == Keyword.Minus && i + 1 < tokens.size && tokens[i + 1].tokenName == "NumLit")
            //    continue
            if (accept(prefix.token.str)) {
                return Expression.Invocation(prefix.rewrite()!!, acceptPrefixedPrimaryExpr()!!)
            }
        }
        return acceptPrimaryExpression()
    }

    private fun expectExpression(priority: Int) = acceptExpression(priority) ?: expected("expression")

    private fun acceptExpression(priority: Int): Expression? {
        var accumulator = acceptPrefixedPrimaryExpr() ?: return null

        outerBinop@
        while (true) {
            for (infix in InfixOperator.values()) {
                if ((infix.priority >= priority)) {
                    if (infix == InfixOperator.Application) {
                        val following = acceptPrimaryExpression() ?: continue
                        val acc = accumulator
                        accumulator = when  {
                            // Syntactic sugar to turn `f a b c d` into `f (a, b, c, d)`
                            acc is Expression.Invocation && acc.arg is Expression.ListExpression && acc.arg.is_synthesized_invocation_argument_list -> {
                                Expression.Invocation(
                                    acc.callee,
                                    Expression.ListExpression(acc.arg.elements + listOf(following))
                                )
                            }
                            acc is Expression.Invocation -> {
                                Expression.Invocation(
                                    acc.callee,
                                    Expression.ListExpression(listOf(acc.arg, following))
                                )
                            }
                            else -> Expression.Invocation(accumulator, following)
                        }
                        continue@outerBinop
                    } else if (accept(infix.token.str)) {
                        when (infix) {
                            InfixOperator.Projection -> {
                                val id = expectIdentifier()
                                accumulator = Expression.Projection(accumulator, id)
                            }
                            InfixOperator.Ascription -> {
                                val type = expectType()
                                accumulator = Expression.Ascription(accumulator, type)
                            }
                            InfixOperator.Cast -> {
                                val type = expectType()
                                accumulator = Expression.Cast(accumulator, type)
                            }
                            InfixOperator.Assignment -> {
                                val value = expectExpression(infix.priority)
                                accumulator = Expression.Assignment(accumulator, value)
                            }
                            else -> {
                                val rhs = expectExpression(infix.priority)
                                accumulator = Expression.Invocation(
                                            infix.rewrite()!!,
                                    Expression.ListExpression(listOf(
                                        accumulator,
                                        rhs
                                    ))
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
            return Pattern.LiteralPattern(Literal.ListLiteral(emptyList()))

        val firstExpression = eatPattern()
        if (firstExpression is Pattern.BinderPattern && accept("=")) {
            val firstId = firstExpression.id
            // this is a record !
            val fields = mutableListOf(Pair(firstId, eatPattern()))
            while (true) {
                if (accept(endToken)) {
                    return Pattern.RecordPattern(fields)
                } else {
                    expect(",")
                    val id = expectIdentifier()
                    expect("=")
                    val expr = eatPattern()
                    if (fields.any { it.first == id })
                        throw Exception("identifier $id given two values $here")
                    fields += Pair(id, expr)
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
        accept("mut") -> {
            val id = expectIdentifier()
            Pattern.BinderPattern(id, true)
        }
        front.tokenName == "StringLit" -> {
            val nom = eat(); Pattern.LiteralPattern(
                Literal.StrLiteral(
                    nom.payload!!
                )
            ); }
        front.tokenName == "NumLit" -> {
            val nom = eat(); Pattern.LiteralPattern(
                Literal.NumLiteral(
                    nom.payload!!
                )
            ); }
        front.tokenName == "Identifier" -> {
            val id = eatIdentifier()
            Pattern.BinderPattern(id, false)
        }
        accept("(") -> {
            eatPatternParenthesisInsides(")")
        }

        else -> null
    }

    private fun eatPattern(): Pattern {
        val pattern = acceptPatternBasic() ?: expected("literal or list or dictionary or ctor pattern")
        if (pattern is Pattern.BinderPattern && !pattern.mutable) {
            val args = mutableListOf<Pattern>()
            while (true) {
                val arg = acceptPatternBasic() ?: break
                args += arg
            }
            if (args.isNotEmpty())
                return Pattern.CtorPattern(
                    BindPoint.new(
                        pattern.id
                    ), args
                )
        }
        if (accept(":")) {
            val typeAnnotation = expectType()
            return Pattern.TypeAnnotatedPattern(pattern, typeAnnotation)
        }
        return pattern
    }
    private fun expectSpecializationArgs(): List<TypeExpr> {
        val types = mutableListOf<TypeExpr>()
        if (accept("(")) {
            types.add(expectType())
            while(accept(",")) {
                types.add(expectType())
            }
            expect(")")
        } else {
            types.add(expectType())
        }
        return types
    }

    fun parseModule(moduleName: String = "DefaultModule"): Module {
        val module = expectModule(moduleName)
        expect("EOF")
        return module
    }

    fun parseType(): TypeExpr {
        val type = expectType()
        expect("EOF")
        return type
    }
}

fun createModule(moduleName: String, implementation: String): Module {
    return Parser(implementation, Tokenizer(implementation).tokenize()).parseModule(moduleName)
}