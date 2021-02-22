package boneless.experimental

import org.junit.Test
import java.io.File
import java.util.*

class TestStructurizer {
    fun make_random_cfg(): Graph {
        var i = 0
        fun name(): String = "${i++}"

        val seeder = Random()
        while (true) {
            var seed = seeder.nextLong()
            println("seed: $seed")

            seed = 5666716588046080733

            val rnd = Random(seed)
            fun <E> List<E>.seededRandom(): E {
                assert(this.isNotEmpty())
                val i = rnd.nextInt(size)
                return this[i]
            }

            val nodesCount = 16; // 4 + rnd.nextInt(8)
            val nodes = (0..nodesCount).map {
                if (it == 0)
                    return@map Node.Exit(name())

                val branchWeight = 0.35
                if (rnd.nextFloat() < branchWeight) {
                    Node.Branch(name())
                } else {
                    Node.Jump(name())
                }
            }

            for (node in nodes) {
                when (node) {
                    is Node.Branch -> {
                        node.targets = mutableListOf(Edge(node, nodes.seededRandom()), Edge(node, nodes.seededRandom()))
                    }
                    is Node.Jump -> {
                        node.target = Edge(node, nodes.seededRandom())
                    }
                }
            }

            val graph = Graph(nodes.seededRandom())
            if (isReachable(graph, nodes[0], graph.entryNode))
                return graph
            else
                continue
        }
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

        catchLoopExits(graph)

        p.print(graph, "coral1", "relooped_")

        p.finish()

        w.close()
    }
}