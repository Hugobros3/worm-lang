package boneless.emit

import boneless.classfile.*
import boneless.core.BuiltinFn
import boneless.type.Type

fun Emitter.emit_builtin_fn_classfile(): ClassFile {
    val cfBuilder = ClassFileBuilder(lw2jvm, "BuiltinFns", defaultClassAccessFlags)

    fun emit_builtin_fn(builtin: BuiltinFn) {
        val fnEmitter = FunctionEmitter(this, cfBuilder, listOf(cfBuilder.getVerificationType(builtin.type.dom)!!))
        val argsType = builtin.type.dom

        fun access_arg() {
            fnEmitter.bb.loadVariable(0)
        }

        fun access_arg_extract(n: Int) {
            access_arg()
            val arg_type: Type = (argsType as Type.TupleType).elements[n]
            fnEmitter.bb.getField(mangled_datatype_name(argsType), "_$n", arg_type)
        }

        fun branchWrapper(branchType: BranchType) {
            val ifTrueBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
            val ifFalseBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
            access_arg_extract(0)
            access_arg_extract(1)

            ifTrueBB.pushInt(1)
            ifTrueBB.return_value(builtin.type.codom)
            ifFalseBB.pushInt(0)
            ifFalseBB.return_value(builtin.type.codom)
            fnEmitter.bb.branch(branchType, ifTrueBB, ifFalseBB)
        }

        when(builtin) {
            BuiltinFn.jvm_add_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.add_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_sub_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.sub_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_mul_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.mul_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_div_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.div_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_mod_i32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.mod_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_neg_i32 -> {
                access_arg()
                fnEmitter.bb.neg_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_add_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.add_f32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_sub_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.sub_f32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_mul_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.mul_f32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_div_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.div_f32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_mod_f32 -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.mod_f32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_neg_f32 -> {
                access_arg()
                fnEmitter.bb.neg_f32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_and_bool -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.and_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_or_bool  -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.or_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_xor_bool -> {
                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.xor_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_not_bool -> {
                // JVM doesn't have "not"
                access_arg()
                fnEmitter.bb.pushInt(1)
                fnEmitter.bb.xor_i32()
                fnEmitter.bb.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_infeq_i32 -> { branchWrapper(BranchType.ICMP_LESS_EQUAL) }
            BuiltinFn.jvm_inf_i32 -> { branchWrapper(BranchType.ICMP_LESS) }
            BuiltinFn.jvm_eq_i32 -> { branchWrapper(BranchType.ICMP_EQ) }
            BuiltinFn.jvm_neq_i32 -> { branchWrapper(BranchType.ICMP_NEQ) }
            BuiltinFn.jvm_grt_i32 -> { branchWrapper(BranchType.ICMP_GREATER) }
            BuiltinFn.jvm_grteq_i32 -> { branchWrapper(BranchType.ICMP_GREATER_EQUAL) }
            BuiltinFn.jvm_infeq_f32 -> {
                val ifTrueBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
                val ifFalseBB = fnEmitter.builder.basicBlock(fnEmitter.bb)

                access_arg_extract(1)
                access_arg_extract(0)
                fnEmitter.bb.fcmpl()
                fnEmitter.bb.branch(BranchType.IF_GREATER_EQUAL, ifTrueBB, ifFalseBB)

                ifTrueBB.pushInt(1)
                ifTrueBB.return_value(builtin.type.codom)
                ifFalseBB.pushInt(0)
                ifFalseBB.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_inf_f32 -> {
                val ifTrueBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
                val ifFalseBB = fnEmitter.builder.basicBlock(fnEmitter.bb)

                access_arg_extract(1)
                access_arg_extract(0)
                fnEmitter.bb.fcmpl()
                fnEmitter.bb.branch(BranchType.IF_GREATER, ifTrueBB, ifFalseBB)

                ifTrueBB.pushInt(1)
                ifTrueBB.return_value(builtin.type.codom)
                ifFalseBB.pushInt(0)
                ifFalseBB.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_eq_f32 -> {
                val ifTrueBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
                val ifFalseBB = fnEmitter.builder.basicBlock(fnEmitter.bb)

                access_arg_extract(1)
                access_arg_extract(0)
                fnEmitter.bb.fcmpl()
                fnEmitter.bb.branch(BranchType.IF_EQ, ifTrueBB, ifFalseBB)

                ifTrueBB.pushInt(1)
                ifTrueBB.return_value(builtin.type.codom)
                ifFalseBB.pushInt(0)
                ifFalseBB.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_neq_f32 -> {
                val ifTrueBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
                val ifFalseBB = fnEmitter.builder.basicBlock(fnEmitter.bb)

                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.fcmpl()
                fnEmitter.bb.branch(BranchType.IF_EQ, ifTrueBB, ifFalseBB)

                ifTrueBB.pushInt(0)
                ifTrueBB.return_value(builtin.type.codom)
                ifFalseBB.pushInt(1)
                ifFalseBB.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_grt_f32 -> {
                val ifTrueBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
                val ifFalseBB = fnEmitter.builder.basicBlock(fnEmitter.bb)

                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.fcmpl()
                fnEmitter.bb.branch(BranchType.IF_GREATER, ifTrueBB, ifFalseBB)

                ifTrueBB.pushInt(1)
                ifTrueBB.return_value(builtin.type.codom)
                ifFalseBB.pushInt(0)
                ifFalseBB.return_value(builtin.type.codom)
            }
            BuiltinFn.jvm_grteq_f32 -> {
                val ifTrueBB = fnEmitter.builder.basicBlock(fnEmitter.bb)
                val ifFalseBB = fnEmitter.builder.basicBlock(fnEmitter.bb)

                access_arg_extract(0)
                access_arg_extract(1)
                fnEmitter.bb.fcmpl()
                fnEmitter.bb.branch(BranchType.IF_GREATER_EQUAL, ifTrueBB, ifFalseBB)

                ifTrueBB.pushInt(1)
                ifTrueBB.return_value(builtin.type.codom)
                ifFalseBB.pushInt(0)
                ifFalseBB.return_value(builtin.type.codom)

            }
            else -> throw Exception("Missing codegen for intrinsic ${builtin}")
        }

        val attributes = fnEmitter.finish(null)
        val descriptor = getMethodDescriptor(builtin.type)
        cfBuilder.method(builtin.name, descriptor, defaulMethodAccessFlags.copy(acc_final = true, acc_static = true), attributes)
    }

    for (builtin in BuiltinFn.values())
        emit_builtin_fn(builtin)

    return cfBuilder.finish()
}