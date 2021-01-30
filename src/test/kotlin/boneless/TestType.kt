package boneless

import boneless.bind.bind
import boneless.core.BuiltinFn
import boneless.parse.Parser
import boneless.parse.Tokenizer
import boneless.type.type
import boneless.util.prettyPrint
import org.junit.Test

class TestType {
    private fun testType(str: String) {
        val parser =
            Parser(str, Tokenizer(str).tokenize())
        val module = parser.parseModule()
        bind(module)
        type(module)

        val module_serialized = module.prettyPrint(printInferredTypes = true)
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
            println(builtin.typeExpr)
        }

        testType("""
            fn pow2(x: I32) => x * x;
            
            data Pos = [I32, I32];
            data Rect = [min = Pos, max = Pos];
            fn area Rect(min = Pos (sx, sy), max = Pos (ex, ey)) -> I32 = (ex - sx) * (ey - sy);
        """.trimIndent())
    }

    @Test
    fun testLetInfer() {
        testType("""
            data Cheese = I32;
            type AlsoCheese = Cheese;
            fn f() => {
                let i = 2;
                let o: I64 = 2;
                let (x, y: I64) = (6, 9);
                let (w, (a, b: F32, c: I64)) = (0, (4, 5, 1));
                let (lower = lo: F32, upper = up) = (lower = 0, upper = 99.5);
                let cheese = Cheese(5);
                // is it the big cheese ?
                let Cheese(big) = cheese; // yes it is !
                // is it also cheese ?
                let is_it: AlsoCheese = cheese;
                
                let pair = (1, 2);
                let (p1, p2) = pair;
            };
        """.trimIndent())

        expectFailure {
            testType("""
                fn f() => {
                    let r = (a = 1, b = 2);
                    let (a, b) = r;
                };
            """.trimIndent())
        }
    }

    @Test
    fun testControlFlow() {
        testType("""
            fn f(a: Bool) => if a then 1 else -1;
        """.trimIndent())

        testType("""
            fn f(a: Bool) -> I32 = {
                while a do {
                    // a = a + 1;
                };
                -1
            };
        """.trimIndent())
    }
}