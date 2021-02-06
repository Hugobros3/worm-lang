package boneless.classfile

import boneless.classfile.util.MethodBuilderDotPrinter

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.Writer
import java.lang.Integer.max

internal sealed class BasicBlockOutFlow {
    object Undef: BasicBlockOutFlow()
    object FnReturn: BasicBlockOutFlow()
    data class Branch(val mode: BranchType, val ifTrue: BasicBlock, val ifFalse: BasicBlock): BasicBlockOutFlow()
    data class Jump(val successor: BasicBlock): BasicBlockOutFlow()
}

enum class BranchType {
    IF_LESS_EQUAL,
    IF_LESS,
    IF_EQ,
    IF_NEQ,
    IF_GREATER,
    IF_GREATER_EQUAL,

    ICMP_LESS_EQUAL,
    ICMP_LESS,
    ICMP_EQ,
    ICMP_NEQ,
    ICMP_GREATER,
    ICMP_GREATER_EQUAL,
}
private data class JumpTarget(val bytecodeOffset: Int)

private sealed class Patch {
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

class MethodBuilder(private val classFileBuilder: ClassFileBuilder, initialLocals: List<VerificationType>) {
    val initialBasicBlock = BasicBlock(classFileBuilder, initialLocals, emptyList(), "entry_point")

    internal val bbBuilders = mutableListOf<BasicBlock>()
    private var cnt = 0

    private val baos___ = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos___)
    private var lastStackMapOffset = 0
    private val stackMapFrames = mutableListOf<Attribute.StackMapTable.StackMapFrame>()
    private fun position() = baos___.size()

    private val patches = mutableListOf<Patch>()

    init {
        bbBuilders += initialBasicBlock
    }

    fun basicBlock(pre_locals: List<VerificationType>, pre_stack: List<VerificationType>, bbName: String = "basicBlock"): BasicBlock {
        var uniqueBBName = bbName
        if (bbBuilders.find { it.bbName == bbName } != null) {
            uniqueBBName = bbName + "${cnt++}"
        }
        val bbb = BasicBlock(classFileBuilder, pre_locals, pre_stack, uniqueBBName)
        bbBuilders += bbb
        return bbb
    }

    fun basicBlock(predecessor: BasicBlock, bbName: String = "basicBlock", additionalStackInputs: List<VerificationType> = emptyList()): BasicBlock {
        return basicBlock(predecessor.locals, predecessor.stack + additionalStackInputs, bbName)
    }

    fun finish(dumpDot: Writer?): List<Attribute> {
        var max_stack = 0
        var max_locals = 0

        /*for (bb in bbBuilders) {
            assert(bb.finalized)
        }*/

        if (dumpDot != null) {
            MethodBuilderDotPrinter(this, dumpDot).print()
        }

        fun emit_bb(bb: BasicBlock): Int {
            if (bb.emitted_position != null)
                return bb.emitted_position!!
            else {
                // TODO check locals/stack match upon control flow
                max_locals = max(max_locals, bb.max_locals)
                max_stack = max(max_stack, bb.max_stack)
                bb.finalize()
                val p = position()
                dos.write(bb.code)
                bb.emitted_position = p
                when (val succ = bb.outgoingFlow!!) {
                    BasicBlockOutFlow.Undef -> throw Exception("Uninitialized control flow !")
                    BasicBlockOutFlow.FnReturn -> {
                        /** nothing to do, instruction was already in the bb */
                    }
                    is BasicBlockOutFlow.Branch -> {
                        // TODO use mode to check the bb yields what we need on the stack
                        val ifTrue = branch(succ.mode)
                        // If the jump target wasn't emitted already, write it directly
                        if (succ.ifFalse.emitted_position == null) {
                            emit_bb(succ.ifFalse)
                        } else {
                            // Otherwise we have to create a GOTO
                            val gotoTarget = goto()
                            gotoTarget.target = succ.ifFalse.get_jump_target(bb)
                        }
                        emit_bb(succ.ifTrue)
                        ifTrue.target = succ.ifTrue.get_jump_target(bb)
                    }
                    is BasicBlockOutFlow.Jump -> {
                        // If the jump target wasn't emitted already, write it directly
                        if (succ.successor.emitted_position == null) {
                            emit_bb(succ.successor)
                        } else {
                            // Otherwise we have to create a GOTO
                            val gotoTarget = goto()
                            gotoTarget.target = succ.successor.get_jump_target(bb)
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

        for (bbb in bbBuilders.filter { it.isJumpTarget }.sortedBy { it.emitted_position!! }) {
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

    private fun BasicBlock.get_jump_target(predecessor: BasicBlock): JumpTarget {
        this.predecessors.add(predecessor)
        return JumpTarget(emitted_position!!)
    }

    private fun BasicBlock.emit_stack_map() {
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
            BranchType.IF_LESS_EQUAL -> instruction(JVMInstruction.ifle)
            BranchType.IF_LESS -> instruction(JVMInstruction.iflt)
            BranchType.IF_EQ -> instruction(JVMInstruction.ifeq)
            BranchType.IF_NEQ -> instruction(JVMInstruction.ifne)
            BranchType.IF_GREATER -> instruction(JVMInstruction.ifgt)
            BranchType.IF_GREATER_EQUAL -> instruction(JVMInstruction.ifge)

            BranchType.ICMP_LESS_EQUAL -> instruction(JVMInstruction.if_icmple)
            BranchType.ICMP_LESS -> instruction(JVMInstruction.if_icmplt)
            BranchType.ICMP_EQ -> instruction(JVMInstruction.if_icmpeq)
            BranchType.ICMP_NEQ -> instruction(JVMInstruction.if_icmpne)
            BranchType.ICMP_GREATER -> instruction(JVMInstruction.if_icmpgt)
            BranchType.ICMP_GREATER_EQUAL -> instruction(JVMInstruction.if_icmpge)
            else -> throw Exception("Forgot a branch type here")
        }
        return patchable_jump(before_goto)
    }

    private fun instruction(i: JVMInstruction) {
        dos.writeByte(i.opcode)
    }
}

class BasicBlock internal constructor(
    private val classFileBuilder: ClassFileBuilder,
    internal val pre_locals: List<VerificationType>,
    internal val pre_stack: List<VerificationType>,
    internal val bbName: String) {

    var locals: List<VerificationType> = pre_locals
    var stack: List<VerificationType> = pre_stack
    var max_stack = pre_stack.size
    var max_locals = pre_locals.size

    private val baos___ = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos___)
    internal var finalized = false
        private set

    internal lateinit var code: ByteArray
    internal var outgoingFlow: BasicBlockOutFlow? = null
    internal var emitted_position: Int? = null

    internal val isJumpTarget: Boolean get() = predecessors.isNotEmpty()
    internal val predecessors = mutableListOf<BasicBlock>()

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

    fun reserveVariable(vt: VerificationType): Int {
        assertNotFinalized()
        locals = locals + listOf(vt)
        max_locals = max(max_locals, locals.size)
        return locals.size - 1
    }

    fun loadVariable(i: Int) {
        assertNotFinalized()
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
        assertNotFinalized()
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
        assertNotFinalized()
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
        assertNotFinalized()
        instruction(JVMInstruction.defaultvalue)
        val cc_index = classFileBuilder.constantClass(className)
        immediate_short(cc_index)
        pushStack(VerificationType.Object(cc_index.toInt()))
    }

    fun mutateSetFieldName(className: String, fieldName: String, fieldDescriptor: FieldDescriptor, aggregateType: VerificationType) {
        assertNotFinalized()
        val vmt = classFileBuilder.getVerificationType(fieldDescriptor)
        popStack(vmt)
        popStack(aggregateType)
        instruction(JVMInstruction.withfield)
        immediate_short(classFileBuilder.constantFieldRef(className, fieldName, fieldDescriptor))
        pushStack(aggregateType)
    }

    fun getField(className: String, fieldName: String, fieldDescriptor: FieldDescriptor) {
        assertNotFinalized()
        popStack(VerificationType.Object(classFileBuilder.constantClass(className).toInt()))
        instruction(JVMInstruction.getfield)
        immediate_short(classFileBuilder.constantFieldRef(className, fieldName, fieldDescriptor))
        pushStack(classFileBuilder.getVerificationType(fieldDescriptor))
    }

    /** Version that can call into foreign stuff with multiple args */
    fun callStaticInternal(className: String, methodName: String, methodDescriptor: MethodDescriptor, expectedReturnType: VerificationType?) {
        assertNotFinalized()
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
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.iadd)
        pushStack(VerificationType.Integer)
    }
    
    fun sub_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.isub)
        pushStack(VerificationType.Integer)
    }
    
    fun mul_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.imul)
        pushStack(VerificationType.Integer)
    }
    
    fun div_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.idiv)
        pushStack(VerificationType.Integer)
    }
    
    fun mod_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.irem)
        pushStack(VerificationType.Integer)
    }
    
    fun neg_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.ineg)
        pushStack(VerificationType.Integer)
    }

    fun add_f32() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fadd)
        pushStack(VerificationType.Float)
    }

    fun sub_f32() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fsub)
        pushStack(VerificationType.Float)
    }

    fun mul_f32() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fmul)
        pushStack(VerificationType.Float)
    }

    fun div_f32() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fdiv)
        pushStack(VerificationType.Float)
    }

    fun mod_f32() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.frem)
        pushStack(VerificationType.Float)
    }

    fun neg_f32() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fneg)
        pushStack(VerificationType.Float)
    }

    fun and_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.iand)
        pushStack(VerificationType.Integer)
    }

    fun or_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.ior)
        pushStack(VerificationType.Integer)
    }

    fun xor_i32() {
        assertNotFinalized()
        popStack(VerificationType.Integer)
        popStack(VerificationType.Integer)
        instruction(JVMInstruction.ixor)
        pushStack(VerificationType.Integer)
    }

    fun fcmpl() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fcmpl)
        pushStack(VerificationType.Float)
    }

    fun fcmpg() {
        assertNotFinalized()
        popStack(VerificationType.Float)
        popStack(VerificationType.Float)
        instruction(JVMInstruction.fcmpg)
        pushStack(VerificationType.Float)
    }

    fun drop_value(verificationType: VerificationType) {
        assertNotFinalized()
        popStack(verificationType)
        // TODO use pop2 for 64 bit types
        instruction(JVMInstruction.pop)
    }

    fun return_void() {
        assertNotFinalized()
        if (outgoingFlow != null)
            throw Exception("Can't set successor twice")
        instruction(JVMInstruction.`return`)
        outgoingFlow = BasicBlockOutFlow.FnReturn
    }

    fun return_value(vt: VerificationType) {
        assertNotFinalized()
        if (outgoingFlow != null)
            throw Exception("Can't set successor twice")
        popStack(vt)
        when(vt) {
            VerificationType.Integer -> instruction(JVMInstruction.ireturn)
            VerificationType.Float -> instruction(JVMInstruction.freturn)
            VerificationType.Long -> TODO()
            VerificationType.Double -> TODO()
            is VerificationType.Object -> instruction(JVMInstruction.areturn)
            else -> throw Exception("Unhandled vt: vt")
        }
        outgoingFlow = BasicBlockOutFlow.FnReturn
    }

    fun jump(target: BasicBlock) {
        assertNotFinalized()
        if (outgoingFlow != null)
            throw Exception("Can't set successor twice")
        outgoingFlow = BasicBlockOutFlow.Jump(target)
    }

    fun branch(mode: BranchType, ifTrue: BasicBlock, ifFalse: BasicBlock) {
        assertNotFinalized()
        if (outgoingFlow != null)
            throw Exception("Can't set successor twice")
        outgoingFlow = BasicBlockOutFlow.Branch(mode, ifTrue, ifFalse)
    }

    private fun assertNotFinalized() {
        if (finalized) throw Exception("BB was already finalized")
    }

    internal fun finalize() {
        if (finalized) throw Exception("Can't finish twice")
        code = baos___.toByteArray()
        dos.close()
        finalized = true
    }
}