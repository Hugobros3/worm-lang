package boneless

import org.junit.Test

class TestBinder {

    private fun testBind(str: String) {
        val parser = Parser(str, Tokenizer(str).tokenize())
        val module = parser.parseModule()
        val module_serialized = module.prettyPrint()
        println(module_serialized)

        bind(module)
    }

    @Test
    fun testBinderBasic() {
        testBind(
            """
            def foo :: {
                let x = 56;
                let y = (x, 9);
            };
            """.trimIndent()
        )
    }

    @Test
    fun testPattern() {
        testBind("""
        def f1 :: fn x => x;
        def f2 :: fn (x) => x;
        def f3 :: fn (x, y) => x;
        def f4 :: fn (first = a, second = b) => a + b;
        def f5 :: fn () => 0;
        """.trimIndent())
    }
}