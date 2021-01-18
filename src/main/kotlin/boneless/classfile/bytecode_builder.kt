package boneless.classfile

import boneless.classfile.JVMComputationalType.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class BytecodeBuilder(private val classFileBuilder: ClassFileBuilder) {
    private var max_stack = 0
    private var max_locals = 0
    private val baos___ = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos___)

    private val locals = mutableListOf<JVMComputationalType>()
    private val stack = mutableListOf<JVMComputationalType>()

    private fun pushStack(t: JVMComputationalType) {
        stack.add(t)
        max_stack = stack.size
    }
    private fun popStack(expected: JVMComputationalType): JVMComputationalType {
        val t = stack.removeAt(stack.size - 1)
        assert(t == expected)
        return t
    }

    private fun instruction(i: JVMInstruction) {
        dos.writeByte(i.opcode)
    }

    private fun immediate_byte(byte: Byte) {
        dos.writeByte(byte.toInt())
    }
    private fun immediate_short(short: Short) {
        dos.writeShort(short.toInt())
    }

    fun pushInt(num: Int) {
        when  {
            num < 256 -> {
                instruction(JVMInstruction.bipush)
                immediate_byte(num.toByte())
            }
            num < 65536 -> {
                instruction(JVMInstruction.sipush)
                immediate_short(num.toShort())
            }
            else -> TODO()
        }
        pushStack(CT_Int)
    }

    fun return_void() {
        instruction(JVMInstruction.`return`)
    }

    fun return_value(type: JVMActualType) {
        popStack(CT_Int)
        when(type.comp) {
            CT_Int -> instruction(JVMInstruction.ireturn)
            CT_Float -> TODO()
            CT_Long -> TODO()
            CT_Double -> TODO()
            CT_Reference -> TODO()
            CT_ReturnAddress -> TODO()
        }
    }

    fun finish(): Attribute.Code {
        dos.flush()
        return Attribute.Code(max_stack.toShort(), max_locals.toShort(), baos___.toByteArray(), emptyList(), emptyList())
    }
}