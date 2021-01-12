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

class ModuleType {
}