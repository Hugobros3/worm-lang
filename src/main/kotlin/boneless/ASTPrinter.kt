package boneless

fun Expression.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

fun Type.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

private class PrettyPrinter(val resugarizePrefixAndInfixSymbols: Boolean = true) {
    private fun Instruction.print() = when (this) {
        is Instruction.Def -> "def $identifier" + type.printTypeAnnotation() + " :: " + body.print() + ";"
        is Instruction.Let -> "let $identifier" + type.printTypeAnnotation() + " = " + body.print() + ";"
        is Instruction.Var -> "var $identifier" + type.printTypeAnnotation() + if (defaultValue != null) " = ${defaultValue.print()}" else "" + ";"
    }

    private fun Type?.printTypeAnnotation(): String = this?.let { ": " + it.print() } ?: ""

    fun Value.print(firstOperand: Boolean = true): String = when (this) {
        is Value.NumLiteral -> if (num.startsWith("-") && !firstOperand) "($num)" else num
        is Value.StrLiteral -> "\"$str\""
        is Value.ListLiteral -> "(" + list.joinToString(", ") { it.print() } + ")"
        is Value.DictionaryLiteral -> "(" + dict.map { (id, e) -> "$id = $e" }.joinToString(", ") + ")"
    }

    fun Expression?.print(infixOpPriority: Int = -1, firstOperand: Boolean = true): String {
        var p = 0
        fun open() = if (p <= infixOpPriority) "(" else ""
        fun close() = if (p <= infixOpPriority) ")" else ""
        return when (this) {
            null -> ""
            is Expression.QuoteValue -> value.print(firstOperand)
            is Expression.QuoteType -> "[" + type.print() + "]"
            is Expression.RefSymbol -> symbol
            is Expression.Invocation -> {
                val callee = arguments[0]
                if (resugarizePrefixAndInfixSymbols && callee is Expression.RefSymbol) {
                    val prefix = PrefixSymbol.values().find { it.rewrite == callee.symbol }
                    val infix = InfixSymbol.values().find { it.rewrite == callee.symbol }
                    if (prefix != null && arguments.size == 2) {
                        return prefix.token.str + arguments[1].print(9999)
                    } else if (infix != null && arguments.size == 3) {
                        p = infix.priority
                        return open() + arguments[1].print(p) + " ${infix.token.str} " + arguments[2].print(p) + close()
                    }
                }
                p = InfixSymbol.Application.priority
                open() + arguments.mapIndexed { i, it -> it.print(p, i == 0) }
                    .joinToString(" ") + close()
            }
            is Expression.ListExpression -> "(" + elements.joinToString(", ") { it.print() } + ")"
            is Expression.DictionaryExpression -> "(" + elements.map { (id, e) -> "$id = ${e.print()}" }.joinToString(", ") + ")"
            is Expression.Sequence -> "{\n" + instructions.joinToString("") { shift(it.print()) + "\n" } + if (yieldValue != null) (shift(
                yieldValue.print(0)
            ) + "\n") else "" + "}"
            is Expression.Function -> {
                //p = InfixSymbol.Map.priority
                open() + "fn " + parameters.joinToString(" ") { it.print() } + " => " + body.print() + close()
            }
            is Expression.Conditional -> "if " + condition.print() + " then " + ifTrue.print() + " else " + ifFalse.print()
            is Expression.Ascription -> {
                p = InfixSymbol.Ascription.priority
                open() + e.print(p, firstOperand) + " : " + type.print() + close()
            }
        }
    }

    fun Type.print(): String = when {
        this is Type.TypeApplication -> if (ops.isEmpty()) name else name + " " + ops.joinToString(" ") { it.print(0) }

        this is Type.TupleType && elements.isEmpty() -> "[]"
        this is Type.TupleType && elements.isNotEmpty() -> "[" + elements.joinToString(" * ") { e -> e.print() } + "]"

        this is Type.RecordType -> "[" + elements.joinToString(", ") { (name, type) -> name + "::" + type.print() } + "]"

        this is Type.ArrayType -> "[" + elementType.print() + (if (size == -1) ".." else "^$size") + "]"

        this is Type.EnumType -> "[" + elements.joinToString(" | ") { (name, type) -> name + "::" + type.print() } + "]"
        else -> throw Exception("Unprintable type")
    }

    fun shift(str: String) = str.lines().joinToString("\n") { "  $it" }
}