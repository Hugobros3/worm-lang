package boneless.experimental

import boneless.experimental.Node.Body
import boneless.util.DotPrinter
import java.io.Writer

data class Graph(val entryNode: Node)

class Node(val name: String) {

    // homebrew algo garbo
    val exits = mutableListOf<Edge>()
    val rewriteEdges = mutableMapOf<Int, Edge>()


    val incommingEdges = mutableListOf<Edge>()

    // SCC crap
    var index = -1
    var lowlink = -1
    var onStack = false

    var isLoopHeader = false
    var loop: Loop? = null

    lateinit var body: Body
    sealed class Body {
        class Exit() : Body() {}
        class Jump(val target: Edge) : Body() {}
        class Branch(val targets: List<Edge>) : Body() {}
    }

    override fun toString(): String {
        return "Node(name='$name', exits=$exits)"
    }
}

class Edge(val source: Node, val dest: Node) {
    var edgeType = EdgeType.UNDEF
    var isSynthetic: Boolean = false
    var isRewritten: Boolean = false

    /** "exit" edges are not actually taken, they are just there to represent the non-local jump that once existed */
    var isExit: Boolean = false
    var needsRewrite: Boolean = false
    lateinit var loopHead: Node

    var loopbackEdge: Boolean = false

    override fun toString(): String {
        return "Edge(source=$source, dest=$dest)"
    }
}

enum class EdgeType {
    FORWARD,
    BACK,
    UNDEF,
}

sealed class AllowBackEdges {
    object Yes : AllowBackEdges()
    object No : AllowBackEdges()
    data class OnlyFor(val node: Node) : AllowBackEdges()
}

fun visitGraph(graph: Graph, nodeVisitor: (Node) -> Unit, edgeVisitor: (Edge) -> Unit, entry: Node = graph.entryNode, visitExits: Boolean = false, allowBackEdges: AllowBackEdges = AllowBackEdges.Yes) {
    val stack = mutableListOf<Node>()
    val done = mutableSetOf<Node>()

    stack.add(entry)
    while (stack.isNotEmpty()) {
        val element = stack.removeLast()
        assert(!done.contains(element))
        nodeVisitor(element)

        fun visit(edge: Edge) {
            if (edge.edgeType == EdgeType.BACK) {
                when (allowBackEdges) {
                    AllowBackEdges.Yes -> { }
                    AllowBackEdges.No -> return
                    is AllowBackEdges.OnlyFor -> {
                        if (allowBackEdges.node != edge.dest)
                            return
                    }
                }
            }
            edgeVisitor(edge)
            if (!done.contains(edge.dest) && !stack.contains(edge.dest) && edge.dest != element) {
                stack.add(edge.dest)
            }
        }

        element.immediateSuccessors().forEach(::visit)

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
                    //edge.edgeType = EdgeType.BACK
                } else {
                    //edge.edgeType = EdgeType.FORWARD
                }
            } else {
                if (!trace.contains(edge.dest)) {
                    visitNode(edge.dest)
                    //edge.edgeType = EdgeType.FORWARD
                } else {
                    //edge.edgeType = EdgeType.BACK
                    //edge.dest.isLoopHeader = true
                }
            }
        }

        element.immediateSuccessors().forEach(::visit)

        done.add(element)
        trace.removeLast()
    }
    visitNode(graph.entryNode)
}

fun Node.immediateSuccessors(): List<Edge> = when (val body = body) {
    is Node.Body.Branch -> body.targets
    is Node.Body.Jump -> listOf(body.target)
    is Node.Body.Exit -> emptyList()
}

fun checkIncommingEdges(graph: Graph) {
    fun checkEdge(e: Edge) {}
    fun checkNode(node: Node) {
        for (succ in node.immediateSuccessors()) {
            assert(succ.dest.incommingEdges.find { it.source == node } != null)
        }
    }
    visitGraph(graph, ::checkNode, ::checkEdge)
}

fun isReachable(graph: Graph, target: Node, from: Node, allowBackEdges: AllowBackEdges): Boolean {
    if (target == from)
        return true

    var reachable = false
    fun checkEdge(e: Edge) {}
    fun checkNode(node: Node) {
        if (node == target)
            reachable = true
    }
    visitGraph(graph, ::checkNode, ::checkEdge, from, allowBackEdges = allowBackEdges)
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

    val loops = mutableMapOf<Node, MutableSet<Node>>()

    for (backedge in backedges) {
        val loopHead = backedge.dest
        // Recursively visit the predecessors of the back edge origin up until we find a node
        // that is not a successor of the head
        val todo = mutableListOf<Node>(backedge.source)
        val once = mutableSetOf<Node>()

        val handled = loops.getOrPut(loopHead) {
            mutableSetOf<Node>()
        }

        while (todo.isNotEmpty()) {
            val node = todo.removeLast()
            assert(isReachable(graph, node, loopHead, allowBackEdges = AllowBackEdges.No)) {
                println("woo")
                isReachable(graph, node, loopHead, allowBackEdges = AllowBackEdges.No)
            }

            // External edges are allowed out of the loop head
            if (node == loopHead) {
                continue
            }

            for (pred in node.incommingEdges) {
                if (pred.edgeType != EdgeType.BACK && isReachable(
                        graph,
                        pred.source,
                        loopHead,
                        allowBackEdges = AllowBackEdges.No
                    )
                ) {
                    if (!once.contains(pred.source)) {
                        todo.add(pred.source)
                        once.add(pred.source)
                    }
                }
            }

            // Find any branches that cannot reach the loop head anymore and force them to go there
            val body = node.body
            if (body is Node.Body.Branch) {
                var body: Node.Body.Branch = body
                if (handled.contains(node)) continue
                for (i in 0 until body.targets.size) {
                    val originalEdge = body.targets[i]
                    if (!isReachable(graph, loopHead, originalEdge.dest, allowBackEdges = AllowBackEdges.OnlyFor(loopHead)
                        )/* || (originalEdge.edgeType == EdgeType.BACK && originalEdge.dest != loopHead)*/) {
                        val toHead = Edge(originalEdge.source, loopHead)
                        toHead.isSynthetic = true

                        /*val newTargets = MutableList(body.targets.size) {body.targets[it]}
                        newTargets[i] = toHead
                        body = Node.Body.Branch(newTargets)
                        node.body = body*/
                        node.rewriteEdges[i] = toHead
                        originalEdge.needsRewrite = true

                        val exitEdge = Edge(originalEdge.source, originalEdge.dest)
                        exitEdge.isSynthetic = true
                        exitEdge.isExit = true
                        exitEdge.loopHead = loopHead

                        loopHead.exits.add(exitEdge)
                        println("${node.name} is a problem area for ${loopHead.name}")
                    }
                }
                handled.add(node)
            }
        }
    }
}

fun recreate(graph: Graph): Graph {
    val newNodes = mutableMapOf<Node, Node>()

    fun recreateNode(node: Node): Node {
        val newNode: Node = Node(node.name)
        newNodes[node] = newNode

        val targets = mutableListOf<Edge>()
        for ((i, edge) in node.immediateSuccessors().withIndex()) {
            var edge = node.rewriteEdges[i] ?: edge

            val newSource = newNodes.getOrPut(edge.source) {
                recreateNode(edge.source)
            }
            val newDest = newNodes.getOrPut(edge.dest) {
                recreateNode(edge.dest)
            }

            val newEdge = Edge(newSource, newDest)
            // newEdge.isSynthetic = edge.isSynthetic
            if (node.rewriteEdges[i] != null)
                newEdge.isRewritten = true
            if (targets.find { it.source == newEdge.source && it.dest == newEdge.dest } == null)
                targets.add(newEdge)
        }

        for (exit in node.exits) {
            val newSource = newNodes.getOrPut(exit.loopHead) {
                recreateNode(exit.loopHead)
            }
            val newDest = newNodes.getOrPut(exit.dest) {
                recreateNode(exit.dest)
            }

            val newEdge = Edge(newSource, newDest)
            newEdge.isSynthetic = true
            if (targets.find { it.source == newEdge.source && it.dest == newEdge.dest } == null)
                targets.add(newEdge)
        }

        when {
            targets.isEmpty() -> {
                newNode.body = Body.Exit()
            }
            targets.size == 1 -> {
                newNode.body = Body.Jump(targets[0])
            }
            else -> {
                newNode.body = Body.Branch(targets)
            }
        }

        return newNode
    }

    val newRoot = newNodes.getOrPut(graph.entryNode) {
        recreateNode(graph.entryNode)
    }
    return Graph(newRoot)
}

fun removeUpdatedMarkers(graph: Graph) {
    fun checkEdge(e: Edge) {
        e.isSynthetic = false
        e.isRewritten = false
    }
    fun checkNode(node: Node) {}
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

    fun print(graph: Graph, nodeColor: String, prefix: String = "", forest: List<Loop>? = null) {
        val nodeAppearance = NodeAppearance(fillColor = nodeColor, style = "filled")
        val ControlFlow = DotPrinter.ArrowStyle(arrowHead = "normal")
        val Synthetic = DotPrinter.ArrowStyle(arrowHead = "normal", color = "blue", style = "dashed")
        val Rewrote = DotPrinter.ArrowStyle(arrowHead = "normal", color = "blue", style = "solid")
        val ExitFlowReal = DotPrinter.ArrowStyle(arrowHead = "normal", color = "red", style = "dashed")
        val ExitFlowOg = DotPrinter.ArrowStyle(arrowHead = "normal", color = "red", style = "dotted")
        val LoopbackEdge = DotPrinter.ArrowStyle(arrowHead = "normal", color = "red", style = "solid")

        fun nodeVisitor(node: Node) {
            var shape = if (node == graph.entryNode) nodeAppearance.copy(shape = "rectangle") else nodeAppearance
            if (node.isLoopHeader)
                shape = shape.copy(shape = "diamond")
            if (node.loop != null) {
                shape = shape.copy(fillColor = node.loop!!.colour)
            }
            this.node(prefix + node.name, if (node.body is Node.Body.Branch) "X ${node.name}" else node.name, shape)
        }
        fun nodeVisitorNOP(node: Node) {}

        fun edgeVisitor(edge: Edge) {
            if (edge.isExit) {
                //this.arrow(prefix + edge.source.name, prefix + edge.dest.name, ExitFlowOg, "")
                this.arrow(prefix + edge.loopHead.name, prefix + edge.dest.name, ExitFlowReal, "")
                return
            }

            val label = when (edge.edgeType) {
                EdgeType.FORWARD -> ""
                EdgeType.BACK -> "back"
                EdgeType.UNDEF -> "undef"
            }

            var appearance = ControlFlow
            if (edge.isSynthetic)
                appearance = Synthetic
            if (edge.isRewritten)
                appearance = Rewrote
            if (edge.needsRewrite)
                appearance = ExitFlowOg
            if (edge.loopbackEdge)
                appearance = LoopbackEdge

            this.arrow(prefix + edge.source.name, prefix + edge.dest.name, appearance, label)
        }

        if (forest != null) {
            val notInLoops = getNodes(graph).toMutableSet()
            fun printLoop(loop: Loop) {
                output += "subgraph cluster_${loop.id} {"
                indent++
                val uniqueNodes = loop.body.toMutableSet()
                for (subloop in loop.subloops) {
                    uniqueNodes.removeAll(subloop.body)
                    notInLoops.removeAll(subloop.body)
                    printLoop(subloop)
                }
                for (node in uniqueNodes)
                    nodeVisitor(node)
                indent--
                output += "}"
            }
            for (loop in forest)
                printLoop(loop)
            for (node in notInLoops)
                nodeVisitor(node)
        }

        visitGraph(graph, if (forest != null) ::nodeVisitorNOP else ::nodeVisitor, ::edgeVisitor, visitExits = true)
    }

    fun finish() {
        indent--
        output += "}"
    }
}