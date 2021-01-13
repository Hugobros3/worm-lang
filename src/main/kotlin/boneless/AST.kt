package boneless

typealias Identifier = String

sealed class Type {
    data class PrimitiveType(val primitiveType: boneless.PrimitiveType) : Type()
    data class TypeApplication(val callee: BindPoint, val args: List<Expression>) : Type()
    data class RecordType(val elements: List<Pair<Identifier, Type>>) : Type()
    data class TupleType(val elements: List<Type>) : Type() {
        val isUnit: Boolean get() = elements.isEmpty()
        val canBeDefiniteArray: Boolean get() = !isUnit && elements.all { it == elements[0] }
    }
    data class ArrayType(val elementType: Type, val size: Int) : Type() {
        val isDefinite: Boolean get() = size != 0
    }
    data class EnumType(val elements: List<Pair<Identifier, Type>>) : Type()
    data class NominalType(val name: Identifier, val dataType: Type): Type()
    data class FnType(val dom: Type, val codom: Type, val constructorFor: NominalType? = null, val builtin: BuiltinFn? = null) : Type()
}

data class Module(val defs: Set<Def>)
data class Def(val identifier: Identifier, val parameters: List<DefParameter>, val annotatedType: Type?, val body: DefBody) : Typeable by typeable() {
    init {
        assert(parameters.isEmpty()) { "unsupported" }
    }

    sealed class DefBody {
        data class ExprBody(val expr: Expression): DefBody()
        data class DataCtor(val type: Type): DefBody() {
            // Created by type-checker
            lateinit var nominalType: Type.NominalType
        }
        data class TypeAlias(val type: Type): DefBody()
    }

    val is_type: Boolean = body is DefBody.TypeAlias

    data class DefParameter(val identifier: Identifier)
}

sealed class Instruction {
    data class Let(val binder: Pattern.Binder, val isMutable: Boolean, val annotatedType: Type?, val body: Expression) : Instruction()
    data class Evaluate(val e: Expression) : Instruction()
    //data class Var(val identifier: Identifier, val type: Type?, val defaultValue: Expression?) : Instruction()
}

sealed class Value : Typeable by typeable() {
    data class NumLiteral(val num: String): Value()
    data class StrLiteral(val str: String): Value()

    // These can't really be parsed in expressions (the parser has no way of knowing if all the parameters are constant)
    data class ListLiteral(val list: List<Value>): Value()
    data class RecordLiteral(val fields: List<Pair<Identifier, Value>>): Value()

    val isUnit: Boolean get() = this is ListLiteral && this.list.isEmpty()
}

sealed class Pattern : Typeable by typeable() {
    data class Binder(val id: Identifier): Pattern()
    data class Literal(val value: Value): Pattern()
    data class ListPattern(val list: List<Pattern>): Pattern()
    data class RecordPattern(val fields: List<Pair<Identifier, Pattern>>): Pattern()
    data class CtorPattern(val callee: BindPoint, val args: List<Pattern>): Pattern()
    data class TypeAnnotatedPattern(val inside: Pattern, val annotatedType: Type): Pattern()

    // Something is refutable (ie there are values of the type of the pattern that do not match the pattern)
    // as soon as it contains a literal.
    val isRefutable: Boolean
        get() = when(this) {
            is Binder -> false
            is Literal -> !value.isUnit // the unit literal is not a refutable pattern
            is ListPattern -> list.any { it.isRefutable }
            is RecordPattern -> fields.any { it.second.isRefutable }
            is CtorPattern -> args.any { it.isRefutable }
            is TypeAnnotatedPattern -> false
        }
}

sealed class Expression : Typeable by typeable() {
    data class QuoteValue(val value: Value) : Expression()
    data class QuoteType(val quotedType: Type) : Expression()

    data class IdentifierRef(val referenced: BindPoint) : Expression()

    data class ListExpression(val list: List<Expression>) : Expression()
    data class RecordExpression(val fields: List<Pair<Identifier, Expression>>) : Expression()

    data class Invocation(val callee: Expression, val args: List<Expression>) : Expression()
    data class Function(val parameters: Pattern, val body: Expression) : Expression()

    data class Ascription(val e: Expression, val ascribedType: Type) : Expression()
    data class Cast(val e: Expression, val destinationType: Type) : Expression()

    data class Sequence(val instructions: List<Instruction>, val yieldValue: Expression?) : Expression()
    data class Conditional(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression()
}