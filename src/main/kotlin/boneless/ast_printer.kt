package boneless

fun Module.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

fun Expression.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

fun Type.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

private class PrettyPrinter(val resugarizePrefixAndInfixSymbols: Boolean = true) {
    fun Module.print() = defs.joinToString("\n") { it.print() }
    fun Def.print() = "def $identifier" + type.printTypeAnnotation() + " :: " + when(body) {
        is Def.DefBody.ExprBody -> body.expr.print(0)
        is Def.DefBody.DataCtor -> "data " + body.type.print()
        is Def.DefBody.TypeAlias -> "type " + body.type.print()
    } + ";"

    private fun Instruction.print() = when (this) {
        is Instruction.Let -> "let ${binder.id}" + annotatedType.printTypeAnnotation() + " = " + body.print() + ";"
        is Instruction.Evaluate -> e.prettyPrint() + ";"
    }

    private fun Type?.printTypeAnnotation(): String = this?.let { ": " + it.print() } ?: ""

    fun Value.print(firstOperand: Boolean = true): String = when (this) {
        is Value.NumLiteral -> if (num.startsWith("-") && !firstOperand) "($num)" else num
        is Value.StrLiteral -> "\"$str\""
        is Value.ListLiteral -> "(" + list.joinToString(", ") { it.print() } + ")"
        is Value.RecordLiteral -> "(" + fields.map { (id, e) -> "$id = $e" }.joinToString(", ") + ")"
    }

    fun Expression?.print(infixOpPriority: Int = -1, firstOperand: Boolean = true): String {
        var p = 0
        fun open() = if (p <= infixOpPriority) "(" else ""
        fun close() = if (p <= infixOpPriority) ")" else ""
        return when (this) {
            null -> ""
            is Expression.QuoteValue -> value.print(firstOperand)
            is Expression.QuoteType -> "[" + quotedType.print() + "]"
            is Expression.IdentifierRef -> referenced.identifier
            is Expression.Invocation -> {
                val callee = callee
                if (resugarizePrefixAndInfixSymbols && callee is Expression.IdentifierRef) {
                    val prefix = PrefixSymbol.values().find { it.rewrite == callee.referenced.identifier }
                    val infix = InfixSymbol.values().find { it.rewrite == callee.referenced.identifier }
                    if (prefix != null && args.size == 1) {
                        return prefix.token.str + args[0].print(9999)
                    } else if (infix != null && args.size == 2) {
                        p = infix.priority
                        return open() + args[0].print(p) + " ${infix.token.str} " + args[1].print(p) + close()
                    }
                }
                p = InfixSymbol.Application.priority
                open() + this.callee.print() + " " + args.mapIndexed { i, it -> it.print(p, i == 0) }.joinToString(" ") + close()
            }
            is Expression.ListExpression -> "(" + list.joinToString(", ") { it.print() } + ")"
            is Expression.RecordExpression -> "(" + fields.map { (id, e) -> "$id = ${e.print()}" }.joinToString(", ") + ")"
            is Expression.Sequence -> "{\n" + instructions.joinToString("") { shift(it.print()) + "\n" } + if (yieldValue != null) (shift(
                yieldValue.print(0)
            ) + "\n") else "" + "}"
            is Expression.Function -> {
                //p = InfixSymbol.Map.priority
                open() + "fn " + parameters.print() + " => " + body.print() + close()
            }
            is Expression.Conditional -> "if " + condition.print() + " then " + ifTrue.print() + " else " + ifFalse.print()
            is Expression.Ascription -> {
                p = InfixSymbol.Ascription.priority
                open() + e.print(p, firstOperand) + " : " + ascribedType.print() + close()
            }
            is Expression.Cast -> {
                p = InfixSymbol.Cast.priority
                open() + e.print(p, firstOperand) + " as " + destinationType.print() + close()
            }
        }
    }

    fun Type.print(): String = when {
        this is Type.PrimitiveType -> primitiveType.name
        this is Type.TypeApplication -> callee.identifier + (if (args.isEmpty()) "" else " " + args.joinToString(" ") { it.print(0) })

        this is Type.TupleType && elements.isEmpty() -> "[]"
        this is Type.TupleType && elements.isNotEmpty() -> "[" + elements.joinToString(", ") { e -> e.print() } + "]"

        this is Type.RecordType -> "[" + elements.joinToString(", ") { (name, type) -> name + "::" + type.print() } + "]"

        this is Type.ArrayType -> "[" + elementType.print() + (if (size == -1) ".." else "^$size") + "]"

        this is Type.EnumType -> "[" + elements.joinToString(" | ") { (name, type) -> name + "::" + type.print() } + "]"
        this is Type.FnType -> dom.print() + " -> " + codom.print()
        this is Type.NominalType -> name
        else -> throw Exception("Unprintable type")
    }

    fun Pattern.print(firstOperand: Boolean = true): String {
        return when (this) {
            is Pattern.Binder -> id
            is Pattern.Literal -> (if (value.isUnit) "" else "\\") + value.print(firstOperand)
            is Pattern.ListPattern -> "(" + list.joinToString(", ") { it.print(firstOperand) } + ")"
            is Pattern.RecordPattern -> "(" + fields.map { (id, p) -> "$id = ${p.print()}" }.joinToString(", ") + ")"
            is Pattern.CtorPattern -> callee.identifier + " " + args.joinToString(" ") { it.print(firstOperand) }
            is Pattern.TypeAnnotatedPattern -> inside.print() + " : " + annotatedType.print()
        }
    }

    fun shift(str: String) = str.lines().joinToString("\n") { "  $it" }
}