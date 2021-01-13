package boneless

import org.junit.Assert.assertTrue
import org.junit.Test

class TestParser {

    private fun testModule(str: String) {
        val parser = Parser(str, Tokenizer(str).tokenize())
        val program = parser.parseModule()
        val printedProgram = program.prettyPrint()
        println(printedProgram)

        val parser2 = Parser(printedProgram, Tokenizer(printedProgram).tokenize())
        val program2 = parser2.parseModule()
        assertTrue(program == program2)
    }

    private fun testParseSeq(str: String) {
        val parser = Parser(str, Tokenizer(str).tokenize())
        val seq = parser.parseSequenceContents()
        println(seq.prettyPrint())
    }

    private fun testParseType(str: String) {
        val p = Parser(str, Tokenizer(str).tokenize())
        val t = p.parseType()

        println("Parsed type as: $t")
        println("Printed form: " + t.prettyPrint())
    }

    @Test
    fun testParse() {

        testParseSeq("let x = 56;")

        testParseSeq("""
                let x = 56;
                let y = (x, 9);
            """.trimIndent())

        testModule("""
                def PersonIdentity :: data [
                    fullName :: String,
                    dob :: Date
                ];
            """.trimIndent())

        testModule("""
                // def x: String :: "a";
                def xx :: "a"; // TS will infer String 
            """.trimIndent())

        testModule("""
                /* block comment */
                def a :: 666;
                /*
                 * more complicated 
                 * /* block comment */ 
                 * to try and trick the parser
                */
            """.trimIndent())

        testModule("""
                def pair :: fn a => (a, a);
                def factorial :: fn (number: I32) => if number <= 1 then 1 else factorial (number - 1);
            """.trimIndent())

        testParseSeq("""
                let x = 5 * 4 + 3;
                let y = 5 + 4 * 3;
                
                let z = -5;
                let w = -z;
                let v = z - w;
                
                let t = ---------42;
                let u = ---------t;
                
                let a = - 4 * 5 : A;
                let b = 5 * -4 : A;
            """.trimIndent())

        testParseSeq("""
                let s = map (fn (x, y, z) => x + y + z) arr;
            """.trimIndent())

        testParseSeq("""
                let x = sum 2 (2 * reverse);
                let y = 3 * 3 francis;
            """.trimIndent())

        testModule("""
                def operations :: fn (a, b, c, d) => {
                    let x = a ^ b;
                    let y = !x | c;
                    let z = !!(x ^ c ^ d ^ y);
                };
            """.trimIndent())

        testModule("""
                def deref_s_ptr :: fn s: ref [I32, I32] => @s;
            """.trimIndent())
    }

    @Test
    fun testTypeParser() {
        testParseType("I32")
        testParseType("[]")
        testParseType("[I32]")
        testParseType("[x :: I32, y :: I32]")
        testParseType("[x :: I32, y :: I32, z :: I32]")
        testParseType("[left :: f32 | right :: I32]")
        testParseType("[left :: f32 | right :: I32 | center :: []]")
        testParseType("[I32, I32, I32]")
        testParseType("[I32, I32, f32]")
        testParseType("[[I32, I32], f32]")
        testParseType("[[I32, I32], [], f32]")
        testParseType("[f32..]")
        testParseType("[f32^6]")
        //testParseType("[Option [I32]]")
        //testParseType("Option [I32]")
        testParseType("[I32^1]")
    }

    @Test
    fun testParseAggregates() {
        testParseSeq("""
            let x = ();
        """.trimIndent())

        testParseSeq("""
            let x = (1);
        """.trimIndent())

        testParseSeq("""
            let x = (1, 2);
        """.trimIndent())

        testParseSeq("""
            let x = (one = 1);
        """.trimIndent())

        testParseSeq("""
            let x = (one = 1, two = 2);
        """.trimIndent())

        testParseSeq("""
            let x: [I32^2] = (one = 1, two = 2);
        """.trimIndent())

        expectFailure {
            testParseSeq("""
            let x: [I32^2] = (one = 1, one = 2);
        """.trimIndent())
        }
    }
    @Test
    fun testCast() {
        testParseSeq("""
            let x = (1, 2, 3, 4, 5) as [I32^5];
        """.trimIndent())

        testParseSeq("""
            let x: [I32^5] = (1, 2, 3, 4, 5);
        """.trimIndent())
    }

    @Test
    fun testPattern() {
        testModule("""
            def f1 :: fn x => x;
            def f2 :: fn (x) => x;
            def f3 :: fn (x, y) => x;
        """.trimIndent())

        expectFailure {
            testModule("""
                def f1 :: fn (x, 0) => x;
            """.trimIndent())
        }
    }
}