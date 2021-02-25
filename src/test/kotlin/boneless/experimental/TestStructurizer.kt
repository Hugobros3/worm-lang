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

            // seed = -3218575443370169431
            // seed = -9169918898909610541
            // seed = 5059993167240053725
            // seed = -3428758823127463859
             seed = -3906915954911236504
            // seed = -8851239485985846864
            //seed = 7768002510966139666

            val rnd = Random(seed)
            fun <E> List<E>.seededRandom(): E {
                assert(this.isNotEmpty())
                val i = rnd.nextInt(size)
                return this[i]
            }

            val nodesCount = 32; // 4 + rnd.nextInt(8)
            val nodes = (0..nodesCount).map { Node(name()) }

            for ((i, node) in nodes.withIndex()) {
                if (i == 0) {
                    node.body = Node.Body.Exit()
                }

                val branchWeight = 0.35
                if (rnd.nextFloat() < branchWeight) {
                    val branches = mutableListOf(Edge(node, nodes.seededRandom()), Edge(node, nodes.seededRandom()))
                    node.body = Node.Body.Branch(branches)
                } else {
                    node.body = Node.Body.Jump(Edge(node, nodes.seededRandom()))
                }
            }

            val graph = Graph(nodes.seededRandom())
            if (isReachable(graph, nodes[0], graph.entryNode, AllowBackEdges.Yes))
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
        var graph = make_random_cfg()

        val colours = listOf(
            "springgreen",
            "springgreen1",
            "springgreen2",
            "springgreen3",
            "springgreen4",
            "steelblue",
            "steelblue1",
            "steelblue2",
            "steelblue3",
            "steelblue4",
            "   tan   ",
            "   tan1   ",
            "   tan2   ",
            "   tan3   ",
            "   tan4  ",
            "thistle",
            "thistle1",
            "thistle2",
            "thistle3",
            "thistle4",
            "tomato",
            "tomato1",
            "tomato2",
            "tomato3",
            "tomato4",
            "transparent",
            "turquoise",
            "turquoise1",
            "turquoise2",
            "turquoise3",
            "turquoise4",
            "violet",
            "violetred",
            "violetred1",
            "violetred2",
            "violetred3",
            "violetred4",
            "wheat",
            "wheat1",
            "wheat2",
            "wheat3",
            "wheat4",
            "white",
            "whitesmoke",
            "yellow",
            "yellow1",
            "yellow2",
            "yellow3",
            "yellow4",
            "yellowgreen"
        )
        var colour = 0

        preprocessEdges(graph)
        checkIncommingEdges(graph)

        val p = CFGGraphPrinter(w)

        // p.print(graph, colours[(colour++) % colours.size])

        /*for (i in 0 until 3) {
            catchLoopExits(graph)
            p.print(graph, colours[(colour++) % colours.size], "relooped_${i}_")

            graph = recreate(graph)
            preprocessEdges(graph)
            checkIncommingEdges(graph)
            p.print(graph, colours[(colour++) % colours.size], "recreated_${i}_")
            removeUpdatedMarkers(graph)
        }*/

        val forest = get_loop_forest(graph)
        forest.forEach {
            println("scc: $it")
        }
        p.print(graph, colours[(colour++) % colours.size])
        p.print(graph, colours[(colour++) % colours.size], prefix = "forest_", forest = forest)

        p.finish()

        w.close()
    }
}