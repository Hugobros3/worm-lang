package boneless

import boneless.bind.bind
import boneless.emit.emit
import boneless.parse.Parser
import boneless.parse.Tokenizer
import boneless.type.type
import org.junit.Test
import java.io.File

class TestJVMCodegen {

    private fun module(moduleName: String, str: String): Module {
        val parser =
            Parser(str, Tokenizer(str).tokenize())
        val module = parser.parseModule(moduleName)
        bind(module)
        type(module)

        //val module_serialized = module.prettyPrint(printInferredTypes = true)
        //println(module_serialized)
        return module
    }

    @Test
    fun testEmitVoidModule() {
        val mod = module("VoidFn","""
            fn f() => ();
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }

    @Test
    fun testEmitIntModule() {
        val mod = module("IntFn","""
            fn f() => 42;
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }

    @Test
    fun testEmitIntIntModule() {
        val mod = module("IntIntFn","""
            fn f(i: I32) => i;
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }

    @Test
    fun testEmitLetBasicModule() {
        val mod = module("EmitLetBasic","""
            fn f() => {
                let nice = 69;
                nice
            };
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }

    @Test
    fun testTupleBasic() {
        val mod = module("TupleBasic","""
            fn f() => {
                let pair = (2, 3);
                pair
            };
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }

    @Test
    fun testExtractTuple() {
        val mod = module("ExtractTuple","""
            type Pair = [I32, I32];
            fn f(pair: Pair) => {
                let (x, y) = pair;
                x
            };
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }

    @Test
    fun testExtractComplicated() {
        val mod = module("ExtractComplicated","""
            fn f() => {
                let big = ((0, 4, 5, 1), (), (9999));
                let ((a, b, c, d), x, z) = big;
                (z, (d, a, b))
            };
        """.trimIndent())

        val outputDir = File("test_out/")
        emit(mod, outputDir)
    }
}