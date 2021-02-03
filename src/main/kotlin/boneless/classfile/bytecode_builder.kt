package boneless.classfile

import boneless.emit.getFieldDescriptor
import boneless.emit.getMethodDescriptor
import boneless.emit.getVerificationType
import boneless.type.Type
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.Integer.max

data class JumpTarget(internal val bytecodeOffset: Int)

internal sealed class BBBSucc {
    object Undef: BBBSucc()
    object FnReturn: BBBSucc()
    data class Branch(val mode: BranchType, val ifTrue: BasicBlockBuilder, val ifFalse: BasicBlockBuilder): BBBSucc()
    data class Jump(val successor: BasicBlockBuilder): BBBSucc()
}

class MethodBuilder(private val classFileBuilder: ClassFileBuilder, initialLocals: List<VerificationType>) {
    val bbBuilders = mutableListOf<BasicBlockBuilder>()
    var cnt = 0

    val initialBasicBlock = BasicBlockBuilder(classFileBuilder, initialLocals, emptyList(), "entry_point")

    init {
        bbBuilders += initialBasicBlock
    }

    fun basicBlock(
        pre_locals: List<VerificationType>,
        pre_stack: List<VerificationType>,
        bbName: String = "basicBlock${cnt++}"
    ): BasicBlockBuilder {
        val bbb = BasicBlockBuilder(classFileBuilder, pre_locals, pre_stack, bbName)
        bbBuilders += bbb
        return bbb
    }

    fun basicBlock(
        predecessor: BasicBlockBuilder,
        bbName: String = "basicBlock${cnt++}",
        additionalStack: List<VerificationType> = emptyList()
    ): BasicBlockBuilder {
        val bbb = BasicBlockBuilder(classFileBuilder, predecessor.locals, predecessor.stack + additionalStack, bbName)
        bbBuilders += bbb
        return bbb
    }

    private val patches = mutableListOf<Patch>()

    sealed class Patch {
        class ShortPatch(override val bytecodeLocation: Int, var data: Short) : Patch() {
            override fun apply_patch(ba: ByteArray) {
                ba[bytecodeLocation + 0] = ((data.toInt() shr 8) and 0xff).toByte()
                ba[bytecodeLocation + 1] = ((data.toInt() shr 0) and 0xff).toByte()
            }
        }

        class JumpTargetPatch(override val bytecodeLocation: Int, private val initialPosition: Int) : Patch() {
            lateinit var target: JumpTarget
            override fun apply_patch(ba: ByteArray) {
                val diff = target.bytecodeOffset - initialPosition
                ba[bytecodeLocation + 0] = ((diff shr 8) and 0xff).toByte()
                ba[bytecodeLocation + 1] = ((diff shr 0) and 0xff).toByte()
            }
        }

        abstract val bytecodeLocation: Int
        abstract fun apply_patch(ba: ByteArray)
    }

    val baos___ = ByteArrayOutputStream()
    val dos = DataOutputStream(baos___)
    var lastStackMapOffset = 0
    val stackMapFrames = mutableListOf<Attribute.StackMapTable.StackMapFrame>()
    fun position() = baos___.size()

    fun finish(): List<Attribute> {
        var max_stack = 0
        var max_locals = 0

        fun emit_bb(bbb: BasicBlockBuilder): Int {
            if (bbb.emitted_position != null)
                return bbb.emitted_position!!
            else {
                // TODO check locals/stack match upon control flow
                max_locals = max(max_locals, bbb.max_locals)
                max_stack = max(max_stack, bbb.max_stack)
                bbb.finish_()
                val p = position()
                dos.write(bbb.finished!!.code)
                bbb.emitted_position = p
                when (val succ = bbb.succ!!) {
                    BBBSucc.Undef -> throw Exception("Uninitialized control flow !")
                    BBBSucc.FnReturn -> {
                        /** nothing to do, instruction was already in the bb */
                    }
                    is BBBSucc.Branch -> {
                        // TODO use mode to check the bb yields what we need on the stack
                        val if_true = branch(succ.mode)
                        // If the jump target wasn't emitted already, write it directly
                        if (succ.ifFalse.emitted_position == null) {
                            emit_bb(succ.ifFalse)
                        } else {
                            // Otherwise we have to create a GOTO
                            val goto_target = goto()
                            goto_target.target = succ.ifFalse.get_jump_target()
                        }
                        emit_bb(succ.ifTrue)
                        if_true.target = succ.ifTrue.get_jump_target()
                    }
                    is BBBSucc.Jump -> {
                        // If the jump target wasn't emitted already, write it directly
                        if (succ.successor.emitted_position == null) {
                            emit_bb(succ.successor)
                        } else {
                            // Otherwise we have to create a GOTO
                            val goto_target = goto()
                            goto_target.target = succ.successor.get_jump_target()
                        }
                    }
                }
                return p
            }
        }

        emit_bb(bbBuilders[0])
        dos.flush()
        val ba = baos___.toByteArray()
        for (patch in patches) {
            patch.apply_patch(ba)
        }

        for (bbb in bbBuilders.filter { it.is_jump_target }.sortedBy { it.emitted_position!! }) {
            bbb.emit_stack_map()
        }

        val codeAttributes = mutableListOf<AttributeInfo>()
        if (stackMapFrames.isNotEmpty()) {
            val stackMapTable = Attribute.StackMapTable(stackMapFrames)
            codeAttributes.add(stackMapTable.wrap(classFileBuilder))
        }
        val code = Attribute.Code(max_stack.toShort(), max_locals.toShort(), ba, emptyList(), codeAttributes)
        val attributes = mutableListOf<Attribute>(code)
        return attributes
    }

    private fun patchable_jump(jumpInstructionLocation: Int): Patch.JumpTargetPatch {
        val now = position()
        dos.writeShort(0) // patched out afterwards
        val patch = Patch.JumpTargetPatch(now, jumpInstructionLocation)
        patches += patch
        return patch
    }

    private fun BasicBlockBuilder.get_jump_target(): JumpTarget {
        this.is_jump_target = true
        val jt = JumpTarget(this.emitted_position!!)
        return jt
    }

    private fun BasicBlockBuilder.emit_stack_map() {
        val pos = this.emitted_position!!
        assert(pos >= lastStackMapOffset)
        val offset = pos - lastStackMapOffset
        val frame = Attribute.StackMapTable.StackMapFrame.FullFrame(offset, pre_locals, pre_stack)
        stackMapFrames += frame
        lastStackMapOffset = pos + 1
    }

    private fun goto(): Patch.JumpTargetPatch {
        val before_goto = position()
        instruction(JVMInstruction.goto)
        return patchable_jump(before_goto)
    }

    private fun branch(mode: BranchType): Patch.JumpTargetPatch {
        val before_goto = position()
        when (mode) {
            BranchType.ICMP_LESS_EQUAL -> instruction(JVMInstruction.if_icmple)
        }
        return patchable_jump(before_goto)
    }

    private fun instruction(i: JVMInstruction) {
        dos.writeByte(i.opcode)
    }
}

class BasicBlockBuilder internal constructor(
    private val classFileBuilder: ClassFileBuilder,
    internal val pre_locals: List<VerificationType>,
    internal val pre_stack: List<VerificationType>,
    private val bbName: String) {

    var locals: List<VerificationType> = pre_locals
    var stack: List<VerificationType> = pre_stack
    var max_stack = pre_stack.size
    var max_locals = pre_locals.size

    private val baos___ = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos___)

    internal var finished: BasicBlock? = null
    internal var succ: BBBSucc? = null
    internal var emitted_position: Int? = null

    //internal var jump_target: JumpTarget?
    internal var is_jump_target = false
    internal val preds = mutableListOf<BasicBlock>()
    /*

    private var lastStackMapOffset = 0
    private val stackMapFrames = mutableListOf<Attribute.StackMapTable.StackMapFrame>()*/

    private fun pushStack(t: VerificationType) {
        stack = stack + listOf(t)
        max_stack = max(max_stack, stack.size)
    }
    private fun popStack(expected: VerificationType) {
        val t = stack.last()
        stack = stack.dropLast(1)
        assert(t == expected) { "Popped $t, expected $expected" }
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

    fun reserveVariable(t: Type): Int {
        val vt = classFileBuilder.getVerificationType(t)
        locals = locals + listOf(vt ?: throw Exception("No reserving variables for zero-sized types: $t"))
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

    fun return_void() {
        instruction(JVMInstruction.`return`)
        succ = BBBSucc.FnReturn
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
        succ = BBBSucc.FnReturn
    }

    fun jump(target: BasicBlockBuilder) {
        succ = BBBSucc.Jump(target)
    }

    fun branch(mode: BranchType, ifTrue: BasicBlockBuilder, ifFalse: BasicBlockBuilder) {
        succ = BBBSucc.Branch(mode, ifTrue, ifFalse)
    }

    fun finish_(): BasicBlock {
        if (finished != null) throw Exception("Can't finish twice")
        finished = BasicBlock(pre_locals, pre_stack, locals, stack, BBSuccessor.Undef, baos___.toByteArray())
        return finished!!
    }
}