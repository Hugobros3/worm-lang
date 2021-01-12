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
        val t = p.eatType()

        println("Parsed type as: $t")
        println("Printed form: " + t.prettyPrint())
    }

    private fun expectFailure(f: () -> Unit) {
        try {
            f()
            assertTrue(false)
        } catch (e: Exception) {
            println("Fails as expected: $e")
        }
    }

    @Test
    fun testParse() {

        testParseSeq("let x = 56;")

        testParseSeq("""
                let x = 56;
                let y = (x, 9);
            """.trimIndent())

        testModule("""
                def PersonIdentity :: [
                    fullName :: String,
                    dob :: Date
                ];
            """.trimIndent())

        testModule("""
                def x: String :: "a";
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
                def operations :: fn a, b, c, d => {
                    let x = a ^ b;
                    let y = !x | c;
                    let z = !!(x ^ c ^ d ^ y);
                };
            """.trimIndent())

        testModule("""
                def deref_s_ptr :: fn s: ref [I32 * I32] => @s;
            """.trimIndent())
    }

    @Test
    fun testTypeParser() {
        testParseType("i32")
        testParseType("[]")
        testParseType("[i32]")
        testParseType("[x :: i32, y :: i32]")
        testParseType("[x :: i32, y :: i32, z :: i32]")
        testParseType("[left :: f32 | right :: i32]")
        testParseType("[left :: f32 | right :: i32 | center :: []]")
        testParseType("[i32 * i32 * i32]")
        testParseType("i32 * i32 * i32")
        testParseType("[i32 * i32 * f32]")
        testParseType("[f32..]")
        testParseType("[f32^6]")
        testParseType("[Option [i32]]")
        testParseType("Option [i32]")
        testParseType("[i32^1]")
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
            let x: [i32^2] = (one = 1, two = 2);
        """.trimIndent())

        expectFailure {
            testParseSeq("""
            let x: [i32^2] = (one = 1, one = 2);
        """.trimIndent())
        }
    }
    @Test
    fun testCast() {
        testParseSeq("""
            let x = (1, 2, 3, 4, 5) as [i32^5];
        """.trimIndent())

        testParseSeq("""
            let x: [i32^5] = (1, 2, 3, 4, 5);
        """.trimIndent())
    }
}