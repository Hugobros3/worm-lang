package boneless.emit

import boneless.*
import boneless.bind.TermLocation
import boneless.bind.get_def
import boneless.classfile.*
import boneless.core.prelude_modules
import boneless.type.PrimitiveTypeEnum
import boneless.type.Type
import boneless.type.unit_type
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

    fun getFieldDescriptor(type: Type): FieldDescriptor? {
        return when(type) {
            is Type.TypeParam -> throw Exception("Type params should be monomorphized !")
            is Type.PrimitiveType -> when(type.primitiveType) {
                PrimitiveTypeEnum.Bool -> FieldDescriptor.BaseType.Z
                PrimitiveTypeEnum.I32 -> FieldDescriptor.BaseType.I
                PrimitiveTypeEnum.I64 -> FieldDescriptor.BaseType.J
                PrimitiveTypeEnum.F32 -> FieldDescriptor.BaseType.F
            }
            is Type.RecordType -> TODO()
            is Type.TupleType -> if (type.isUnit) null else FieldDescriptor.ReferenceType.NullFreeClassType(mangled_datatype_name(type))
            is Type.ArrayType -> TODO()
            is Type.EnumType -> TODO()
            is Type.NominalType -> TODO()
            is Type.FnType -> TODO()
            is Type.Top -> TODO("java/lang/object")
        }
    }

    fun getMethodDescriptor(fnType: Type.FnType): MethodDescriptor {
        val dom = getFieldDescriptor(fnType.dom)
        val codom = if (fnType.codom == unit_type()) ReturnDescriptor.V else ReturnDescriptor.NonVoidDescriptor(getFieldDescriptor(fnType.codom)!!)
        return MethodDescriptor(listOf(dom).filterNotNull(), codom)
    }

    fun mangled_datatype_name(type: Type): String = when(type) {
        is Type.TypeParam -> throw Exception("Type params should be monomorphized !")
        is Type.PrimitiveType -> "Primitive${type.primitiveType.name}"
        is Type.RecordType -> "RecordType__" + type.elements.joinToString("") { (f, t) -> "${f}_${mangled_datatype_name(t)}__" }
        is Type.TupleType -> "TupleType__" + type.elements.joinToString("") { t -> "${mangled_datatype_name(t)}__" }
        is Type.ArrayType -> TODO()
        is Type.EnumType -> TODO()
        is Type.NominalType -> "BL_NT_${type.name}"
        is Type.FnType -> throw Exception("Not a data type")
        is Type.Top -> TODO("java/lang/object")
    }

    fun mangled_contract_instance_name(contractName: String, argumentsExpr: List<Type>): String = "BL_INST_$contractName$"+argumentsExpr.joinToString("_") { mangled_datatype_name(it) }

    fun tuple_type_init_descriptor(tupleType: Type.TupleType) = MethodDescriptor(tupleType.elements.mapNotNull { getFieldDescriptor(it) }, ReturnDescriptor.NonVoidDescriptor(getFieldDescriptor(tupleType)!!))

    fun emit_datatype_classfile_if_needed(type: Type) {
        when (type) {
            is Type.PrimitiveType -> {}
            is Type.RecordType -> TODO()
            is Type.TupleType -> {
                // Unit type does not get a class
                if (type == unit_type())
                    return

                type_classes.getOrPut(type) {
                    val builder = ClassFileBuilder(className = mangled_datatype_name(type), accessFlags = defaultClassAccessFlags.copy(acc_value_type = true))
                    for ((i, element) in type.elements.withIndex()) {
                        if (element == unit_type())
                            continue
                        builder.field("_$i", getFieldDescriptor(element)!!, defaultFieldAccessFlags.copy(acc_final = true))
                    }

                    val initDescriptor = tuple_type_init_descriptor(type)
                    val initCodeBuilder = BytecodeBuilder(builder)
                    val params = type.elements.mapIndexed { i, t ->
                        val fd = getFieldDescriptor(t)
                        if (fd != null) {
                            initCodeBuilder.reserveVariable(fd.toActualJVMType().asComputationalType)
                        } else null
                    }

                    val mangled_dt = mangled_datatype_name(type)
                    initCodeBuilder.pushDefaultValueType(mangled_dt)
                    for ((i, element) in type.elements.withIndex()) {
                        if (element == unit_type())
                            continue
                        initCodeBuilder.loadVariable(params[i]!!)
                        val fieldName = "_$i"
                        initCodeBuilder.mutateSetFieldName(mangled_dt, fieldName, getFieldDescriptor(element)!!)
                    }
                    initCodeBuilder.return_value(getFieldDescriptor(type)!!.toActualJVMType())

                    val attributes = initCodeBuilder.finish()
                    builder.method("<init>", initDescriptor, defaulMethodAccessFlags.copy(acc_static = true, acc_public = true), attributes)
                    builder.finish()
                }
                for (element in type.elements)
                    emit_datatype_classfile_if_needed(element)
            }
            is Type.ArrayType -> TODO()
            is Type.EnumType -> TODO()
            is Type.NominalType -> TODO()
            is Type.FnType -> {
                emit_datatype_classfile_if_needed(type.dom)
                emit_datatype_classfile_if_needed(type.codom)
            }
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
                        val attributes = FunctionEmitter(this, d, builder).emit()
                        builder.method(f, descriptor, defaulMethodAccessFlags.copy(acc_final = true, acc_static = true), attributes)
                    }
                    is Expression.IdentifierRef -> when(val r = d.id.resolved) {
                        is TermLocation.DefRef -> TODO()
                        is TermLocation.BinderRef -> TODO()
                        is TermLocation.BuiltinFnRef -> {
                            val wrapper = fn_wrapper(d)
                            println(wrapper.prettyPrint())
                            val attributes = FunctionEmitter(this, wrapper, builder).emit()
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
                    val attributes = FunctionEmitter(this, def.body.fn, builder).emit()
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

    internal fun emit(builder: BytecodeBuilder, literal: Literal) {
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