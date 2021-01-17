package boneless.classfile

fun dump_bytecode(code: ByteArray, cp: List<ConstantPoolEntry>) {
    var pc = 0
    while (pc < code.size) {
        print("$pc: ")
        val opcode = code[pc++]
        val instruction = JVMInstruction.values().find { it.opcode == opcode.toInt() and 0xFF } ?: throw Exception("Unrecognized opcode: ${opcode.toInt() and 0xFF}")
        print(instruction.name)
        if (instruction.operands != "") {
            if (instruction.operands == "?") {
                TODO()
            } else {
                for (c in instruction.operands) {
                    when(c) {
                        'b' -> {
                            val operand = code[pc++].toInt() and 0xFF
                            print(" $operand")
                        }
                        's' -> {
                            val op1 = code[pc++].toInt() and 0xFF
                            val op2 = code[pc++].toInt() and 0xFF
                            val operand = (op1 shl 8) or op2
                            print(" $operand")
                        }
                        'i' -> {
                            val op1 = code[pc++].toInt() and 0xFF
                            val op2 = code[pc++].toInt() and 0xFF
                            val op3 = code[pc++].toInt() and 0xFF
                            val op4 = code[pc++].toInt() and 0xFF
                            val operand = (op1 shl 24) or (op2 shl 16) or (op3 shl 8) or op4
                            print(" $operand")
                        }
                        '0' -> {
                            val operand = code[pc++].toInt() and 0xFF
                            assert(operand == 0)
                            print(" $operand")
                        }
                        else -> throw Exception("unknown operand descriptor $c ?")
                    }
                }
            }
        }
        print("\n")
    }
}