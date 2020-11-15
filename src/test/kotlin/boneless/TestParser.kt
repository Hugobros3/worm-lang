package boneless

import org.junit.Assert.assertTrue
import org.junit.Test

class TestParser {

    @Test
    fun testParse() {
        fun test(str: String) {
            val p = Parser(str, Tokenizer(str).tokenize())
            val pp = p.parseProgram()
            println(pp)
            println(pp.print(false))

            val printed = pp.print(false)
            val againParser = Parser(printed, Tokenizer(printed).tokenize())
            val reparsed = againParser.parseProgram()
            println(reparsed.yieldValue)
            println(reparsed.yieldValue.print(false))
            assertTrue(pp == reparsed.yieldValue)
        }

        test("let x = 56;")

        test("""
            let x = 56;
            let y = (x, 9);
        """.trimIndent())

        test("""
            def PersonIdentity :: struct {
                var fullName: String;
                var dob: Date;
            };
        """.trimIndent())

        test("""
            def x: String :: "a";
            def xx :: "a"; // infers string 
        """.trimIndent())

        test("""
            /* block comment */ let a = 666;
            
            /*
             * more complicated 
             * /* block comment */ 
             * to try and trick the parser
            */
        """.trimIndent())

        test("""
            def pair :: a => (a, a);
            def factorial :: (number: I32) => if number <= 1 then 1 else factorial (number - 1);
        """.trimIndent())

        test("""
            let x = 5 * 4 + 3;
            let y = 5 + 4 * 3;
            
            let z = -5;
            let w = -z;
            let v = z - w;
            
            let t = ---------42;
            
            let a = - 4 * 5 : A;
            let b = 5 * -4 : A;
        """.trimIndent())

        test("""
            let s = map ((x, y, z) => x + y + z) arr;
        """.trimIndent())
    }
}