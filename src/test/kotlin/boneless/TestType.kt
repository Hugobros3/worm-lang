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
            def f1 :: 5;
            def f2 :: ();
            def f3 :: (1, 2, 3);
            def f4 :: (x = 1, y = 2);
            def f5 :: fn x: I32 => (x,x);
        """.trimIndent())
    }

    @Test
    fun testTypeData() {
        testType("""
            def Empty :: data [];
            def f1 :: fn Empty () => 0;
            
            def Pos :: data [I32, I32];
            def g1 :: Pos (1, 98);
            def g2 :: fn x: I32 => Pos (x, 98);
            def g3 :: fn Pos(x, y) => y;
        """.trimIndent())
    }

    @Test
    fun testTypeAnnotation() {
        testType("""
            def f1 : I32 :: 5;
        """.trimIndent())

        expectFailure {
            testType("""
                def f1 : [] :: 5;
            """.trimIndent())
        }
    }

    @Test
    fun testBuiltins() {
        for (builtin in BuiltinFn.values()) {
            println(builtin.type)
        }

        testType("""
            def pow2 :: fn (x: I32) => x * x;
            
            def Pos :: data [I32, I32];
            def Square :: data [min :: Pos, max :: Pos];
            def area :: fn Square(min = Pos (sx, sy), max = Pos (ex, ey)) => (ex - sx) * (ey - sy);
        """.trimIndent())
    }
}