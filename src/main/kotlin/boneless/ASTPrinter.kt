package boneless

fun Instruction.print() = when(this) {
    is Instruction.Def -> "def $identifier" + type.printType() + " :: " + body.print() + ";"
    is Instruction.Let -> "let $identifier" + type.printType() + " = " + body.print() + ";"
    is Instruction.Var -> "var $identifier" + type.printType() + if(defaultValue != null) " = ${defaultValue.print()}" else "" + ";"
}

fun Expression?.printType(): String = this?.let { ": " + it.print(0) } ?: ""

fun Expression?.print(infixOpPriority: Int = -1, firstOperand: Boolean = true): String {
    var p = 0
    fun open() = if (p <= infixOpPriority) "(" else ""
    fun close() = if (p <= infixOpPriority) ")" else ""
    return when(this) {
        null -> ""
        Expression.Unit -> "()"
        is Expression.StringLit -> "\"$lit\""
        is Expression.NumLit -> if (lit.startsWith("-") && !firstOperand) "($lit)" else lit
        is Expression.RefSymbol -> symbol
        is Expression.Invocation -> {
            p = InfixSymbol.Application.priority
            open() + arguments.mapIndexed { i, it -> it.print(InfixSymbol.Application.priority, i == 0) }.joinToString(" ") + close()
        }
        is Expression.Tuple -> "(" + elements.joinToString(", ") { it.print() } + ")"
        is Expression.Sequence -> "{\n" + instructions.joinToString("") { shift(it.print()) + "\n" } + if(yieldValue != null) (shift(yieldValue.print(0)) + "\n") else "" + "}"
        is Expression.Function -> {
            p = InfixSymbol.Map.priority
            open() + parameters.joinToString(" ") { it.print(p) } + " => " + body.print(p) + close()
        }
        is Expression.Conditional -> "if " + condition.print() + " then " + ifTrue.print() + " else " + ifFalse.print()
        is Expression.Ascription -> {
            p = InfixSymbol.Ascription.priority
            open() + e.print(p, firstOperand) + " : "+ type.print(p) + close()
        }
    }
}

fun shift(str: String) = str.lines().joinToString("\n") { "  $it" }