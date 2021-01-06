package boneless

sealed class Type {
    data class TypeApplication(val name: String, val ops: List<Expression>) : Type()
    data class TupleType(val elements: List<Type>, val names: List<String>?) : Type() {
        val isStruct: Boolean get() = names != null
        val isUnit: Boolean get() = elements.isEmpty()
        val shouldBeDefiniteArray: Boolean get() = !isUnit && !isStruct && elements.all { it == elements[0] }
    }
    data class ArrayType(val elementType: Type, val size: Int) : Type() {
        val isDefinite: Boolean get() = size != -1
    }
    data class EnumType(val elements: List<Type>, val names: List<String>) : Type()
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

sealed class Instruction {
    data class Def(val identifier: String, val parameters: List<DefParameter>, val type: Type?, val body: Expression) : Instruction() {
        data class DefParameter(val identifier: String)
    }
    data class Let(val identifier: String, val isMutable: Boolean, val type: Type?, val body: Expression) : Instruction()
    data class Var(val identifier: String, val type: Type?, val defaultValue: Expression?) : Instruction()
}

sealed class Expression {
    object Unit : Expression()
    data class StringLit(val lit: String) : Expression()
    data class NumLit(val lit: String) : Expression()
    data class QuoteType(val type: Type) : Expression()

    data class RefSymbol(val symbol: String) : Expression()

    data class Tuple(val elements: List<Expression>) : Expression()
    data class Invocation(val arguments: List<Expression>) : Expression()
    data class Function(val parameters: List<Expression>, val body: Expression) : Expression()

    data class Ascription(val e: Expression, val type: Type) : Expression()

    data class Sequence(val instructions: List<Instruction>, val yieldValue: Expression?) : Expression()
    data class Conditional(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression()
}