package boneless.core

import boneless.type.PrimitiveTypeEnum
import boneless.type.Type

private val I32T = Type.PrimitiveType(PrimitiveTypeEnum.I32)
private val F32T = Type.PrimitiveType(PrimitiveTypeEnum.F32)
private val BoolT = Type.PrimitiveType(PrimitiveTypeEnum.Bool)

enum class BuiltinFn(val type: Type.FnType) {
    jvm_add_i32(Type.FnType(Type.TupleType(listOf(I32T, I32T)), I32T)),
    jvm_sub_i32(Type.FnType(Type.TupleType(listOf(I32T, I32T)), I32T)),
    jvm_mul_i32(Type.FnType(Type.TupleType(listOf(I32T, I32T)), I32T)),
    jvm_div_i32(Type.FnType(Type.TupleType(listOf(I32T, I32T)), I32T)),
    jvm_mod_i32(Type.FnType(Type.TupleType(listOf(I32T, I32T)), I32T)),
    jvm_neg_i32(Type.FnType(I32T, I32T)),

    jvm_add_f32(Type.FnType(Type.TupleType(listOf(F32T, F32T)), F32T)),
    jvm_sub_f32(Type.FnType(Type.TupleType(listOf(F32T, F32T)), F32T)),
    jvm_mul_f32(Type.FnType(Type.TupleType(listOf(F32T, F32T)), F32T)),
    jvm_div_f32(Type.FnType(Type.TupleType(listOf(F32T, F32T)), F32T)),
    jvm_mod_f32(Type.FnType(Type.TupleType(listOf(F32T, F32T)), F32T)),
    jvm_neg_f32(Type.FnType(F32T, F32T)),

    jvm_and_bool(Type.FnType(Type.TupleType(listOf(BoolT, BoolT)), BoolT)),
    jvm_or_bool (Type.FnType(Type.TupleType(listOf(BoolT, BoolT)), BoolT)),
    jvm_xor_bool(Type.FnType(Type.TupleType(listOf(BoolT, BoolT)), BoolT)),
    jvm_not_bool(Type.FnType(BoolT, BoolT)),

    jvm_infeq_i32(Type.FnType(Type.TupleType(listOf(I32T, I32T)), BoolT)),
    ;
}
