package boneless

import org.junit.Test

class TestType {
    private fun testType(str: String) {
        val parser = Parser(str, Tokenizer(str).tokenize())
        val module = parser.parseModule()
        bind(module)
        type(module)

        val module_serialized = module.prettyPrint()
        println(module_serialized)
    }

    @Test
    fun testTypeBasic() {
        testType("""
            def f1 = 5;
            def f2 = ();
            def f3 = (1, 2, 3);
            def f4 = (x = 1, y = 2);
            def f5 = fn x: I32 => (x,x);
        """.trimIndent())
    }

    @Test
    fun testTypeData() {
        testType("""
            data Empty = [];
            fn f1 Empty () => 0;
            fn f2 (Empty ()) => 0;
            
            data Pos = [I32, I32];
            def g1 = Pos (1, 98);
            fn g2 x: I32 => Pos (x, 98);
            fn g3 Pos(x, y) => y;
        """.trimIndent())
    }

    @Test
    fun testTypeAnnotation() {
        testType("""
            def f1 : I32 = 5;
        """.trimIndent())

        expectFailure {
            testType("""
                def f1 : [] = 5;
            """.trimIndent())
        }
    }

    @Test
    fun testBuiltins() {
        for (builtin in BuiltinFn.values()) {
            println(builtin.type)
        }

        testType("""
            fn pow2(x: I32) => x * x;
            
            data Pos = [I32, I32];
            data Rect = [min = Pos, max = Pos];
            fn area Rect(min = Pos (sx, sy), max = Pos (ex, ey)) -> I32 = (ex - sx) * (ey - sy);
        """.trimIndent())
    }
}