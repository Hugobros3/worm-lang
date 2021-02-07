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

        val module_serialized = module.prettyPrint(printInferredTypes = false)
        val module_serialized2 = module.prettyPrint(printInferredTypes = true)
        println(module_serialized)
        println(module_serialized2)
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
            fn f() => {
                let x1 = (- 6);
                let x2 = (3 + 6);
                let x3 = (3 - 6);
                let x4 = (3 * 6);
                let x5 = (3 / 6);
                let x6 = (3 % 6);
            };
        """.trimIndent())

        testType("""
            fn pow2(x: I32) => x * x;
            
            data Pos = [I32, I32];
            data Rect = [min = Pos, max = Pos];
            fn area Rect(min = Pos (sx, sy), max = Pos (ex, ey)) -> I32 = (ex - sx) * (ey - sy);
        """.trimIndent())
    }

    @Test
    fun testFloats() {
        testType("""
            fn f(f1: F32) => {
                3.0 * f1
            };
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

    @Test
    fun testTypeParams() {
        testType("""
            forall T
            fn mk_pair (a: T) => (a, a);
            
            fn f() => {
                mk_pair::I32 (2);
            };
            
            fn g() => {
                let f1 = mk_pair::I32;
                let f2 : fn I32 -> [I32, I32] = f1;
                let f3 : fn I32 -> [I32, I32] = mk_pair::I32;
            };
        """.trimIndent())

        testType("""
            forall T
            data Option = [some = T | none = []];
            
            fn f(o: Option::I32) => {
            };
        """.trimIndent())
    }

    @Test
    fun testContracts() {
        testType("""
            forall T
            contract Foo = [
                fooerize = fn T -> I32
            ];
            
            instance Foo::I32 = (
                fooerize = fn t => t
            );
            
            fn f() => {
                let (fooerize = f2) = Foo::I32;
                f2 96
            };
            
            fn g() => {
                Foo::I32.fooerize 96
            };
        """.trimIndent())

        expectFailure {
            testType("""
            forall T
            contract Foo = [
                fooerize = fn T -> I32
            ];
            
            fn g() => {
                Foo::I32.fooerize 96
            };
        """.trimIndent())
        }

        testType("""
            forall T
            contract Foo = [
                fooerize = fn T -> I32
            ];
            
            forall T
            instance Foo::T = (
                fooerize = fn t => 0
            );
            
            fn g() => {
                Foo::I32.fooerize 96
            };
        """.trimIndent())
    }

    @Test
    fun testProjection() {
        testType("""
            forall T1, T2
            data Pair = [first = T1, second = T2];
            
            fn sum_pair(p: Pair::(I32, I32)) => p.first + p.second;
        """.trimIndent())
    }

    @Test
    fun testTypeArgsInference() {
        testType("""
            forall T
            fn mk_pair (a: T) => (a, a);
            
            fn f() => {
                mk_pair (2);
            };
        """.trimIndent())

        testType("""
            forall T
            contract Foo = [
                fooerize = fn T -> I32
            ];
            
            forall T
            instance Foo::T = (
                fooerize = fn t => 0
            );
            
            fn f() => {
                Foo.fooerize 96
            };
        """.trimIndent())
    }

    @Test
    fun testTypeArgsInferenceReverse() {
        testType("""
            forall T
            contract random = fn [] -> T;
            
            instance random::I32 = fn () => 5; // chosen by dice roll, guaranteed to be fair
            
            fn f() => {
                let x: I32 = random();
            };
        """.trimIndent())
    }

    @Test
    fun testVector() {
        testType("""
            forall T
            data Vec3 = [x = F32, y = F32, z = F32];
            
            fn dot(a: Vec3::F32, b: Vec3::F32) => a.x * b.x + a.y * b.y + a.z * b.z;
            fn dot2(a: Vec3::F32, b: Vec3::F32) -> F32 = dot(a, b);
        """.trimIndent())
    }

    @Test
    fun testRectangle() {
        testType("""
            data Point = [I32, I32];
            data Rectangle = [min = Point, max = Point];

            forall T
            contract Shape = [ get_area = fn T -> I32 ];

            instance Shape::Rectangle = (
                get_area = fn Rectangle(min = Point (sx, sy), max = Point (ex, ey)) => (ex - sx) * (ey - sy)
            );
        """.trimIndent())
    }

    @Test
    fun testSubtypingBasic() {
        testType("""
            fn f() => {
                let x: [a = I32] = (a = 36, b = 37);
                let y: [a = I32 | b = F32] = (a = 36);
                let z: [I32, F32] = (a = 36, b = 0.5);
                
                let w: [I32..3] = (1, 2, 3);
                let r: [I32, I32, I32] = w;
            };
        """.trimIndent())
    }

    @Test
    fun testSubtypingFunctions() {
        testType("""
            fn f(
                a: fn [a = I32, b = I32] -> []
            ) => {
                let x: fn [a = I32, b = I32] -> [] = a;
                let y: fn [a = I32, b = I32, c = I32] -> [] = a;
            };
        """.trimIndent())

        expectFailure {
            testType("""
                fn f(
                    a: fn [a = I32, b = I32] -> []
                ) => {
                    let y: fn [a = I32] -> [] = a;
                };
            """.trimIndent())
        }
    }

    @Test
    fun testPatternSubtyping() {
        testType("""
            fn f1 a => a;
            fn f2 a: Top => a;
            fn f3 a -> Top = a;
        """.trimIndent())
    }

    @Test
    fun testUndef() {
        testType("""
            fn f() => {
                let x: I32 = undef();
            };
        """.trimIndent())
    }

     @Test
     fun testCast() {
         testType("""
             fn f(
                a: [a = I32, b = I32]
            ) => {
                let x = a as [a = I32];
            };
         """.trimIndent())

         expectFailure {
             testType("""
                 fn f a => (a, a as I32);
             """.trimIndent())
         }
     }

    @Test
    fun testMut() {
        testType("""
            fn f() => {
                let mut x: I32 = 0;
            };
        """.trimIndent())
    }
}