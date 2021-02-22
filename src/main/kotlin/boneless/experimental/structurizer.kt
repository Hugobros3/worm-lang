package boneless.experimental

import boneless.util.DotPrinter
import java.io.Writer

data class Graph(val entryNode: Node)

sealed class Node(val name: String) {
    val joins = mutableListOf<Branch>()
    val incommingEdges = mutableListOf<Edge>()

    class Jump(name: String) : Node(name) {
        lateinit var target: Edge
    }
    class Branch(name: String) : Node(name) {
        lateinit var targets: List<Edge>
        val rgb = ""
    }
}

class Edge(val source: Node, val target: Node) {
    var signature: List<Node.Branch>? = null
    var edgeType = EdgeType.UNDEF
    var isSynthetic: Boolean = false
}

enum class EdgeType {
    FORWARD,
    BACK,
    UNDEF,
}

fun visitGraph(graph: Graph, nodeVisitor: (Node) -> Unit, edgeVisitor: (Edge) -> Unit) {
    val stack = mutableListOf<Node>()
    val done = mutableSetOf<Node>()

    stack.add(graph.entryNode)
    while (stack.isNotEmpty()) {
        val element = stack.removeLast()
        nodeVisitor(element)

        fun visit(edge: Edge) {
            edgeVisitor(edge)
            if (done.contains(edge.target)) {

            } else if (!stack.contains(edge.target)){
                stack.add(edge.target)
            }
        }

        when (element) {
            is Node.Jump -> visit(element.target)
            is Node.Branch -> element.targets.forEach(::visit)
        }

        done.add(element)
    }
}

/**
 * Visits the whole graph depth-first, tags any edges going back to a node in the trace as a back edge.
 * Hitting a previously hit node that is not along the current path is a forward edge.
 * This method also populates the incomingEdges variables of each Node
 */
fun preprocessEdges(graph: Graph) {
    val trace = mutableListOf<Node>()
    val done = mutableSetOf<Node>()

    fun visitNode(element: Node) {
        trace.add(element)

        fun visit(edge: Edge) {
            edge.target.incommingEdges.add(edge)
            if (done.contains(edge.target)) {
                if (trace.contains(edge.target)) {
                    edge.edgeType = EdgeType.BACK
                } else {
                    edge.edgeType = EdgeType.FORWARD
                }
            } else {
                if (!trace.contains(edge.target)) {
                    visitNode(edge.target)
                    edge.edgeType = EdgeType.FORWARD
                } else {
                    edge.edgeType = EdgeType.BACK
                }
            }
        }

        when (element) {
            is Node.Jump -> visit(element.target)
            is Node.Branch -> element.targets.forEach(::visit)
        }

        done.add(element)
        trace.removeLast()
    }
    visitNode(graph.entryNode)
}

fun Node.immediateSuccessors(): List<Node> = when (this) {
    is Node.Branch -> targets.map { it.target }
    is Node.Jump -> listOf(target.target)
}

fun checkIncommingEdges(graph: Graph) {
    fun checkEdge(e: Edge) {}
    fun checkNode(node: Node) {
        for (succ in node.immediateSuccessors()) {
            assert(succ.incommingEdges.find { it.source == node } != null)
        }
    }
    visitGraph(graph, ::checkNode, ::checkEdge)
}

fun createDomtree() {
    // http://www.cs.rice.edu/~keith/EMBED/dom.pdf
}

class CFGGraphPrinter(output: Writer) : DotPrinter(output) {

    init {
        output += "digraph MethodCFG {"
        indent++
        output += "bgcolor=transparent;"
    }

    fun print(graph: Graph, nodeColor: String) {
        val nodeAppearance = NodeAppearance(fillColor = nodeColor, style = "filled")
        val ControlFlow = DotPrinter.ArrowStyle(arrowHead = "normal")

        fun nodeVisitor(node: Node) {
            this.node(node.name, if (node is Node.Branch) "X" else "", nodeAppearance)
        }
        fun edgeVisitor(edge: Edge) {
            val label = when(edge.edgeType) {
                EdgeType.FORWARD -> ""
                EdgeType.BACK -> "back"
                EdgeType.UNDEF -> "undef"
            }
            this.arrow(edge.source.name, edge.target.name, ControlFlow, label)
        }
        visitGraph(graph, ::nodeVisitor, ::edgeVisitor)
    }

    fun finish() {
        indent--
        output += "}"
    }
}