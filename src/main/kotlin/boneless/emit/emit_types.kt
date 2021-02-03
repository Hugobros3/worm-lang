package boneless.emit

import boneless.classfile.*
import boneless.type.PrimitiveTypeEnum
import boneless.type.Type
import boneless.type.unit_type

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

fun Emitter.emit_datatype_classfile_if_needed(type: Type) {
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