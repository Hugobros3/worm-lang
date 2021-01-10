package boneless

typealias Identifier = String

sealed class Type {
    data class TypeApplication(val name: String, val ops: List<Expression>) : Type()
    data class RecordType(val elements: List<Pair<Identifier, Type>>) : Type()
    data class TupleType(val elements: List<Type>) : Type() {
        val isUnit: Boolean get() = elements.isEmpty()
        val canBeDefiniteArray: Boolean get() = !isUnit && elements.all { it == elements[0] }
    }
    /*{
        val isStruct: Boolean get() = names != null
    }*/
    data class ArrayType(val elementType: Type, val size: Int) : Type() {
        val isDefinite: Boolean get() = size != 0
    }
    data class EnumType(val elements: List<Pair<Identifier, Type>>) : Type()
}

fun Type.normalize(): Type = when {
    // tuples of size 1 do not exist
    this is Type.TupleType && elements.size == 1 -> elements[0]

    // todo make those part of the subtyping relation instead
    // tuples of identical elements are normalized into definite arrays
    //this is Type.TupleType && shouldBeDefiniteArray -> Type.ArrayType(elements[0], elements.size)

    // definite arrays of size 1 do not exist
    //this is Type.ArrayType && size == 1 -> elementType
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

sealed class Instruction {
    data class Def(val identifier: Identifier, val parameters: List<DefParameter>, val type: Type?, val body: Expression) : Instruction() {
        data class DefParameter(val identifier: Identifier)
    }
    data class Let(val identifier: Identifier, val isMutable: Boolean, val type: Type?, val body: Expression) : Instruction()
    data class Var(val identifier: Identifier, val type: Type?, val defaultValue: Expression?) : Instruction()
}

sealed class Value {
    data class NumLiteral(val num: String): Value()
    data class StrLiteral(val str: String): Value()

    // These can't really be parsed in expressions (the parser has no way of knowing if all the parameters are constant)
    data class ListLiteral(val list: List<Value>): Value()
    data class DictionaryLiteral(val dict: Map<Identifier, Value>): Value()
}

sealed class Expression {
    //object Unit : Expression()
    data class QuoteValue(val value: Value) : Expression()
    data class QuoteType(val type: Type) : Expression()

    data class RefSymbol(val symbol: Identifier) : Expression()

    data class ListExpression(val elements: List<Expression>) : Expression()
    data class DictionaryExpression(val elements: Map<Identifier, Expression>) : Expression()

    data class Invocation(val arguments: List<Expression>) : Expression()
    data class Function(val parameters: List<Expression>, val body: Expression) : Expression()

    data class Ascription(val e: Expression, val type: Type) : Expression()
    // data class Cast(val e: Expression, val type: Type) : Expression()

    data class Sequence(val instructions: List<Instruction>, val yieldValue: Expression?) : Expression()
    data class Conditional(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression()
}