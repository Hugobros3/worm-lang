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
            def foo = {
                let x = 56;
                let y = (x, 9);
            };
            """.trimIndent()
        )
    }

    @Test
    fun testPattern() {
        testBind("""
        fn f1 x => x;
        fn f2 (x) => x;
        fn f3 (x, y) => x;
        fn f4 (first = a, second = b) => a + b;
        fn f5 () => 0;
        fn f6 EmptyCtor () => 0;
        fn f7 Pos2D (a, b) => b;
        fn f8 Vector (x = a, y = b, z = c) => a * b * c;
        fn f9 Rect ( Pos2D (x1, y1), Pos2D (x2, y2) ) => (x2 - x1) * (y2 - y1);
        
        // don't mind those
        data EmptyCtor = [];
        data Pos2D = [I32, I32];
        data Vector = [x = F32, y = F32, z = F32];
        data Rect = [Pos2D, Pos2D];
        """.trimIndent())
    }
}