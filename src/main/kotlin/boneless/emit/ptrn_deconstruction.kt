package boneless.emit

import boneless.Pattern
import boneless.classfile.BasicBlock
import boneless.type.Type
import boneless.type.unit_type

typealias PutOnStack = (BasicBlock) -> Unit

fun Emitter.registerPattern(map: MutableMap<Pattern, PutOnStack>, pattern: Pattern, procedure: PutOnStack) {
    map[pattern] = procedure
    when (pattern) {
        is Pattern.BinderPattern -> {} // that's it
        is Pattern.LiteralPattern -> TODO()
        is Pattern.ListPattern -> {
            when (val type = pattern.type!!) {
                is Type.TupleType -> {
                    for ((i, subpattern) in pattern.elements.withIndex()) {
                        if (subpattern.type!! == unit_type())
                            continue
                        val fieldDescriptor = getFieldDescriptor(subpattern.type!!)!!
                        val extract_element_procedure: PutOnStack = { builder ->
                            procedure(builder)
                            builder.getField(mangled_datatype_name(type), "_$i", fieldDescriptor)
                        }
                        registerPattern(map, subpattern, extract_element_procedure)
                    }
                }
                else -> throw Exception("Can't emit a list pattern as a $type")
            }
        }
        is Pattern.RecordPattern -> TODO()
        is Pattern.CtorPattern -> TODO()
        is Pattern.TypeAnnotatedPattern -> registerPattern(map, pattern.pattern, procedure)
    }
}