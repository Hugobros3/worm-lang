package boneless.classfile

import boneless.classfile.JVMComputationalType.*
import boneless.emit.getFieldDescriptor
import boneless.emit.getMethodDescriptor
import boneless.emit.getVerificationType
import boneless.type.Type
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.Integer.max

class BytecodeBuilder(private val classFileBuilder: ClassFileBuilder) {
    private var max_stack = 0
    private var max_locals = 0
    private val baos___ = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos___)

    private var locals = mutableListOf<VerificationType>()
    private val stack = mutableListOf<VerificationType>()

    private val patches = mutableListOf<Patch>()
    inner class Patch(val pos: Int, var data: Short)

    private val stackMapFrames = mutableListOf<Attribute.StackMapTable.StackMapFrame>()

    private fun pushStack(t: VerificationType) {
        stack.add(t)
        max_stack = max(max_stack, stack.size)
    }
    private fun popStack(expected: VerificationType) {
        val t = stack.removeAt(stack.size - 1)
        assert(t == expected) { "Popped $t, expected $expected" }
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

    fun reserveVariable(t: Type): Int {
        val vt = classFileBuilder.getVerificationType(t)
        locals.add(vt ?: throw Exception("No reserving variables for zero-sized types: $t"))
        max_locals = max(max_locals, locals.size)
        return locals.size - 1
    }

    fun loadVariable(i: Int) {
        if (i >= locals.size)
            throw Exception("Variable $i is not reserved")
        val t = locals[i]
        when (t) {
            VerificationType.Integer -> {
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
            VerificationType.Float -> {
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
            VerificationType.Long -> TODO()
            VerificationType.Double -> TODO()
            is VerificationType.Object -> {
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
            else -> throw Exception("Unknown verification type $t")
        }
        pushStack(t)
    }

    fun setVariable(i: Int) {
        if (i >= locals.size)
            throw Exception("Variable $i is not reserved")
        val t = locals[i]
        when (t) {
            VerificationType.Integer -> {
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
            VerificationType.Float -> TODO()
            VerificationType.Long -> TODO()
            VerificationType.Double -> TODO()
            is VerificationType.Object -> {
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
            else -> throw Exception("Unknown verification type $t")
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
        pushStack(VerificationType.Integer)
    }

    fun patchable_short(): Patch {
        val now = position()
        immediate_short(0) // patched out afterwards
        val patch = Patch(now, 0)
        patches += patch
        return patch
    }

    fun branch_infeq_i32(yieldType: JVMComputationalType?, ifTrue: () -> Unit, ifFalse: () -> Unit) {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)

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
        val cc_index = classFileBuilder.constantClass(className)
        immediate_short(cc_index)
        pushStack(VerificationType.Object(cc_index.toInt()))
    }

    fun mutateSetFieldName(className: String, fieldName: String, memberType: Type, aggregateType: Type) {
        val vmt = classFileBuilder.getVerificationType(memberType)  ?: throw Exception("Member type can't be zero sized: $aggregateType")
        val vat = classFileBuilder.getVerificationType(aggregateType) ?: throw Exception("Aggregate type can't be zero sized: $aggregateType")
        val fieldDescriptor = getFieldDescriptor(memberType)!!
        popStack(vmt)
        popStack(vat)
        instruction(JVMInstruction.withfield)
        immediate_short(classFileBuilder.constantFieldRef(className, fieldName, fieldDescriptor))
        pushStack(vat)
    }

    fun return_void() {
        instruction(JVMInstruction.`return`)
    }

    fun return_value(type: Type) {
        val vt = classFileBuilder.getVerificationType(type) ?: throw Exception("Return can't be zero sized: $type")
        popStack(vt)
        when(vt) {
            VerificationType.Integer -> instruction(JVMInstruction.ireturn)
            VerificationType.Float -> instruction(JVMInstruction.freturn)
            VerificationType.Long -> TODO()
            VerificationType.Double -> TODO()
            is VerificationType.Object -> instruction(JVMInstruction.areturn)
            else -> throw Exception("Unhandled vt: vt")
        }
    }

    fun getField(className: String, fieldName: String, fieldType: Type) {
        popStack(VerificationType.Object(classFileBuilder.constantClass(className).toInt()))
        instruction(JVMInstruction.getfield)
        val fieldDescriptor = getFieldDescriptor(fieldType)  ?: throw Exception("Extracted field can't be zero sized: $fieldType")
        immediate_short(classFileBuilder.constantFieldRef(className, fieldName, fieldDescriptor))
        pushStack(classFileBuilder.getVerificationType(fieldType)!!)
    }

    fun callStatic(className: String, methodName: String, methodType: Type.FnType) {
        callStaticInternal(className, methodName, getMethodDescriptor(methodType), classFileBuilder.getVerificationType(methodType.codom))
    }

    /** Version that can call into foreign stuff with multiple args */
    fun callStaticInternal(className: String, methodName: String, methodDescriptor: MethodDescriptor, expectedReturnType: VerificationType?) {
        for (fd in methodDescriptor.dom.reversed()) {
            popStack(classFileBuilder.getVerificationType(fd))
        }
        instruction(JVMInstruction.invokestatic)
        immediate_short(classFileBuilder.constantMethodRef(className, methodName, methodDescriptor))
        assert((expectedReturnType == null) == (methodDescriptor.codom == ReturnDescriptor.V))
        if (expectedReturnType != null) {
            pushStack(expectedReturnType)
        }
    }
    
    fun add_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.iadd)
        pushStack(VerificationType.Integer)
    }
    
    fun sub_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.isub)
        pushStack(VerificationType.Integer)
    }
    
    fun mul_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.imul)
        pushStack(VerificationType.Integer)
    }
    
    fun div_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.idiv)
        pushStack(VerificationType.Integer)
    }
    
    fun mod_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.irem)
        pushStack(VerificationType.Integer)
    }
    
    fun neg_i32() {
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.ineg)
        pushStack(VerificationType.Integer)
    }

    fun add_f32() {
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fadd)
        pushStack(VerificationType.Float)
    }

    fun sub_f32() {
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fsub)
        pushStack(VerificationType.Float)
    }

    fun mul_f32() {
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fmul)
        pushStack(VerificationType.Float)
    }

    fun div_f32() {
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fdiv)
        pushStack(VerificationType.Float)
    }

    fun mod_f32() {
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.frem)
        pushStack(VerificationType.Float)
    }

    fun neg_f32() {
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fneg)
        pushStack(VerificationType.Float)
    }

    fun and_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.iand)
        pushStack(VerificationType.Integer)
    }

    fun or_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.ior)
        pushStack(VerificationType.Integer)
    }

    fun xor_i32() {
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.ixor)
        pushStack(VerificationType.Integer)
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

/*fun JVMComputationalType.toVerifType() = when(this) {
    VerificationType.Integer -> VerificationType.Integer
    VerificationType.Float -> VerificationType.Float
    VerificationType.Long -> VerificationType.Long
    VerificationType.Double -> VerificationType.Double
    CT_Reference -> VerificationType.Object(TODO())
    CT_ReturnAddress -> throw Exception("Disallowed")
}*/