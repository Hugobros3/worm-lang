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

    private val patches = mutableListOf<Patch>()
    inner class Patch(val pos: Int, var data: Short)

    private val stackMapFrames = mutableListOf<Attribute.StackMapTable.StackMapFrame>()

    private fun pushStack(t: JVMComputationalType) {
        stack.add(t)
        max_stack = max(max_stack, stack.size)
    }
    private fun popStack(expected: JVMComputationalType): JVMComputationalType {
        val t = stack.removeAt(stack.size - 1)
        assert(t == expected) { "Popped $t, expected $expected" }
        return t
    }

    private fun position(): Int = baos___.size()

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
            CT_Float -> {
                when(i) {
                    0 -> instruction(JVMInstruction.fload_0)
                    1 -> instruction(JVMInstruction.fload_1)
                    2 -> instruction(JVMInstruction.fload_2)
                    3 -> instruction(JVMInstruction.fload_3)
                    else -> {
                        instruction(JVMInstruction.fload)
                        immediate_byte(i.toByte())
                    }
                }
            }
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

    fun patchable_short(): Patch {
        val now = position()
        immediate_short(0) // patched out afterwards
        val patch = Patch(now, 0)
        patches += patch
        return patch
    }

    fun branch_infeq_i32(yieldType: JVMComputationalType?, ifTrue: () -> Unit, ifFalse: () -> Unit) {
        popStack(CT_Int)
        popStack(CT_Int)

        val before_if = position()
        instruction(JVMInstruction.if_icmple)
        val ifTrueJumpLocation = patchable_short()
        ifFalse()

        // Goto statement after the "false" segment to join with the "true" section
        val before_goto = position()
        instruction(JVMInstruction.goto)
        val ifFalseJoinGoto = patchable_short()

        val ifTrueTarget = position()
        ifTrue()
        ifTrueJumpLocation.data = (ifTrueTarget - before_if).toShort()

        val joinTarget = position()
        ifFalseJoinGoto.data = (joinTarget - before_goto).toShort()
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
            CT_Float -> instruction(JVMInstruction.freturn)
            CT_Long -> TODO()
            CT_Double -> TODO()
            CT_Reference -> instruction(JVMInstruction.areturn)
            CT_ReturnAddress -> throw Exception("Illegal")
        }
    }

    fun getField(className: String, fieldName: String, fieldDescriptor: FieldDescriptor) {
        popStack(CT_Reference)
        instruction(JVMInstruction.getfield)
        immediate_short(classFileBuilder.constantFieldRef(className, fieldName, fieldDescriptor))
        pushStack(fieldDescriptor.toActualJVMType().asComputationalType)
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
    
    fun add_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.iadd)
        pushStack(CT_Int)
    }
    
    fun sub_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.isub)
        pushStack(CT_Int)
    }
    
    fun mul_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.imul)
        pushStack(CT_Int)
    }
    
    fun div_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.idiv)
        pushStack(CT_Int)
    }
    
    fun mod_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.irem)
        pushStack(CT_Int)
    }
    
    fun neg_i32() {
        popStack(CT_Int)
        instruction(JVMInstruction.ineg)
        pushStack(CT_Int)
    }

    fun add_f32() {
        popStack(CT_Float)
        popStack(CT_Float)
        instruction(JVMInstruction.fadd)
        pushStack(CT_Float)
    }

    fun sub_f32() {
        popStack(CT_Float)
        popStack(CT_Float)
        instruction(JVMInstruction.fsub)
        pushStack(CT_Float)
    }

    fun mul_f32() {
        popStack(CT_Float)
        popStack(CT_Float)
        instruction(JVMInstruction.fmul)
        pushStack(CT_Float)
    }

    fun div_f32() {
        popStack(CT_Float)
        popStack(CT_Float)
        instruction(JVMInstruction.fdiv)
        pushStack(CT_Float)
    }

    fun mod_f32() {
        popStack(CT_Float)
        popStack(CT_Float)
        instruction(JVMInstruction.frem)
        pushStack(CT_Float)
    }

    fun neg_f32() {
        popStack(CT_Float)
        instruction(JVMInstruction.fneg)
        pushStack(CT_Float)
    }

    fun and_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.iand)
        pushStack(CT_Int)
    }

    fun or_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.ior)
        pushStack(CT_Int)
    }

    fun xor_i32() {
        popStack(CT_Int)
        popStack(CT_Int)
        instruction(JVMInstruction.ixor)
        pushStack(CT_Int)
    }

    fun finish(): List<Attribute> {
        dos.flush()
        val ba = baos___.toByteArray()
        for (patch in patches) {
            ba[patch.pos + 0] = ((patch.data.toInt() shr 8) and 0xff).toByte()
            ba[patch.pos + 1] = ((patch.data.toInt() shr 0) and 0xff).toByte()
        }
        val code = Attribute.Code(max_stack.toShort(), max_locals.toShort(), ba, emptyList(), emptyList())
        val attributes = mutableListOf<Attribute>(code)
        if (stackMapFrames.isNotEmpty()) {
            val stackMapTable = Attribute.StackMapTable(stackMapFrames)
            attributes.add(stackMapTable)
        }
        return attributes
    }
}

fun JVMComputationalType.toVerifType() = when(this) {
    CT_Int -> VerificationType.Integer
    CT_Float -> VerificationType.Float
    CT_Long -> VerificationType.Long
    CT_Double -> VerificationType.Double
    CT_Reference -> VerificationType.Object(TODO())
    CT_ReturnAddress -> throw Exception("Disallowed")
}