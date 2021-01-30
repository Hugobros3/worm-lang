package boneless.core

import boneless.TypeExpr
import boneless.parse.Parser
import boneless.parse.Tokenizer

enum class BuiltinFn(type_str: String) {
    Add("fn [I32, I32] -> I32"),
    Multiply("fn [I32, I32] -> I32"),
    Subtract("fn [I32, I32] -> I32"),
    Negate("fn I32 -> I32"),
    ;

    val typeExpr: TypeExpr
    init {
        val p = Parser(
            type_str,
            Tokenizer(type_str).tokenize()
        )
        typeExpr = p.parseType()
    }
}