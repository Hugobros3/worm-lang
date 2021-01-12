package boneless

fun Type.normalize(): Type = when {
    // tuples of size 1 do not exist
    this is Type.TupleType && elements.size == 1 -> elements[0]
    // definite arrays of size 1 do not exist
    // this is Type.ArrayType && size == 1 -> elementType
    else -> this
}

fun isSubtype(T: Type, S: Type): Boolean {
    return when {
        // A definite array is a subtype of a tuple type iff that tuple type is not unit, and it has the same data layout as the definite array
        T is Type.ArrayType && S is Type.TupleType && T.isDefinite && T.size == S.elements.size && S.elements.all { it == T.elementType } -> true
        // And vice versa
        T is Type.TupleType && S is Type.ArrayType && S.isDefinite && S.size == T.elements.size && T.elements.all { it == S.elementType } -> true

        // A struct type T is a subtype of a nameless subtype S if they contain the same things, in the same order
        T is Type.RecordType && S is Type.TupleType && T.elements.map { it.second } == S.elements -> true

        // A record type T is a subtype of another record type S iff the elements in T are a superset of the elements in S
        T is Type.RecordType && S is Type.RecordType && T.elements.containsAll(S.elements) -> true
        else -> false
    }
}

fun unit_type() = Type.TupleType(emptyList())

enum class PrimitiveType(val size: Int) {
    /*
    U8(1),
    U16(2),
    U32(4),
    U64(8),*/

    // Not needed for now
    //I8(1),
    //I16(2),
    I32(4),
    //I64(8),

    F32(4),
    // F64(8)
    ;
}

class TypeChecker(val module: Module) {
    val stack = mutableListOf<Frame>()

    class Frame(val typingWhat: Any)

    fun enter(what: Any): Boolean {
        val oh_no = stack.any { it.typingWhat == what }
        if (oh_no) {
            error("The type checker has run into recursive problems typing $what")
        }
        stack.add(0, Frame(what))
        return oh_no
    }

    fun leave() = stack.removeAt(0)
    fun <R> in_frame(what: Any, f: () -> R): R {
        enter(what)
        val r = f()
        leave()
        return r
    }

    fun type() {
        for (def in module.defs) {
            def.type = infer(def)
        }
    }

    fun infer(def: Def): Type = in_frame(def) {
        if (def.body is Expression.QuoteType) {
            def.is_type = true
            // TODO check/resolve types ?
            def.body.type
        } else {
            enter(def.body)
            def.is_type = false
            infer(def.body)
        }
    }

    fun infer(expr: Expression): Type = in_frame(expr) {
        when (expr) {
            is Expression.QuoteValue -> infer(expr.value)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> TODO()
            is Expression.ListExpression -> TODO()
            is Expression.RecordExpression -> TODO()
            is Expression.Invocation -> TODO()
            is Expression.Function -> TODO()
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Sequence -> {
                for (inst in expr.instructions)
                    check(inst)
                if(expr.yieldValue == null)
                    unit_type()
                else
                    infer(expr.yieldValue)
            }
            is Expression.Conditional -> TODO()
        }
    }

    fun infer(value: Value): Type = in_frame(value) {
        when (value) {
            is Value.NumLiteral -> if (value.num.toDouble() == value.num.toInt().toDouble()) Type.PrimitiveType(PrimitiveType.I32) else Type.PrimitiveType(PrimitiveType.F32)
            is Value.StrLiteral -> TODO()
            is Value.ListLiteral -> {
                val types = value.list.map { infer(it) }
                if (types.isEmpty())
                    unit_type()
                //else if (types.size == 1)
                //    types[0]
                else if (types.isNotEmpty() && types.all { it == types[0] })
                    Type.ArrayType(types[0], types.size)
                else
                    Type.TupleType(types)
            }
            is Value.RecordLiteral -> {
                val types = value.fields.map { (f, v) -> Pair(f, infer(v)) }
                Type.RecordType(types)
            }
        }
    }

    fun check(inst: Instruction) = in_frame(inst) {
        when(inst) {
            is Instruction.Let -> {
                val expected_type = inst.type
                if (expected_type != null)
                    check(inst.body, expected_type)
            }
            is Instruction.Evaluate -> {
                val inferred = infer(inst.e)
            }
        }
    }

    fun check(expr: Expression, expected_type: Type) {

    }
}