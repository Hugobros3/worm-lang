package boneless.util

import boneless.*
import boneless.parse.InfixOperator
import boneless.parse.PrefixOperator
import boneless.type.Type

fun Module.prettyPrint(resugarizePrefixAndInfixSymbols: Boolean = true, printInferredTypes: Boolean = false): String {
    val t = this
    return PrettyPrinter(resugarizePrefixAndInfixSymbols, printInferredTypes).run { t.print() }
}

fun Expression.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

fun Type.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

private class PrettyPrinter(val resugarizePrefixAndInfixSymbols: Boolean = true, val printInferredTypes: Boolean = false) {
    fun Module.print() = defs.joinToString("\n") { it.print() }
    fun Def.print() = when(body) {
        is Def.DefBody.ExprBody -> "def $identifier" + body.annotatedType.printTypeAnnotation() + " = " + body.expr.print(0)
        is Def.DefBody.DataCtor -> "data " + identifier + " = " + body.datatype.print()
        is Def.DefBody.TypeAlias -> "type " + identifier + " = " + body.aliasedType.print()
        is Def.DefBody.FnBody -> "fn $identifier " + body.fn.run {
            if (returnTypeAnnotation == null)
                parameters.print() + " => " + body.print()
            else
                parameters.print() + " -> " + returnTypeAnnotation.print() + " = " + body.print()
        }
    } + ";"

    private fun Instruction.print() = when (this) {
        is Instruction.Let -> "let ${pattern.print()}" + " = " + body.print() + ";"
        is Instruction.Evaluate -> expr.prettyPrint() + ";"
    }

    private fun Type?.printTypeAnnotation(): String = this?.let { ": " + it.print() } ?: ""

    fun Literal.print(firstOperand: Boolean = true): String = when (this) {
        is Literal.NumLiteral -> if (number.startsWith("-") && !firstOperand) "($number)" else number
        is Literal.StrLiteral -> "\"$string\""
        is Literal.ListLiteral -> "(" + elements.joinToString(", ") { it.print() } + ")"
        is Literal.RecordLiteral -> "(" + fields.joinToString(", ") { (id, e) -> "$id = $e" } + ")"
    }

    fun Expression?.print(infixOpPriority: Int = -1, firstOperand: Boolean = true): String {
        return if (printInferredTypes) {
            "(${this.print_(infixOpPriority, firstOperand)} /* ${this?.type.printTypeAnnotation()} */)"
        } else this.print_(infixOpPriority, firstOperand)
    }

    fun Expression?.print_(infixOpPriority: Int = -1, firstOperand: Boolean = true): String {
        var p = 0
        fun open() = if (p <= infixOpPriority) "(" else ""
        fun close() = if (p <= infixOpPriority) ")" else ""
        return when (this) {
            null -> ""
            is Expression.QuoteLiteral -> literal.print(firstOperand)
            is Expression.QuoteType -> "[" + quotedType.print() + "]"
            is Expression.IdentifierRef -> id.identifier
            is Expression.Invocation -> {
                val callee = callee
                if (resugarizePrefixAndInfixSymbols && callee is Expression.IdentifierRef) {
                    val prefix = PrefixOperator.values().find { it.rewrite == callee.id.identifier }
                    val infix = InfixOperator.values().find { it.rewrite == callee.id.identifier }
                    if (prefix != null && args.size == 1) {
                        return prefix.token.str + args[0].print(9999)
                    } else if (infix != null && args.size == 2) {
                        p = infix.priority
                        return open() + args[0].print(p) + " ${infix.token.str} " + args[1].print(p) + close()
                    }
                }
                p = InfixOperator.Application.priority
                open() + this.callee.print() + " " + args.mapIndexed { i, it -> it.print(p, i == 0) }.joinToString(" ") + close()
            }
            is Expression.ListExpression -> "(" + elements.joinToString(", ") { it.print() } + ")"
            is Expression.RecordExpression -> "(" + fields.map { (id, e) -> "$id = ${e.print()}" }.joinToString(", ") + ")"
            is Expression.Sequence -> "{\n" + instructions.joinToString("") { shift(it.print()) + "\n" } + if (yieldValue != null) (shift(
                yieldValue.print(0)
            ) + "\n") else "" + "}"
            is Expression.Function -> {
                if (returnTypeAnnotation == null)
                    open() + "fn " + parameters.print() + " => " + body.print() + close()
                else
                    open() + "fn " + parameters.print() + " -> " + returnTypeAnnotation.print() + " = " + body.print() + close()
            }
            is Expression.Conditional -> "if " + condition.print() + " then " + ifTrue.print() + " else " + ifFalse.print()
            is Expression.Ascription -> {
                p = InfixOperator.Ascription.priority
                open() + expr.print(p, firstOperand) + " : " + ascribedType.print() + close()
            }
            is Expression.Cast -> {
                p = InfixOperator.Cast.priority
                open() + expr.print(p, firstOperand) + " as " + destinationType.print() + close()
            }
        }
    }

    fun Type.print(): String = when {
        this is Type.PrimitiveType -> primitiveType.name
        this is Type.TypeApplication -> callee.identifier + (if (args.isEmpty()) "" else " " + args.joinToString(" ") { it.print(0) })

        this is Type.TupleType && elements.isEmpty() -> "[]"
        this is Type.TupleType && elements.isNotEmpty() -> "[" + elements.joinToString(", ") { e -> e.print() } + "]"

        this is Type.RecordType -> "[" + elements.joinToString(", ") { (name, type) -> name + "=" + type.print() } + "]"

        this is Type.ArrayType -> "[" + elementType.print() + (if (size == -1) ".." else "..$size") + "]"

        this is Type.EnumType -> "[" + elements.joinToString(" | ") { (name, type) -> name + "=" + type.print() } + "]"
        this is Type.FnType -> dom.print() + " -> " + codom.print()
        this is Type.NominalType -> name
        else -> throw Exception("Unprintable type")
    }

    fun Pattern.print(firstOperand: Boolean = true): String {
        return if (printInferredTypes) {
            "(${this.print_(firstOperand)} /* ${this.type.printTypeAnnotation()} */)"
        } else this.print_(firstOperand)
    }
    fun Pattern.print_(firstOperand: Boolean = true): String {
        return when (this) {
            is Pattern.BinderPattern -> id
            is Pattern.LiteralPattern -> (if (literal.isUnit) "" else "\\") + literal.print(firstOperand)
            is Pattern.ListPattern -> "(" + elements.joinToString(", ") { it.print(firstOperand) } + ")"
            is Pattern.RecordPattern -> "(" + fields.map { (id, p) -> "$id = ${p.print()}" }.joinToString(", ") + ")"
            is Pattern.CtorPattern -> callee.identifier + " " + args.joinToString(" ") { it.print(firstOperand) }
            is Pattern.TypeAnnotatedPattern -> pattern.print() + " : " + annotatedType.print()
        }
    }

    fun shift(str: String) = str.lines().joinToString("\n") { "  $it" }
}