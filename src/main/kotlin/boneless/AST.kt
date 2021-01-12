package boneless

typealias Identifier = String

sealed class Type {
    data class PrimitiveType(val primitiveType: boneless.PrimitiveType) : Type()
    data class TypeApplication(val name: String, val ops: List<Expression>) : Type() { lateinit var resolved: BoundIdentifier }
    data class RecordType(val elements: List<Pair<Identifier, Type>>) : Type()
    data class TupleType(val elements: List<Type>) : Type() {
        val isUnit: Boolean get() = elements.isEmpty()
        val canBeDefiniteArray: Boolean get() = !isUnit && elements.all { it == elements[0] }
    }
    data class ArrayType(val elementType: Type, val size: Int) : Type() {
        val isDefinite: Boolean get() = size != 0
    }
    data class EnumType(val elements: List<Pair<Identifier, Type>>) : Type()
}

data class Module(val defs: Set<Def>)
data class Def(val identifier: Identifier, val parameters: List<DefParameter>, val type: Type?, val body: Expression) {
    data class DefParameter(val identifier: Identifier)
}

sealed class Instruction {
    data class Let(val identifier: Identifier, val isMutable: Boolean, val type: Type?, val body: Expression) : Instruction()
    data class Evaluate(val e: Expression) : Instruction()
    //data class Var(val identifier: Identifier, val type: Type?, val defaultValue: Expression?) : Instruction()
}

sealed class Value {
    data class NumLiteral(val num: String): Value()
    data class StrLiteral(val str: String): Value()

    // These can't really be parsed in expressions (the parser has no way of knowing if all the parameters are constant)
    data class ListLiteral(val list: List<Value>): Value()
    data class DictionaryLiteral(val dict: Map<Identifier, Value>): Value()

    val isUnit: Boolean get() = this is ListLiteral && this.list.isEmpty()
}

sealed class Pattern {
    data class Binder(val id: Identifier): Pattern()
    data class Literal(val value: Value): Pattern()
    data class ListPattern(val list: List<Pattern>): Pattern()
    data class DictPattern(val dict: Map<Identifier, Pattern>): Pattern()
    data class CtorPattern(val target: Identifier, val args: List<Pattern>): Pattern() { lateinit var resolved: BoundIdentifier }
    data class TypeAnnotatedPattern(val inside: Pattern, val type: Type): Pattern()

    // Something is refutable (ie there are values of the type of the pattern that do not match the pattern)
    // as soon as it contains a literal.
    val isRefutable: Boolean
        get() = when(this) {
            is Binder -> false
            is Literal -> !value.isUnit // the unit literal is not a refutable pattern
            is ListPattern -> list.any { it.isRefutable }
            is DictPattern -> dict.values.any { it.isRefutable }
            is CtorPattern -> args.any { it.isRefutable }
            is TypeAnnotatedPattern -> false
        }
}

sealed class Expression {
    data class QuoteValue(val value: Value) : Expression()
    data class QuoteType(val type: Type) : Expression()

    data class IdentifierRef(val id: Identifier) : Expression() { lateinit var resolved: BoundIdentifier }

    data class ListExpression(val list: List<Expression>) : Expression()
    data class DictionaryExpression(val dict: Map<Identifier, Expression>) : Expression()

    data class Invocation(val target: Expression, val args: List<Expression>) : Expression()
    data class Function(val parameters: Pattern, val body: Expression) : Expression()

    data class Ascription(val e: Expression, val type: Type) : Expression()
    data class Cast(val e: Expression, val type: Type) : Expression()

    data class Sequence(val instructions: List<Instruction>, val yieldValue: Expression?) : Expression()
    data class Conditional(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression()
}