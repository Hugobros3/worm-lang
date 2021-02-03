package boneless.classfile

import boneless.classfile.JVMActualType.*
import boneless.classfile.JVMComputationalType.*

sealed class FieldDescriptor {
    sealed class BaseType : FieldDescriptor() {
        object B : BaseType()
        object C : BaseType()
        object D : BaseType()
        object F : BaseType()
        object I : BaseType()
        object J : BaseType()
        object S : BaseType()
        object Z : BaseType()

        override fun toString(): String = javaClass.simpleName
    }

    sealed class ReferenceType : FieldDescriptor() {
        data class NullableClassType(val className: String) : ReferenceType() {
            override fun toString() = "L$className;"
        }
        data class NullFreeClassType(val className: String) : ReferenceType(){
            override fun toString() = "Q$className;"
        }
        data class ArrayType(val componentType: FieldDescriptor) : ReferenceType(){
            override fun toString() = "[$componentType"
        }
    }

    fun toActualJVMType(): JVMActualType = when(this) {
        BaseType.B -> AT_Byte
        BaseType.C -> AT_Char
        BaseType.D -> AT_Double
        BaseType.F -> AT_Float
        BaseType.I -> AT_Int
        BaseType.J -> AT_Long
        BaseType.S -> AT_Short
        BaseType.Z -> AT_Boolean
        is ReferenceType.NullableClassType -> AT_Reference
        is ReferenceType.NullFreeClassType -> AT_Reference
        is ReferenceType.ArrayType -> AT_Reference
    }
}

data class MethodDescriptor(val dom: List<FieldDescriptor>, val codom: ReturnDescriptor){
    override fun toString() = "(${dom.joinToString("")})$codom"
}
sealed class ReturnDescriptor {
    object V: ReturnDescriptor() {
        override fun toString(): String = "V"
    }
    data class NonVoidDescriptor(val fieldType: FieldDescriptor): ReturnDescriptor() {
        override fun toString() = fieldType.toString()
    }
}

// https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-2.html#jvms-2.11.1-320
enum class JVMComputationalType {
    CT_Int,
    CT_Float,
    CT_Long,
    CT_Double,
    CT_Reference,
    CT_ReturnAddress
}

// https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-2.html#jvms-2.11.1-320
enum class JVMActualType(val asComputationalType: JVMComputationalType, val cat: Int) {
    // These types are FAKE !
    AT_Boolean(CT_Int, 1),
    AT_Byte(CT_Int, 1),
    AT_Char(CT_Int, 1),
    AT_Short(CT_Int, 1),
    AT_Int(CT_Int, 1),

    AT_Float(CT_Float, 1),
    AT_Long(CT_Long, 2),
    AT_Double(CT_Double, 2),
    AT_Reference(CT_Reference, 1),
    AT_ReturnAddress(CT_ReturnAddress, 1)
}

sealed class VerificationType {
    object Top: VerificationType()
    object Integer: VerificationType()
    object Float: VerificationType()
    object Null: VerificationType()
    object UninitializedThis: VerificationType()
    data class Object(val cpool_index: Int): VerificationType()
    data class Uninitialized(val offset: Int): VerificationType()
    object Long: VerificationType()
    object Double: VerificationType()

    override fun toString(): String {
        return javaClass.simpleName
    }
}

fun VerificationType.toActualType(): JVMActualType = when(this) {
    VerificationType.Top -> throw Exception("Who knows ?")
    VerificationType.Integer -> AT_Int
    VerificationType.Float -> AT_Float
    VerificationType.Null -> AT_Reference
    VerificationType.UninitializedThis -> TODO() // not sure
    is VerificationType.Object -> AT_Reference
    is VerificationType.Uninitialized -> TODO() // not sure
    VerificationType.Long -> AT_Long
    VerificationType.Double -> AT_Double
}

fun VerificationType.toComputationalType() = toActualType().asComputationalType