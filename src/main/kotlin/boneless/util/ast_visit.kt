package boneless.util

import boneless.*

data class Visitors(
    val exprVisitor: (Expression) -> Boolean,
    val literalVisitor: (Literal) -> Boolean,
    val typeExprVisitor: (TypeExpr) -> Boolean,
    val patternVisitor: (Pattern) -> Boolean,
    val instructionVisitor: (Instruction) -> Boolean
)

fun visitAST(e: Expression, visitors: Visitors) {
    if (!visitors.exprVisitor(e))
        return
    when(e) {
        is Expression.QuoteLiteral -> visitAST(e.literal, visitors)
        is Expression.QuoteType -> visitAST(e.quotedType, visitors)
        is Expression.IdentifierRef -> {}
        is Expression.ExprSpecialization -> {
            visitAST(e.target, visitors)
            for (arg in e.arguments)
                visitAST(arg, visitors)
        }
        is Expression.ListExpression -> {
            for (item in e.elements)
                visitAST(item, visitors)
        }
        is Expression.RecordExpression -> {
            for ((f, item) in e.fields)
                visitAST(item, visitors)
        }
        is Expression.Projection -> {
            visitAST(e.expression, visitors)
        }
        is Expression.Invocation -> {
            visitAST(e.callee, visitors)
            visitAST(e.arg, visitors)
        }
        is Expression.Function -> {
            visitAST(e.param, visitors)
            visitAST(e.body, visitors)
            if(e.returnTypeAnnotation != null)
                visitAST(e.returnTypeAnnotation, visitors)
        }
        is Expression.Ascription -> {
            visitAST(e.expr, visitors)
            visitAST(e.ascribedType, visitors)
        }
        is Expression.Cast -> {
            visitAST(e.expr, visitors)
            visitAST(e.destinationType, visitors)
        }
        is Expression.Sequence -> {
            for (i in e.instructions)
                visitAST(i, visitors)
            if (e.yieldExpression != null)
                visitAST(e.yieldExpression, visitors)
        }
        is Expression.Conditional -> {
            visitAST(e.condition, visitors)
            visitAST(e.ifTrue, visitors)
            visitAST(e.ifFalse, visitors)
        }
        is Expression.WhileLoop -> {
            visitAST(e.loopCondition, visitors)
            visitAST(e.body, visitors)
        }
        else -> throw Exception("Visitor incomplete")
    }
}

fun visitAST(l: Literal, visitors: Visitors) {
    if (!visitors.literalVisitor(l))
        return
    when(l) {
        is Literal.NumLiteral,
        is Literal.StrLiteral,
        is Literal.BoolLiteral -> {}
        is Literal.ListLiteral -> {
            for (sl in l.elements)
                visitAST(sl, visitors)
        }
        is Literal.RecordLiteral -> {
            for ((f, sl) in l.fields)
                visitAST(sl, visitors)
        }
        else -> throw Exception("Visitor incomplete")
    }
}

fun visitAST(t: TypeExpr, visitors: Visitors) {
    if (!visitors.typeExprVisitor(t))
        return
    when(t) {
        is TypeExpr.PrimitiveType,
        is TypeExpr.TypeNameRef -> {}
        is TypeExpr.TypeSpecialization -> {
            visitAST(t.target, visitors)
            for (arg in t.arguments)
                visitAST(arg, visitors)
        }
        is TypeExpr.RecordType -> {
            for ((_, e) in t.elements)
                visitAST(e, visitors)
        }
        is TypeExpr.TupleType -> {
            for (e in t.elements)
                visitAST(e, visitors)
        }
        is TypeExpr.ArrayType -> {
            visitAST(t.elementType, visitors)
        }
        is TypeExpr.EnumType -> {
            for ((_, e) in t.elements)
                visitAST(e, visitors)
        }
        is TypeExpr.FnType -> {
            visitAST(t.dom, visitors)
            visitAST(t.codom, visitors)
        }
        else -> throw Exception("Visitor incomplete")
    }
}

fun visitAST(p: Pattern, visitors: Visitors) {
    if (!visitors.patternVisitor(p))
        return
    when(p) {
        is Pattern.BinderPattern -> {}
        is Pattern.LiteralPattern -> visitAST(p.literal, visitors)
        is Pattern.ListPattern -> {
            for (item in p.elements)
                visitAST(item, visitors)
        }
        is Pattern.RecordPattern -> {
            for ((_, item) in p.fields)
                visitAST(item, visitors)
        }
        is Pattern.CtorPattern -> {
            for (item in p.args)
                visitAST(item, visitors)
        }
        is Pattern.TypeAnnotatedPattern -> {
            visitAST(p.pattern, visitors)
            visitAST(p.annotatedType, visitors)
        }
        else -> throw Exception("Visitor incomplete")
    }
}

fun visitAST(i: Instruction, visitors: Visitors) {
    if (!visitors.instructionVisitor(i))
        return
    when (i) {
        is Instruction.Let -> {
            visitAST(i.pattern, visitors)
            visitAST(i.body, visitors)
        }
        is Instruction.Evaluate -> {
            visitAST(i.expr, visitors)
        }
        else -> throw Exception("Visitor incomplete")
    }
}