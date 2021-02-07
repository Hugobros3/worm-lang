package boneless.emit

import boneless.Pattern
import boneless.classfile.BasicBlock
import boneless.type.Type
import boneless.type.unit_type

typealias ReadVariableRoutine = (BasicBlock) -> Unit
typealias WriteVariableRoutine = (BasicBlock) -> Unit

fun registerPatternReadRoutine(readRoutines: MutableMap<Pattern, ReadVariableRoutine>, pattern: Pattern, readRoutine: ReadVariableRoutine) {
    readRoutines[pattern] = readRoutine
    when (pattern) {
        is Pattern.BinderPattern -> {} // that's it
        is Pattern.LiteralPattern -> TODO()
        is Pattern.ListPattern -> {
            when (val type = pattern.type) {
                is Type.TupleType -> {
                    for ((i, subpattern) in pattern.elements.withIndex()) {
                        if (subpattern.type == unit_type())
                            continue
                        val fieldDescriptor = getFieldDescriptor(subpattern.type)!!
                        val extract_element_procedure: ReadVariableRoutine = { builder ->
                            readRoutine(builder)
                            builder.getField(mangled_datatype_name(type), "_$i", fieldDescriptor)
                        }
                        registerPatternReadRoutine(readRoutines, subpattern, extract_element_procedure)
                    }
                }
                else -> throw Exception("Can't emit a list pattern as a $type")
            }
        }
        is Pattern.RecordPattern -> TODO()
        is Pattern.CtorPattern -> TODO()
        is Pattern.TypeAnnotatedPattern -> registerPatternReadRoutine(readRoutines, pattern.pattern, readRoutine)
    }
}

fun registerPatternWriteRoutine(readRoutines: MutableMap<Pattern, WriteVariableRoutine>, pattern: Pattern, readRoutine: WriteVariableRoutine) {
    readRoutines[pattern] = readRoutine
    when (pattern) {
        is Pattern.BinderPattern -> {} // that's it
        is Pattern.LiteralPattern -> TODO()
        is Pattern.ListPattern -> TODO()
        is Pattern.RecordPattern -> TODO()
        is Pattern.CtorPattern -> TODO()
        is Pattern.TypeAnnotatedPattern -> registerPatternWriteRoutine(readRoutines, pattern.pattern, readRoutine)
    }
}