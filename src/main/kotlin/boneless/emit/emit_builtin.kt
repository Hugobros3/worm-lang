package boneless.emit

import boneless.classfile.*
import boneless.core.BuiltinFn
import boneless.type.Type

fun Emitter.emit_builtin_fn_classfile(): ClassFile {
    val cfBuilder = ClassFileBuilder(lw2jvm, "BuiltinFns", defaultClassAccessFlags)

    fun emit_builtin_fn(builtin: BuiltinFn) {
        val builder = BytecodeBuilder(cfBuilder)
        val argsType = builtin.type.dom
        builder.reserveVariable(getFieldDescriptor(argsType)!!.toActualJVMType().asComputationalType)

        fun access_arg() {
            builder.loadVariable(0)
        }

        fun access_arg_extract(n: Int) {
            access_arg()
            val arg_type: Type = (argsType as Type.TupleType).elements[n]
            builder.getField(mangled_datatype_name(argsType), "_$n", getFieldDescriptor(arg_type)!!)
        }

        when(builtin) {
            BuiltinFn.jvm_add_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.add_i32()
            }
            BuiltinFn.jvm_sub_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.sub_i32()
            }
            BuiltinFn.jvm_mul_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.mul_i32()
            }
            BuiltinFn.jvm_div_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.div_i32()
            }
            BuiltinFn.jvm_mod_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.mod_i32()
            }
            BuiltinFn.jvm_neg_i32 -> {
                access_arg()
                builder.neg_i32()
            }
            BuiltinFn.jvm_add_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.add_f32()
            }
            BuiltinFn.jvm_sub_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.sub_f32()
            }
            BuiltinFn.jvm_mul_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.mul_f32()
            }
            BuiltinFn.jvm_div_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.div_f32()
            }
            BuiltinFn.jvm_mod_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.mod_f32()
            }
            BuiltinFn.jvm_neg_f32 -> {
                access_arg()
                builder.neg_f32()
            }
            BuiltinFn.jvm_and_bool -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.and_i32()
            }
            BuiltinFn.jvm_or_bool  -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.or_i32()
            }
            BuiltinFn.jvm_xor_bool -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.xor_i32()
            }
            BuiltinFn.jvm_not_bool -> {
                // JVM doesn't have "not"
                access_arg()
                builder.pushInt(1)
                builder.xor_i32()
            }
            BuiltinFn.jvm_infeq_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                builder.branch_infeq_i32({
                    builder.pushInt(1)
                }, {
                    builder.pushInt(0)
                })
            }
            else -> throw Exception("Missing codegen for intrinsic ${builtin}")
        }

        builder.return_value(getFieldDescriptor(builtin.type.codom)!!.toActualJVMType())

        val code = builder.finish()
        val descriptor = getMethodDescriptor(builtin.type)
        cfBuilder.method(builtin.name, descriptor, defaulMethodAccessFlags.copy(acc_final = true, acc_static = true), code)
    }

    for (builtin in BuiltinFn.values())
        emit_builtin_fn(builtin)

    return cfBuilder.finish()
}