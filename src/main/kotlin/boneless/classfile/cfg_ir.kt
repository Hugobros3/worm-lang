package boneless.classfile

data class BasicBlock(
    val pre_locals: List<VerificationType>,
    val pre_stack: List<VerificationType>,
    val post_locals: List<VerificationType>,
    val post_stack: List<VerificationType>,
    var successor: BBSuccessor,
    val code: ByteArray /** Contains JVM instructions *without* any relative bytecode indexes - ie no control flow ! */
)

enum class BranchType {
    BOOL,
    ICMP_LESS_EQUAL
}

sealed class BBSuccessor {
    object Undef: BBSuccessor()
    object FnReturn: BBSuccessor()
    data class Branch(val mode: BranchType, val ifTrue: BasicBlock, val ifFalse: BasicBlock): BBSuccessor()
    data class Jump(val successor: BasicBlock): BBSuccessor()
}

data class Function(val initialBlock: BasicBlock)