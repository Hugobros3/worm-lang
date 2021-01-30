package boneless.core

import boneless.TypeExpr
import boneless.parse.Parser
import boneless.parse.Tokenizer

enum class BuiltinFn(type_str: String) {
    jvm_add_i32("fn [I32, I32] -> I32"),
    jvm_sub_i32("fn [I32, I32] -> I32"),
    jvm_mul_i32("fn [I32, I32] -> I32"),
    jvm_div_i32("fn [I32, I32] -> I32"),
    jvm_mod_i32("fn [I32, I32] -> I32"),
    jvm_neg_i32("fn I32 -> I32"),
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
