package boneless.util

import java.io.Writer

open class DotPrinter(protected val output: Writer) {
    protected var indent = 0;

    protected operator fun Writer.plusAssign(s: String) {
        for (i in 0 until indent)
            output.write("    ")
        output.write(s)
        output.write("\n")
    }

    data class NodeAppearance(val shape: String = "ellipse", val color: String = "black", val style: String = "solid", val fillColor: String = "transparent")

    data class ArrowStyle(val arrowHead: String = "normal", val fontSize: Int = 8, val color: String = "black", val fontColor: String = "grey")

    protected fun node(internalName: String, label: String, appearance: NodeAppearance) {
        output += "$internalName [ "
        indent++

        output += "label = \"$label\";"

        output += "shape = ${appearance.shape};"
        output += "color = ${appearance.color};"
        output += "style = ${appearance.style};"
        output += "fillcolor = ${appearance.fillColor};"

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