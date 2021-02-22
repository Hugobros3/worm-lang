package boneless.experimental

import org.junit.Test
import java.io.File
import java.util.*

class TestStructurizer {
    fun make_random_cfg(): Graph {
        var i = 0
        fun name(): String = "${i++}"

        val rnd = Random()
        val nodesCount = 16; // 4 + rnd.nextInt(8)
        val nodes = (0..nodesCount).map {
            val branchWeight = 0.35
            if (rnd.nextFloat() < branchWeight) {
                Node.Branch(name())
            } else {
                Node.Jump(name())
            }
        }

        for (node in nodes) {
            when(node) {
                is Node.Branch -> {
                    node.targets = listOf(Edge(node, nodes.random()), Edge(node, nodes.random()))
                }
                is Node.Jump -> {
                    node.target = Edge(node, nodes.random())
                }
            }
        }

        return Graph(nodes.random())
    }

    @Test
    fun test() {
        val dotFile = File("test_out/structurizer/graph.dot")
        dotFile.parentFile.mkdirs()
        val w = dotFile.writer()
        val graph = make_random_cfg()

        preprocessEdges(graph)
        checkIncommingEdges(graph)

        val p = CFGGraphPrinter(w)

        p.print(graph, "aquamarine")

        p.finish()

        w.close()
    }
}