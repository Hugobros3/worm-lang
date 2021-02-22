package boneless.experimental

import boneless.util.DotPrinter
import java.io.Writer

data class Graph(val entryNode: Node)

sealed class Node(val name: String) {
    val exits = mutableListOf<Edge>()

    val incommingEdges = mutableListOf<Edge>()

    class Exit(name: String) : Node(name) {

    }
    class Jump(name: String) : Node(name) {
        lateinit var target: Edge
    }
    class Branch(name: String) : Node(name) {
        lateinit var targets: MutableList<Edge>
        val rgb = ""
    }

    override fun toString(): String {
        return "Node(name='$name', exits=$exits)"
    }
}

class Edge(val source: Node, val dest: Node) {
    var signature: List<Node.Branch>? = null
    var edgeType = EdgeType.UNDEF
    var isSynthetic: Boolean = false

    /** "exit" edges are not actually taken, they are just there to represent the non-local jump that once existed */
    var isExit: Boolean = false
    lateinit var loopHead: Node

    override fun toString(): String {
        return "Edge(source=$source, dest=$dest)"
    }
}

enum class EdgeType {
    FORWARD,
    BACK,
    UNDEF,
}

fun visitGraph(graph: Graph, nodeVisitor: (Node) -> Unit, edgeVisitor: (Edge) -> Unit, entry: Node = graph.entryNode, visitExits: Boolean = false) {
    val stack = mutableListOf<Node>()
    val done = mutableSetOf<Node>()

    stack.add(entry)
    while (stack.isNotEmpty()) {
        val element = stack.removeLast()
        assert(!done.contains(element))
        nodeVisitor(element)

        fun visit(edge: Edge) {
            edgeVisitor(edge)
            if (!done.contains(edge.dest) && !stack.contains(edge.dest) && edge.dest != element){
                stack.add(edge.dest)
            }
        }

        when (element) {
            is Node.Jump -> visit(element.target)
            is Node.Branch -> element.targets.forEach(::visit)
            is Node.Exit -> {}
        }

        if (visitExits) {
            element.exits.forEach(::visit)
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
            edge.dest.incommingEdges.add(edge)
            if (done.contains(edge.dest)) {
                if (trace.contains(edge.dest)) {
                    edge.edgeType = EdgeType.BACK
                } else {
                    edge.edgeType = EdgeType.FORWARD
                }
            } else {
                if (!trace.contains(edge.dest)) {
                    visitNode(edge.dest)
                    edge.edgeType = EdgeType.FORWARD
                } else {
                    edge.edgeType = EdgeType.BACK
                }
            }
        }

        when (element) {
            is Node.Jump -> visit(element.target)
            is Node.Branch -> element.targets.forEach(::visit)
            is Node.Exit -> {}
        }

        done.add(element)
        trace.removeLast()
    }
    visitNode(graph.entryNode)
}

fun Node.immediateSuccessors(): List<Node> = when (this) {
    is Node.Branch -> targets.map { it.dest }
    is Node.Jump -> listOf(target.dest)
    is Node.Exit -> emptyList()
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

fun isReachable(graph: Graph, target: Node, from: Node): Boolean {
    var reachable = false
    fun checkEdge(e: Edge) {}
    fun checkNode(node: Node) {
        if (node == target)
            reachable = true
    }
    visitGraph(graph, ::checkNode, ::checkEdge, from)
    return reachable
}

fun collectBackEdges(graph: Graph): List<Edge> {
    val backedges = mutableListOf<Edge>()
    fun checkEdge(e: Edge) {
        if (e.edgeType == EdgeType.BACK)
            backedges += e
    }
    fun checkNode(node: Node) {}
    visitGraph(graph, ::checkNode, ::checkEdge)
    return backedges
}

/**
 * Catches all paths that exit a loop without first going back through a backedge to the loop head, and change them to do so
 */
fun catchLoopExits(graph: Graph) {
    val backedges = collectBackEdges(graph)
    println("$backedges")
    for (backedge in backedges) {
        val loopHead = backedge.dest
        // Recursively visit the predecessors of the back edge origin up until we find a node
        // that is not a successor of the head
        val todo = mutableListOf<Node>(backedge.source)
        val once = mutableSetOf<Node>()

        val handled = mutableSetOf<Node>()

        while (todo.isNotEmpty()) {
            val node = todo.removeLast()
            assert(isReachable(graph, node, loopHead))
            for (pred in node.incommingEdges) {
                if (isReachable(graph, pred.source, loopHead)) {
                    if (!once.contains(pred.source)) {
                        todo.add(pred.source)
                        once.add(pred.source)
                    }
                }
            }

            // Find any branches that cannot reach the loop head anymore and force them to go there
            if (node is Node.Branch) {
                if (handled.contains(node)) continue
                for (i in 0 until node.targets.size) {
                    val originalEdge = node.targets[i]
                    if (!isReachable(graph, loopHead, originalEdge.dest)) {
                        /*val toHead = Edge(originalEdge.source, loopHead)
                        toHead.isSynthetic = true
                        node.targets[i] = toHead

                        val exitEdge = Edge(originalEdge.source, originalEdge.dest)
                        exitEdge.isSynthetic = true
                        exitEdge.isExit = true
                        exitEdge.loopHead = loopHead

                        loopHead.exits.add(exitEdge)*/
                        println("${node.name} is a problem area for ${loopHead.name}")
                    }
                }
                handled.add(node)
            }
        }
    }
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

    fun print(graph: Graph, nodeColor: String, prefix: String = "") {
        val nodeAppearance = NodeAppearance(fillColor = nodeColor, style = "filled")
        val ControlFlow = DotPrinter.ArrowStyle(arrowHead = "normal")
        val Synthetic = DotPrinter.ArrowStyle(arrowHead = "normal", color = "blue")
        val ExitFlowReal = DotPrinter.ArrowStyle(arrowHead = "normal", color = "red")
        val ExitFlowOg = DotPrinter.ArrowStyle(arrowHead = "normal", color = "red", style = "dotted")

        fun nodeVisitor(node: Node) {
            val shape = if (node == graph.entryNode) nodeAppearance.copy(shape = "rectangle") else nodeAppearance
            this.node(prefix + node.name, if (node is Node.Branch) "X ${node.name}" else node.name, shape)
        }
        fun edgeVisitor(edge: Edge) {
            if (edge.isExit) {
                this.arrow(prefix + edge.source.name, prefix + edge.dest.name, ExitFlowOg, "")
                this.arrow(prefix + edge.loopHead.name, prefix + edge.dest.name, ExitFlowReal, "")
                return
            }

            val label = when(edge.edgeType) {
                EdgeType.FORWARD -> ""
                EdgeType.BACK -> "back"
                EdgeType.UNDEF -> "undef"
            }
            this.arrow(prefix + edge.source.name, prefix + edge.dest.name, if (edge.isSynthetic) Synthetic else ControlFlow, label)
        }
        visitGraph(graph, ::nodeVisitor, ::edgeVisitor, visitExits = true)
    }

    fun finish() {
        indent--
        output += "}"
    }
}