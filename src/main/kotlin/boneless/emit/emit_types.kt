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

fun getTupleInitializationMethodDescriptor(tupleType: Type.TupleType) = MethodDescriptor(tupleType.elements.mapNotNull { getFieldDescriptor(it) }, ReturnDescriptor.NonVoidDescriptor(getFieldDescriptor(tupleType)!!))

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

fun ClassFileBuilder.getVerificationType(type: Type): VerificationType? = when(type) {
    is Type.PrimitiveType -> when(type.primitiveType) {
        PrimitiveTypeEnum.Bool -> VerificationType.Integer
        PrimitiveTypeEnum.I32 -> VerificationType.Integer
        PrimitiveTypeEnum.I64 -> VerificationType.Long
        PrimitiveTypeEnum.F32 -> VerificationType.Float
    }
    is Type.RecordType -> TODO()
    is Type.TupleType -> if (type == unit_type()) null else VerificationType.Object(constantClass(mangled_datatype_name(type)).toInt())
    is Type.ArrayType -> TODO()
    is Type.EnumType -> TODO()
    is Type.NominalType -> TODO()
    is Type.FnType -> TODO()
    is Type.TypeParam -> TODO()
    Type.Top -> TODO()
}

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

                val initDescriptor = getTupleInitializationMethodDescriptor(type)

                var j = 0
                val params = mutableListOf<VerificationType>()
                val params_vars = type.elements.mapIndexed { i, t ->
                    val fd = getFieldDescriptor(t)
                    if (fd != null) {
                        params.add(builder.getVerificationType(fd))
                        j++
                    } else null
                }

                val functionEmitter = FunctionEmitter(this, builder, params)
                val bb = functionEmitter.bb

                val mangled_dt = mangled_datatype_name(type)
                bb.pushDefaultValueType(mangled_dt)
                val aggregateVt = builder.getVerificationType(type)!!
                for ((i, elementType) in type.elements.withIndex()) {
                    if (elementType == unit_type())
                        continue
                    bb.loadVariable(params_vars[i]!!)
                    val fieldName = "_$i"
                    val fieldDescriptor = getFieldDescriptor(elementType)!!
                    bb.mutateSetFieldName(mangled_dt, fieldName, fieldDescriptor, aggregateVt)
                }
                val returnVt = builder.getVerificationType(type)
                if (returnVt != null)
                    bb.return_value(returnVt)
                else
                    bb.return_void()

                val attributes = functionEmitter.finish(null)
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
        is Type.TypeParam -> TODO()
        Type.Top -> TODO()
        else -> throw Exception("Unhandled type: $type")
    }
}