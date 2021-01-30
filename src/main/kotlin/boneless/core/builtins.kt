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

    jvm_add_f32("fn [F32, F32] -> F32"),
    jvm_sub_f32("fn [F32, F32] -> F32"),
    jvm_mul_f32("fn [F32, F32] -> F32"),
    jvm_div_f32("fn [F32, F32] -> F32"),
    jvm_mod_f32("fn [F32, F32] -> F32"),
    jvm_neg_f32("fn F32 -> F32"),
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
