package boneless

import org.junit.Assert.assertTrue
import org.junit.Test

class TestParser {

    private fun testParse(str: String) {
        val p = Parser(str, Tokenizer(str).tokenize())
        val pp = p.parseProgram()
        //println(pp)
        println(pp.prettyPrint())

        val printed = pp.prettyPrint()
        val againParser = Parser(printed, Tokenizer(printed).tokenize())
        val reparsed = againParser.parseProgram()
        //println(reparsed.yieldValue)
        //println(reparsed.yieldValue!!.prettyPrint())
        assertTrue(pp == reparsed.yieldValue)
    }

    private fun testParseType(str: String) {
        val p = Parser(str, Tokenizer(str).tokenize())
        val t = p.eatType()

        println(t)
        println(t.prettyPrint())
    }

    @Test
    fun testParse() {

        testParse("let x = 56;")

        testParse("""
                let x = 56;
                let y = (x, 9);
            """.trimIndent())

        testParse("""
                def PersonIdentity :: [
                    fullName :: String,
                    dob :: Date
                ];
            """.trimIndent())

        testParse("""
                def x: String :: "a";
                def xx :: "a"; // TS will infer String 
            """.trimIndent())

        testParse("""
                /* block comment */ let a = 666;
                
                /*
                 * more complicated 
                 * /* block comment */ 
                 * to try and trick the parser
                */
            """.trimIndent())

        testParse("""
                def pair :: fn a => (a, a);
                def factorial :: fn (number: I32) => if number <= 1 then 1 else factorial (number - 1);
            """.trimIndent())

        testParse("""
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

        testParse("""
                let s = map (fn (x, y, z) => x + y + z) arr;
            """.trimIndent())

        testParse("""
                let x = sum 2 (2 * reverse);
                let y = 3 * 3 francis;
            """.trimIndent())

        testParse("""
                def operations :: fn a, b, c, d => {
                    let x = a ^ b;
                    let y = !x | c;
                    let z = !!(x ^ c ^ d ^ y);
                };
            """.trimIndent())

        testParse("""
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
        testParse("""
            let x = ();
        """.trimIndent())

        testParse("""
            let x = (1);
        """.trimIndent())

        testParse("""
            let x = (1, 2);
        """.trimIndent())

        testParse("""
            let x = (one = 1);
        """.trimIndent())

        testParse("""
            let x = (one = 1, two = 2);
        """.trimIndent())

        testParse("""
            let x: [i32^2] = (one = 1, two = 2);
        """.trimIndent())
    }
}