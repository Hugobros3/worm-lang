package boneless.classfile

import boneless.classfile.JVMComputationalType.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.Integer.max

class BytecodeBuilder(private val classFileBuilder: ClassFileBuilder) {
    private var max_stack = 0
    private var max_locals = 0
    private val baos___ = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos___)

    private var locals = mutableListOf<JVMComputationalType>()
    private val stack = mutableListOf<JVMComputationalType>()

    private fun pushStack(t: JVMComputationalType) {
        stack.add(t)
        max_stack = max(max_stack, stack.size)
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

    fun reserveVariable(t: JVMComputationalType): Int {
        locals.add(t)
        max_locals = max(max_locals, locals.size)
        return locals.size - 1
    }

    fun loadVariable(i: Int) {
        if (i >= locals.size)
            throw Exception("Variable $i is not reserved")
        val t = locals[i]
        when (t) {
            CT_Int -> {
                when(i) {
                    0 -> instruction(JVMInstruction.iload_0)
                    1 -> instruction(JVMInstruction.iload_1)
                    2 -> instruction(JVMInstruction.iload_2)
                    3 -> instruction(JVMInstruction.iload_3)
                    else -> {
                        instruction(JVMInstruction.iload)
                        immediate_byte(i.toByte())
                    }
                }
            }
            CT_Float -> TODO()
            CT_Long -> TODO()
            CT_Double -> TODO()
            CT_Reference -> {
                when(i) {
                    0 -> instruction(JVMInstruction.aload_0)
                    1 -> instruction(JVMInstruction.aload_1)
                    2 -> instruction(JVMInstruction.aload_2)
                    3 -> instruction(JVMInstruction.aload_3)
                    else -> {
                        instruction(JVMInstruction.aload)
                        immediate_byte(i.toByte())
                    }
                }
            }
            CT_ReturnAddress -> TODO()
        }
        pushStack(t)
    }

    fun setVariable(i: Int) {
        if (i >= locals.size)
            throw Exception("Variable $i is not reserved")
        val t = locals[i]
        when (t) {
            CT_Int -> {
                when(i) {
                    0 -> instruction(JVMInstruction.istore_0)
                    1 -> instruction(JVMInstruction.istore_1)
                    2 -> instruction(JVMInstruction.istore_2)
                    3 -> instruction(JVMInstruction.istore_3)
                    else -> {
                        instruction(JVMInstruction.istore)
                        immediate_byte(i.toByte())
                    }
                }
            }
            CT_Float -> TODO()
            CT_Long -> TODO()
            CT_Double -> TODO()
            CT_Reference -> {
                when(i) {
                    0 -> instruction(JVMInstruction.astore_0)
                    1 -> instruction(JVMInstruction.astore_1)
                    2 -> instruction(JVMInstruction.astore_2)
                    3 -> instruction(JVMInstruction.astore_3)
                    else -> {
                        instruction(JVMInstruction.astore)
                        immediate_byte(i.toByte())
                    }
                }
            }
            CT_ReturnAddress -> TODO()
        }
        popStack(t)
    }

    fun enterScope(fn: () -> Unit) {
        val old_locals = locals
        fn()
        locals = old_locals
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

    fun pushDefaultValueType(className: String) {
        instruction(JVMInstruction.defaultvalue)
        immediate_short(classFileBuilder.constantClass(className))
        pushStack(CT_Reference)
    }

    fun mutateSetFieldName(className: String, fieldName: String, fieldDescriptor: FieldDescriptor) {
        popStack(fieldDescriptor.toActualJVMType().asComputationalType)
        popStack(CT_Reference)
        instruction(JVMInstruction.withfield)
        immediate_short(classFileBuilder.constantFieldRef(className, fieldName, fieldDescriptor))
        pushStack(CT_Reference)
    }

    fun return_void() {
        instruction(JVMInstruction.`return`)
    }

    fun return_value(type: JVMActualType) {
        popStack(type.asComputationalType)
        when(type.asComputationalType) {
            CT_Int -> instruction(JVMInstruction.ireturn)
            CT_Float -> TODO()
            CT_Long -> TODO()
            CT_Double -> TODO()
            CT_Reference -> instruction(JVMInstruction.areturn)
            CT_ReturnAddress -> throw Exception("Illegal")
        }
    }

    fun callStatic(className: String, methodName: String, methodDescriptor: MethodDescriptor) {
        for (fd in methodDescriptor.dom.reversed()) {
            popStack(fd.toActualJVMType().asComputationalType)
        }
        instruction(JVMInstruction.invokestatic)
        immediate_short(classFileBuilder.constantMethodRef(className, methodName, methodDescriptor))
        if (methodDescriptor.codom is ReturnDescriptor.NonVoidDescriptor) {
            pushStack(methodDescriptor.codom.fieldType.toActualJVMType().asComputationalType)
        }
    }

    fun finish(): Attribute.Code {
        dos.flush()
        return Attribute.Code(max_stack.toShort(), max_locals.toShort(), baos___.toByteArray(), emptyList(), emptyList())
    }
}