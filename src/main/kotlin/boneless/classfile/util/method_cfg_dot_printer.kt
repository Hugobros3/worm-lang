package boneless.classfile.util

import boneless.classfile.BasicBlockOutFlow
import boneless.classfile.MethodBuilder
import boneless.util.DotPrinter
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