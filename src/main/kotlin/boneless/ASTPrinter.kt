package boneless

fun Instruction.print() = when(this) {
    is Instruction.Def -> "def $identifier" + type.printType() + " :: " + body.print(false) + ";"
    is Instruction.Let -> "let $identifier" + type.printType() + " = " + body.print(false) + ";"
    is Instruction.Var -> "var $identifier" + type.printType() + if(defaultValue != null) " = ${defaultValue.print(false)}" else "" + ";"
}

fun Expression?.printType(): String = this?.let { ": " + it.print(false) } ?: ""

fun Expression?.print(needsParenthesis: Boolean): String = when(this) {
    null -> ""
    Expression.Unit -> "()"
    is Expression.StringLit -> "\"$lit\""
    is Expression.NumLit -> if (lit.startsWith("-")) "($lit)" else lit
    is Expression.RefSymbol -> symbol
    is Expression.Invocation -> (if (needsParenthesis) "(" else "") + callee.print(false) + (if(arguments.isNotEmpty()) (" " + arguments.joinToString(" ") { it.print(true) }) else "") + (if (needsParenthesis) ")" else "")
    is Expression.Tuple -> "(" + elements.joinToString(", ") { it.print(false) } + ")"
    is Expression.Sequence -> "{\n" + instructions.joinToString("") { shift(it.print()) + "\n" } + if(yieldValue != null) (shift(yieldValue.print(false)) + "\n") else "" + "}"
    is Expression.Function -> (if (needsParenthesis) "(" else "") + parameters.joinToString(" ") { it.print(false) } + " => " + body.print(false) + (if (needsParenthesis) ")" else "")
    is Expression.Conditional -> "if " + condition.print(true) + " then " + ifTrue.print(false) + " else " + ifFalse.print(false)
    is Expression.Ascription -> "( (" + e.print(false) + ") : "+ type.print(false) + ")"
}

fun shift(str: String) = str.lines().joinToString("\n") { "  $it" }