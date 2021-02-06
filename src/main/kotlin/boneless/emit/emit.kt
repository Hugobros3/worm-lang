package boneless.emit

import boneless.*
import boneless.bind.TermLocation
import boneless.bind.get_def
import boneless.classfile.*
import boneless.core.prelude_modules
import boneless.type.PrimitiveTypeEnum
import boneless.type.Type
import boneless.util.prettyPrint
import java.io.File

class Emitter(val modules: List<Module>, val outputDir: File) {
    val mod_classes = mutableMapOf<Module, ClassFile>()
    val type_classes = mutableMapOf<Type, ClassFile>()
    val instance_classes = mutableMapOf<Def, ClassFile>()

    fun emit() {
        for (module in modules)
            mod_classes[module] = emit_module(module)

        val builtin_cf = emit_builtin_fn_classfile()
        val builtin_outputFile = File("${outputDir.absoluteFile}/BuiltinFns.class")
        writeClassFile(builtin_cf, builtin_outputFile)

        for (cf in mod_classes.values) {
            val outputFile = File("${outputDir.absoluteFile}/${cf.name}.class")
            writeClassFile(cf, outputFile)
        }
        for (cf in type_classes.values) {
            val outputFile = File("${outputDir.absoluteFile}/${cf.name}.class")
            writeClassFile(cf, outputFile)
        }
        for (cf in instance_classes.values) {
            val outputFile = File("${outputDir.absoluteFile}/${cf.name}.class")
            writeClassFile(cf, outputFile)
        }
    }

    fun emit_instance_classfile(def: Def) {
        assert(def.body is Def.DefBody.Instance)
        val contract = get_def((def.body as Def.DefBody.Instance).contractId.resolved)!!
        instance_classes.getOrPut(def) {
            val builder = ClassFileBuilder(className = mangled_contract_instance_name(contract.identifier, def.body.arguments), accessFlags = defaultClassAccessFlags)
            // TODO hacky garbage: this assumes the body is a recordexpr
            // TODO do this the proper way with function exprs
            val body = def.body.body as Expression.RecordExpression
            for ((f, d) in body.fields) {
                // TODO more hacky garbage: this assumes all fields are Fns
                val fnt = d.type as Type.FnType
                val descriptor = getMethodDescriptor(fnt)
                when (d) {
                    is Expression.Function -> {
                        val attributes = FunctionEmitter(this, builder, d).emit(d)
                        builder.method(f, descriptor, defaulMethodAccessFlags.copy(acc_final = true, acc_static = true), attributes)
                    }
                    is Expression.IdentifierRef -> when(val r = d.id.resolved) {
                        is TermLocation.DefRef -> TODO()
                        is TermLocation.BinderRef -> TODO()
                        is TermLocation.BuiltinFnRef -> {
                            val wrapper = fn_wrapper(d)
                            println(wrapper.prettyPrint())
                            val attributes = FunctionEmitter(this, builder, wrapper).emit(wrapper)
                            builder.method(f, descriptor, defaulMethodAccessFlags.copy(acc_final = true, acc_static = true), attributes)
                        }
                        is TermLocation.TypeParamRef -> TODO()
                    }
                    else -> TODO()
                }

            }
            builder.finish()
        }
    }

    fun emit_module(module: Module): ClassFile {
        val builder = ClassFileBuilder(className = module.name, accessFlags = defaultClassAccessFlags)

        for (def in module.defs) {
            when(def.body) {
                is Def.DefBody.ExprBody -> TODO()
                is Def.DefBody.DataCtor -> TODO()
                is Def.DefBody.FnBody -> {
                    val descriptor = getMethodDescriptor(def.type as Type.FnType)
                    val attributes = FunctionEmitter(this, builder, def.body.fn).emit(def.body.fn)
                    builder.method(def.identifier, descriptor, defaulMethodAccessFlags.copy(acc_final = true, acc_static = true), attributes)
                }
                is Def.DefBody.TypeAlias -> {
                    emit_datatype_classfile_if_needed(def.type!!)
                }
                is Def.DefBody.Contract -> { /** no code */ }
                is Def.DefBody.Instance -> emit_instance_classfile(def)
            }
        }

        return builder.finish()
    }

    internal fun emit_literal(builder: BasicBlock, literal: Literal) {
        when (literal) {
            is Literal.NumLiteral -> {
                when((literal.type as Type.PrimitiveType).primitiveType) {
                    PrimitiveTypeEnum.Bool -> TODO()
                    PrimitiveTypeEnum.I32 -> {
                        builder.pushInt(literal.number.toInt())
                    }
                    PrimitiveTypeEnum.I64 -> TODO()
                    PrimitiveTypeEnum.F32 -> TODO()
                }
            }
            is Literal.StrLiteral -> TODO()
            is Literal.ListLiteral -> if(literal.isUnit) {
                // put nothing on the stack
            } else {
                TODO()
            }
            is Literal.RecordLiteral -> TODO()
        }
    }
}

fun emit(module: Module, outputDir: File) = Emitter(listOf(module) + prelude_modules, outputDir).emit()