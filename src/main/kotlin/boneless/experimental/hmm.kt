package boneless.experimental

fun getNodes(graph: Graph): List<Node> {
    val nodes = mutableListOf<Node>()
    fun checkEdge(e: Edge) {}
    fun checkNode(node: Node) {
        nodes += node
    }
    visitGraph(graph, ::checkNode, ::checkEdge)
    return nodes
}

fun getEdges(graph: Graph): List<Edge> {
    val edges = mutableListOf<Edge>()
    fun checkEdge(e: Edge) {
        edges += e
    }
    fun checkNode(node: Node) {}
    visitGraph(graph, ::checkNode, ::checkEdge)
    return edges
}

// https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
fun tarjan_scc(graph: Graph): MutableList<Set<Node>> {
    val sccs = mutableListOf<Set<Node>>()
    val stack = mutableListOf<Node>()
    var counter = 0

    fun strong_connect(node: Node) {
        node.index = counter++
        node.lowlink = node.index
        stack.add(node)
        node.onStack = true

        for (edge in node.immediateSuccessors()) {
            // ignore loopback edges
            if (edge.loopbackEdge)
                continue

            val succ = edge.dest
            if (succ.index == -1) {
                strong_connect(succ)
                node.lowlink = Math.min(node.lowlink, succ.lowlink)
            } else if (succ.onStack) {
                node.lowlink = Math.min(node.lowlink, succ.lowlink)
            }
        }

        if (node.lowlink == node.index) {
            val scc = mutableSetOf<Node>()
            do {
                val popped = stack.removeLast()
                popped.onStack = false
                scc += popped
            } while(popped != node)
            sccs += scc
        }
    }

    for (node in getNodes(graph)) {
        if (node.index == -1)
            strong_connect(node)
    }

    return sccs
}

typealias HFun = (Graph, Set<Node>) -> Set<Node>

fun entry_vertices(graph: Graph, scc: Set<Node>): Set<Node> {
    val set = mutableSetOf<Node>()
    for (node in scc) {
        for (pred in node.incommingEdges) {
            if (pred.source !in scc)
                set.add(node)
        }
        if (node == graph.entryNode)
            set.add(node)
    }
    return set
}

var lgid = 0
data class Loop(val header: Set<Node>, val body: Set<Node>, val backedges: List<Edge>) {
    val subloops = mutableListOf<Loop>()

    val id = lgid++
    val colour = colours.removeAt((0 until colours.size).random())

    override fun toString(): String =
        "[" +
        "header: {" + header.joinToString { it.name } + "}" +
        ", body: {" + body.joinToString { it.name } + "}" +
        ", subs: {" + subloops.joinToString { it.toString() } + "}" +
        "]"
}

fun non_trivial(graph: Graph, scc: Set<Node>): Boolean {
    if (scc.size > 1)
        return true
    for (node in scc) {
        for (edge in node.immediateSuccessors()) {
            if (!edge.loopbackEdge && edge.dest == node)
                return true
        }
    }

    return false
}

val colours = mutableListOf("springgreen", "springgreen1", "springgreen2", "springgreen3", "springgreen4", "steelblue", "steelblue1", "steelblue2", "steelblue3", "steelblue4", "   tan   ", "   tan1   ", "   tan2   ", "   tan3   ", "   tan4  ", "thistle", "thistle1", "thistle2", "thistle3", "thistle4", "tomato", "tomato1", "tomato2", "tomato3", "tomato4", "transparent", "turquoise", "turquoise1", "turquoise2", "turquoise3", "turquoise4", "violet", "violetred", "violetred1", "violetred2", "violetred3", "violetred4", "wheat", "wheat1", "wheat2", "wheat3", "wheat4", "white", "whitesmoke", "yellow", "yellow1", "yellow2", "yellow3", "yellow4", "yellowgreen")

fun make_loops(graph: Graph, sccs: List<Set<Node>>): MutableList<Loop> {
    val h_fun: HFun = ::entry_vertices

    val loops = mutableListOf<Loop>()
    for (scc in sccs) {
        if (non_trivial(graph, scc)) {
            val header = h_fun(graph, scc)
            for (node in header) {
                node.isLoopHeader = true
            }
            val backedges = mutableListOf<Edge>()
            for (node in scc) {
                for (edge in node.immediateSuccessors()) {
                    if (edge.dest in header) {
                        backedges += edge
                        edge.loopbackEdge = true
                    }
                }
            }
            val loop = Loop(header, scc, backedges)
            for (node in scc) {
                node.loop = loop
            }
            loops += loop
        }
    }
    return loops
}

fun get_loop_forest(graph: Graph): MutableList<Loop> {
    val loops = make_loops(graph, tarjan_scc(graph))
    do {
        for (node in getNodes(graph)) {
            node.index = -1
            node.lowlink = -1
        }
        val newloops = make_loops(graph, tarjan_scc(graph))
        for (loop in newloops) {
            var insertionPoint: Loop? = null
            while (true) {
                var found = false
                val potential_parents = insertionPoint?.subloops ?: loops
                for (potential_parent in potential_parents) {
                    if (potential_parent.body.containsAll(loop.header)) {
                        insertionPoint = potential_parent
                        found = true
                        break
                    }
                }
                if (!found)
                    break
            }
            insertionPoint?.subloops?.add(loop) ?: throw Exception("loop found at iteration > 1 can't be top level")
            //loops += loop
        }
        if (newloops.isEmpty())
            break
    } while (true)
    return loops
}