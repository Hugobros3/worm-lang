package boneless.core

import boneless.parse.Parser
import boneless.parse.Tokenizer
import boneless.type.Type

enum class BuiltinFn(type_str: String) {
    Add("fn [I32, I32] -> I32"),
    Multiply("fn [I32, I32] -> I32"),
    Subtract("fn [I32, I32] -> I32"),
    Negate("fn I32 -> I32"),
    ;

    val type: Type
    init {
        val p = Parser(
            type_str,
            Tokenizer(type_str).tokenize()
        )
        type = p.parseType()
    }
}