package boneless.util

import boneless.*
import boneless.bind.TermLocation
import java.io.Writer
import java.security.MessageDigest

private val Def.handle: String
    get() = "mod"+module_+"_"+identifier

val ControlFlow = DotPrinter.ArrowStyle(arrowHead = "normal")
val ArgumentOf = DotPrinter.ArrowStyle(arrowHead = "empty")
val TypeOf = DotPrinter.ArrowStyle(arrowHead = "diamond")
val DataDependency = DotPrinter.ArrowStyle(arrowHead = "vee")
val ParameterOf = DotPrinter.ArrowStyle(arrowHead = "none")

val md5 = MessageDigest.getInstance("MD5")
fun String.hash() = md5.digest(toByteArray()).joinToString("") { it.toHexStr() }
fun Byte.toHexStr(): String {
    val lower = this.toInt() and 0xF
    val upper = (this.toInt() shr 4) and 0xF
    val digits = "0123456789ABCDEF"
    return ""+digits[upper]+digits[lower]
}

class AstDotPrinter(private val modules: List<Module>, output: Writer): DotPrinter(output) {
    var gid = 0
    val doneLit = mutableSetOf<Literal>()
    val doneExpr = mutableMapOf<Expression, String>()
    val donePtrn = mutableMapOf<Pattern, String>()

    fun printExpr(expression: Expression): String {
        val nodeId = "expr"+gid++
        //if (doneExpr.contains(expression))
        //    return doneExpr[expression]!!
        doneExpr.put(expression, nodeId)

        val appearance = NodeAppearance(fillColor = "aquamarine1", style = "filled")

        when (expression) {
            is Expression.Ascription -> TODO()
            is Expression.Assignment -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Conditional -> TODO()
            is Expression.ExprSpecialization -> TODO()
            is Expression.Function -> {
                node(nodeId, "Function", appearance.copy( shape = "rectangle"))
                arrow(nodeId, printPtrn(expression.param), DataDependency)
                arrow(nodeId, printExpr(expression.body), DataDependency)
                if(expression.returnTypeAnnotation != null) {
                    arrow(nodeId, printTypeExpr(expression.returnTypeAnnotation), TypeOf)
                }
            }
            is Expression.IdentifierRef -> {
                node(nodeId, "IdentifierRef: \\\"${expression.id.identifier}\\\"", appearance.copy(fillColor = "aquamarine3"))
                when (val r = expression.id.resolved) {
                    is TermLocation.BinderRef -> arrow(nodeId, printPtrn(r.binder), DataDependency)
                    is TermLocation.BuiltinFnRef -> TODO()
                    is TermLocation.DefRef -> arrow(nodeId, r.def.handle, DataDependency)
                    is TermLocation.TypeParamRef -> TODO()
                }
                // TODO implicit garbage
            }
            is Expression.Invocation -> {
                node(nodeId, "Invocation", appearance.copy(fillColor = "tomato"))
                arrow(nodeId, printExpr(expression.callee), DataDependency, "callee")
                arrow(nodeId, printExpr(expression.arg), ArgumentOf, "arg")
            }
            is Expression.ListExpression -> {
                val label = expression.elements.mapIndexed { i, e ->"<p$i> $i" }.joinToString(" | ")
                node(nodeId, "{ListExpr | {$label}}", appearance.copy(shape = "record"))

                for ((i, component) in expression.elements.withIndex()) {
                    val cId = printExpr(component)
                    arrow(nodeId+":p$i", cId, DataDependency)
                }
            }
            is Expression.Projection -> TODO()
            is Expression.QuoteLiteral -> {
                node(nodeId, "QuoteLit", appearance)
                arrow(nodeId, printLit(expression.literal), DataDependency)
            }
            is Expression.QuoteType -> TODO()
            is Expression.RecordExpression -> {
                val label = expression.fields.mapIndexed { i, e ->"<p$i> ${e.first}" }.joinToString(" | ")
                node(nodeId, "{ListExpr | {$label}}", appearance.copy(shape = "record"))

                for ((i, component) in expression.fields.withIndex()) {
                    val cId = printExpr(component.second)
                    arrow(nodeId+":p$i", cId, DataDependency)
                }
            }
            is Expression.Sequence -> {
                var label = expression.instructions.mapIndexed {
                        i, e -> "<p$i> " + when(e) {
                    is Instruction.Evaluate -> "Let"
                    is Instruction.Let -> "Evaluate"
                }
                }.joinToString(" | ")
                if (expression.yieldExpression != null) {
                    label += " | <p${expression.instructions.size}> Yield"
                }
                node(nodeId, "{$label}", appearance.copy(shape = "record"))

                for ((i, component) in expression.instructions.withIndex()) {
                    when(component) {
                        is Instruction.Evaluate -> {
                            val cId = printExpr(component.expr)
                            arrow(nodeId+":p$i", cId, DataDependency)
                        }
                        is Instruction.Let -> {
                            val cId = printPtrn(component.pattern)
                            arrow(nodeId+":p$i", cId, DataDependency)

                            val cId2 = printExpr(component.body)
                            arrow(nodeId+":p$i", cId2, DataDependency)
                        }
                    }
                }

                if (expression.yieldExpression != null) {
                    arrow(nodeId+":p${expression.instructions.size}", printExpr(expression.yieldExpression), DataDependency)
                }
            }
            is Expression.WhileLoop -> TODO()
        }

        return nodeId
    }

    fun printTypeExpr(typeExpr: TypeExpr): String {
        val nodeId = "lit"+gid++

        val appearance = NodeAppearance(style = "filled", fillColor = "darkslategray1", shape = "diamond")
        node(nodeId, typeExpr.prettyPrint(), appearance)

        return nodeId
    }

    fun printLit(literal: Literal): String {
        val nodeId = "lit"+gid++
        //if (doneLit.contains(literal))
        //    return nodeId
        //doneLit.add(literal)

        val appearance = NodeAppearance(style = "dotted")

        when (literal) {
            is Literal.BoolLiteral -> node(nodeId, if (literal.value) "true" else "false", appearance)
            is Literal.NumLiteral -> node(nodeId, literal.number, appearance)
            is Literal.ListLiteral -> {
                val label = literal.elements.mapIndexed { i, e ->"<p$i> $i" }.joinToString(" | ")
                node(nodeId, "{ListLiteral | {$label}}", appearance.copy(shape = "record"))

                for ((i, component) in literal.elements.withIndex()) {
                    val cId = printLit(component)
                    arrow(nodeId+":p$i", cId, DataDependency)
                }
            }
            is Literal.RecordLiteral -> TODO()
            is Literal.StrLiteral -> node(nodeId, "\"" + literal.string + "\"", appearance)
            is Literal.Undef -> node(nodeId, "undef", appearance)
        }

        return nodeId
    }

    fun printPtrn(pattern: Pattern): String {
        val nodeId = "ptrn"+gid++
        if (donePtrn.contains(pattern))
            return donePtrn[pattern]!!
        donePtrn.put(pattern, nodeId)

        val appearance = NodeAppearance(style = "filled", fillColor = "lightgrey", shape = "parallelogram")

        when (pattern) {
            is Pattern.BinderPattern -> {
                node(nodeId, "Binder: \\\"${pattern.id}\\\"", appearance)
            }
            is Pattern.CtorPattern -> {
                val label = pattern.args.mapIndexed { i, e ->"<p$i> $i" }.joinToString(" | ")
                node(nodeId, "{CtorPtrn | {$label}}", appearance.copy(shape = "record"))

                for ((i, component) in pattern.args.withIndex()) {
                    val cId = printPtrn(component)
                    arrow(nodeId+":p$i", cId, DataDependency)
                }

                //arrow(nodeId, printPtrn(pattern.callee), DataDependency, "callee")
            }
            is Pattern.ListPattern -> {
                val label = pattern.elements.mapIndexed { i, e ->"<p$i> $i" }.joinToString(" | ")
                node(nodeId, "{ListPtrn | {$label}}", appearance.copy(shape = "record"))

                for ((i, component) in pattern.elements.withIndex()) {
                    val cId = printPtrn(component)
                    arrow(nodeId+":p$i", cId, DataDependency)
                }
            }
            is Pattern.LiteralPattern -> {
                node(nodeId, "LiteralPtrn", appearance)
                arrow(nodeId, printLit(pattern.literal), DataDependency)
            }
            is Pattern.RecordPattern -> {
                val label = pattern.fields.mapIndexed { i, e ->"<p$i> ${e.first}" }.joinToString(" | ")
                node(nodeId, "{RecordPtrn | {$label}}", appearance.copy(shape = "record"))

                for ((i, component) in pattern.fields.withIndex()) {
                    val cId = printPtrn(component.second)
                    arrow(nodeId+":p$i", cId, DataDependency)
                }
            }
            is Pattern.TypeAnnotatedPattern -> {
                node(nodeId, "TypedPtrn", appearance)
                arrow(nodeId, printPtrn(pattern.pattern), DataDependency)
                arrow(nodeId, printTypeExpr(pattern.annotatedType), TypeOf)
            }
        }

        return nodeId
    }

    fun print() {
        output += "digraph MethodCFG {"
        indent++
        output += "bgcolor=transparent;"
        for (module in modules) {
            output += "subgraph mod_${module.name} {"
            for (def in module.defs) {
                val idName = when(def.body) {
                    is Def.DefBody.Contract -> "contract ${def.identifier}"
                    is Def.DefBody.DataCtor -> "data " + def.identifier
                    is Def.DefBody.ExprBody -> "def " + def.identifier
                    is Def.DefBody.FnBody -> "fn " + def.identifier + def.body.fn.param.prettyPrint()
                    is Def.DefBody.Instance -> "instance " + def.body.contractId.identifier + "::" + "(" + def.body.arguments.joinToString(", ") + ")"
                    is Def.DefBody.TypeAlias -> "type"
                }
                val nodeId = def.handle
                node(nodeId, idName, NodeAppearance("note", fillColor = "orange", style = "filled"))

                when (def.body) {
                    is Def.DefBody.ExprBody -> {
                        val bId = printExpr(def.body.expr)
                        arrow(nodeId, bId, ParameterOf)
                    }
                    is Def.DefBody.FnBody -> {
                        val bId = printExpr(def.body.fn)
                        arrow(nodeId, bId, ParameterOf)
                    }
                    /*is Def.DefBody.Contract -> TODO()
                    is Def.DefBody.DataCtor -> TODO()
                    is Def.DefBody.Instance -> TODO()
                    is Def.DefBody.TypeAlias -> TODO()*/
                }
            }
            output += "}"
        }
        indent--
        output += "}"
    }
}