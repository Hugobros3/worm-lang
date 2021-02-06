package boneless.classfile.util

import boneless.classfile.BasicBlockOutFlow
import boneless.classfile.MethodBuilder
import java.io.Writer

val ControlFlow = DotPrinter.ArrowStyle(arrowHead = "normal")

class MethodBuilderDotPrinter(private val method: MethodBuilder, output: Writer) : DotPrinter(output) {
    fun print() {
        output += "digraph MethodCFG {"
        indent++
        output += "bgcolor=transparent;"
        for(bb in method.bbBuilders) {
            node(bb.bbName, bb.bbName, NodeAppearance("rectangle"))
            when(val s = bb.outgoingFlow) {
                BasicBlockOutFlow.Undef -> TODO()
                BasicBlockOutFlow.FnReturn -> {

                }
                is BasicBlockOutFlow.Branch -> {
                    node(bb.bbName + "_branch", "Branch", NodeAppearance("rectangle", color = "lightblue", style = "filled"))
                    arrow(bb.bbName, bb.bbName + "_branch", ControlFlow)

                    arrow(bb.bbName + "_branch", s.ifTrue.bbName, ControlFlow, s.mode.name)
                    arrow(bb.bbName + "_branch", s.ifFalse.bbName, ControlFlow, "else")
                }
                is BasicBlockOutFlow.Jump -> {
                    arrow(bb.bbName, s.successor.bbName, ControlFlow)
                }
                null -> TODO()
            }
        }
        indent--
        output += "}"
    }
}

open class DotPrinter(protected val output: Writer) {
    protected var indent = 0;

    protected operator fun Writer.plusAssign(s: String) {
        for (i in 0 until indent)
            output.write("    ")
        output.write(s)
        output.write("\n")
    }

    data class NodeAppearance(val shape: String = "ellipse", val color: String = "black", val style: String = "solid")

    data class ArrowStyle(val arrowHead: String = "normal", val fontSize: Int = 8, val color: String = "black", val fontColor: String = "grey")

    protected fun node(internalName: String, label: String, appearance: NodeAppearance) {
        output += "$internalName [ "
        indent++

        output += "label = \"$label\";"

        output += "shape = ${appearance.shape};"
        output += "color = ${appearance.color};"
        output += "style = ${appearance.style};"

        indent--
        output += "]"
    }

    protected fun arrow(src: String, dst: String, style: ArrowStyle, name: String? = null) {
        var arrowParams = "arrowhead=" + style.arrowHead
        if(name != null)
            arrowParams += ",label=\"$name\""
        arrowParams += ",fontsize=${style.fontSize}"
        arrowParams += ",fontcolor=${style.fontColor}"
        arrowParams += ",color=${style.color}"
        output += "$src -> $dst[$arrowParams];"
    }
}