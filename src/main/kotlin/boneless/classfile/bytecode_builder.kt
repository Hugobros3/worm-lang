package boneless.classfile

import boneless.classfile.JVMComputationalType.*
import java.io.ByteArrayOutputStream

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
enum class JVMActualType(val comp: JVMComputationalType, val cat: Int) {
    // These types are FAKE !
    AT_Boolean(CT_Int, 1),
    AT_Byte(CT_Int, 1),
    AT_Char(CT_Int, 1),
    AT_Short(CT_Int, 1),

    AT_Float(CT_Float, 1),
    AT_Long(CT_Long, 2),
    AT_Double(CT_Double, 2),
    AT_Reference(CT_Reference, 1),
    AT_ReturnAddress(CT_ReturnAddress, 1)
}

class BytecodeBuilder() {
    var max_stack = 0
    var max_locals = 0
    val baos = ByteArrayOutputStream()

    val locals = mutableListOf<JVMComputationalType>()
    val stack = mutableListOf<JVMComputationalType>()

    fun popStack(): JVMComputationalType {
        return stack.removeAt(stack.size - 1)
    }

    fun instruction(i: JVMInstruction) {
        baos.write(i.opcode)
    }

    fun return_void() {
        instruction(JVMInstruction.`return`)
    }

    fun return_value(type: JVMActualType) {

    }

    fun finish(): Attribute.Code {
        return Attribute.Code(max_stack.toShort(), max_locals.toShort(), baos.toByteArray(), emptyList(), emptyList())
    }
}