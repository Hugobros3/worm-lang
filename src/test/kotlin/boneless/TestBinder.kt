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
        def f6 :: fn EmptyCtor () => 0;
        def f7 :: fn Pos2D (a, b) => b;
        def f8 :: fn Vector (x = a, y = b, z = c) => a * b * c;
        def f9 :: fn Rect ( Pos2D (x1, y1), Pos2D (x2, y2) ) => (x2 - x1) * (y2 - y1);
        
        // don't mind those
        def EmptyCtor :: data [];
        def Pos2D :: data [I32, I32];
        def Vector :: data [x :: F32, y :: F32, z :: F32];
        def Rect :: data [Pos2D, Pos2D];
        """.trimIndent())
    }
}