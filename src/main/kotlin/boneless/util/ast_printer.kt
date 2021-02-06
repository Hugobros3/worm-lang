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

fun TypeExpr.prettyPrint(): String {
    val t = this
    return PrettyPrinter().run { t.print() }
}

private class PrettyPrinter(val resugarizePrefixAndInfixSymbols: Boolean = true, val printInferredTypes: Boolean = false) {
    fun Module.print() = defs.joinToString("\n") { it.print() }
    fun Def.print(): String {
        var poly = ""
        if (typeParamsNames.isNotEmpty()) {
            poly = "forall " + typeParamsNames.joinToString(", ") { it } + "\n"
        }
        return poly + when(body) {
            is Def.DefBody.ExprBody -> "def $identifier" + body.annotatedType.printTypeAnnotation() + " = " + body.expr.print(0)
            is Def.DefBody.DataCtor -> "data " + identifier + " = " + body.datatype.print()
            is Def.DefBody.TypeAlias -> "type " + identifier + " = " + body.aliasedType.print()
            is Def.DefBody.FnBody -> "fn $identifier " + body.fn.run {
                if (returnTypeAnnotation == null)
                    param.print() + " => " + body.print()
                else
                    param.print() + " -> " + returnTypeAnnotation.print() + " = " + body.print()
            }
            is Def.DefBody.Contract -> "contract $identifier = ${body.payload.print()}"
            is Def.DefBody.Instance -> "instance ${body.contractId.identifier}::" + (if (body.argumentsExpr.size == 1) body.argumentsExpr[0].print() else ( "(" + body.argumentsExpr.joinToString(", ") { it.print() } + ")" )) + " = " + body.body.print(0)
        } + ";"
    }

    private fun Instruction.print() = when (this) {
        is Instruction.Let -> "let " + (if (mutable) "mut " else "") + " ${pattern.print()}" + " = " + body.print() + ";"
        is Instruction.Evaluate -> expr.prettyPrint() + ";"
    }

    private fun TypeExpr?.printTypeAnnotation(): String = this?.let { ": " + it.print() } ?: ""

    fun Literal.print(firstOperand: Boolean = true): String = when (this) {
        is Literal.BoolLiteral -> if (value) "true" else "false"
        is Literal.NumLiteral -> if (number.startsWith("-") && !firstOperand) "($number)" else number
        is Literal.StrLiteral -> "\"$string\""
        is Literal.ListLiteral -> "(" + elements.joinToString(", ") { it.print() } + ")"
        is Literal.RecordLiteral -> "(" + fields.joinToString(", ") { (id, e) -> "$id = $e" } + ")"
        is Literal.Undef -> "builtin_undef"
    }

    fun Expression.print(infixOpPriority: Int = -1, firstOperand: Boolean = true): String {
        return if (printInferredTypes) {
            "(${this.print_(infixOpPriority, firstOperand)} /* ${this.type.print() + (if (implicitUpcast != null) " implicit_upcast_as ${implicitUpcast!!.print()}" else "")  } */)"
        } else this.print_(infixOpPriority, firstOperand)
    }

    fun Expression.print_(infixOpPriority: Int = -1, firstOperand: Boolean = true): String {
        var p = 0
        fun open() = if (p <= infixOpPriority) "(" else ""
        fun close() = if (p <= infixOpPriority) ")" else ""
        return when (this) {
            null -> ""
            is Expression.QuoteLiteral -> literal.print(firstOperand)
            is Expression.QuoteType -> "[" + quotedType.print() + "]"
            is Expression.IdentifierRef -> id.identifier
            is Expression.Projection -> expression.print(0) + "." + id
            is Expression.ExprSpecialization -> target.print() + "::" + if (arguments.size == 1) arguments[0].print() else ( "(" + arguments.joinToString(", ") { it.prettyPrint() } + ")" )
            is Expression.Invocation -> {
                val callee = callee
                if (resugarizePrefixAndInfixSymbols && callee is Expression.IdentifierRef) {
                    val prefix = PrefixOperator.values().find { it.rewrite_ == callee.id.identifier }
                    val infix = InfixOperator.values().find { it.rewrite_ == callee.id.identifier }
                    if (prefix != null) {
                        return prefix.token.str + arg.print(9999)
                    } else if (infix != null) {
                        val args = arg as Expression.ListExpression
                        p = infix.priority
                        return open() + args.elements[0].print(p) + " ${infix.token.str} " + args.elements[1].print(p) + close()
                    }
                }
                p = InfixOperator.Application.priority
                open() + this.callee.print() + " " + arg.print(p) + close()
            }
            is Expression.ListExpression -> "(" + elements.joinToString(", ") { it.print() } + ")"
            is Expression.RecordExpression -> "(" + fields.map { (id, e) -> "$id = ${e.print()}" }.joinToString(", ") + ")"
            is Expression.Sequence -> "{\n" + instructions.joinToString("") { shift(it.print()) + "\n" } + (if (yieldExpression != null) (shift(
                yieldExpression.print(0)
            ) + "\n") else "") + "}"
            is Expression.Function -> {
                if (returnTypeAnnotation == null)
                    open() + "fn " + param.print() + " => " + body.print() + close()
                else
                    open() + "fn " + param.print() + " -> " + returnTypeAnnotation.print() + " = " + body.print() + close()
            }
            is Expression.Conditional -> "if " + condition.print() + " then " + ifTrue.print() + " else " + ifFalse.print()
            is Expression.WhileLoop -> "while " + loopCondition.print(0) + " do " + body.print(0)
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

    fun TypeExpr.print(): String = when {
        this is TypeExpr.PrimitiveType -> primitiveType.name
        this is TypeExpr.TypeNameRef -> callee.identifier
        this is TypeExpr.TypeSpecialization -> target.print() + "::" + if (arguments.size == 1) arguments[0].print() else ( "(" + arguments.joinToString(", ") { it.prettyPrint() } + ")" )

        this is TypeExpr.TupleType && elements.isEmpty() -> "[]"
        this is TypeExpr.TupleType && elements.isNotEmpty() -> "[" + elements.joinToString(", ") { e -> e.print() } + "]"

        this is TypeExpr.RecordType -> "[" + elements.joinToString(", ") { (name, type) -> name + "=" + type.print() } + "]"

        this is TypeExpr.ArrayType -> "[" + elementType.print() + (if (size == -1) ".." else "..$size") + "]"

        this is TypeExpr.EnumType -> "[" + elements.joinToString(" | ") { (name, type) -> name + "=" + type.print() } + "]"
        this is TypeExpr.FnType -> "fn " + dom.print() + " -> " + codom.print()
        this is TypeExpr.Top -> "Top"
        else -> throw Exception("Unprintable type")
    }

    fun Type.print(): String = when {
        this is Type.Top -> "Top"
        this is Type.PrimitiveType -> primitiveType.name
        this is Type.TupleType && elements.isEmpty() -> "[]"
        this is Type.TupleType && elements.isNotEmpty() -> "[" + elements.joinToString(", ") { e -> e.print() } + "]"

        this is Type.RecordType -> "[" + elements.joinToString(", ") { (name, type) -> name + "=" + type.print() } + "]"

        this is Type.ArrayType -> "[" + elementType.print() + (if (size == -1) ".." else "..$size") + "]"

        this is Type.EnumType -> "[" + elements.joinToString(" | ") { (name, type) -> name + "=" + type.print() } + "]"
        this is Type.FnType -> "fn " + dom.print() + " -> " + codom.print()
        this is Type.NominalType -> name
        this is Type.TypeParam -> bound.def.typeParamsNames[bound.index]
        else -> throw Exception("Unprintable type")
    }

    fun Pattern.print(firstOperand: Boolean = true): String {
        return if (printInferredTypes) {
            "(${this.print_(firstOperand)} /* ${this.type!!.print()} */)"
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