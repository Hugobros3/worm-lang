package boneless

import boneless.parse.Tokenizer
import org.junit.Test

class TestTokenizer {
    @Test
    fun testTokenizer() {
        fun test(str: String) {
            val t = Tokenizer(str).tokenize()
            println(t)
            println(t.map { it.tokenName }.joinToString(" "))
        }

        test("let x = 56;")

        test("""
            def PersonIdentity :: struct {
                var fullName: String;
                var dob: Date;
            };
        """.trimIndent())

        test("""
            def Pair[T1, T2] :: struct {
                var first: &T1;
                var second: &T2;
            };
        """.trimIndent())
    }
}