package boneless.classfile

import boneless.classfile.JVMComputationalType.*
import jdk.jfr.internal.JVM

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
        BaseType.B -> JVMActualType.AT_Byte
        BaseType.C -> JVMActualType.AT_Char
        BaseType.D -> JVMActualType.AT_Double
        BaseType.F -> JVMActualType.AT_Float
        BaseType.I -> JVMActualType.AT_Int
        BaseType.J -> JVMActualType.AT_Long
        BaseType.S -> JVMActualType.AT_Short
        BaseType.Z -> JVMActualType.AT_Boolean
        is ReferenceType.NullableClassType -> JVMActualType.AT_Reference
        is ReferenceType.NullFreeClassType -> JVMActualType.AT_Reference
        is ReferenceType.ArrayType -> JVMActualType.AT_Reference
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