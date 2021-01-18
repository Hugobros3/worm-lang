package boneless

import boneless.bind.bind
import boneless.emit.emit
import boneless.parse.Parser
import boneless.parse.Tokenizer
import boneless.type.type
import org.junit.Test
import java.io.File

class TestJVMCodegen {

    private fun module(str: String): Module {
        val parser =
            Parser(str, Tokenizer(str).tokenize())
        val module = parser.parseModule()
        bind(module)
        type(module)

        //val module_serialized = module.prettyPrint(printInferredTypes = true)
        //println(module_serialized)
        return module
    }

    @Test
    fun testEmitVoidModule() {
        val mod = module("""
            fn f() => ();
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }
}