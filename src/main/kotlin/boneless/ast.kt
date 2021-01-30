package boneless

import boneless.bind.BindPoint
import boneless.core.BuiltinFn
import boneless.type.PrimitiveTypeEnum
import boneless.type.Type
import boneless.type.Typeable
import boneless.type.typeable

typealias Identifier = String

data class Module(val name: Identifier, val defs: Set<Def>)
data class Def(val identifier: Identifier, val body: DefBody, val typeParams: List<Identifier>) : Typeable by typeable() {
    sealed class DefBody {
        data class ExprBody(val expr: Expression, val annotatedType: TypeExpr?): DefBody()
        data class DataCtor(val datatype: TypeExpr): DefBody() {
            // Created by type-checker
            lateinit var nominalType: Type.NominalType
        }
        data class FnBody(val fn: Expression.Function): DefBody()
        data class TypeAlias(val aliasedType: TypeExpr): DefBody()

        data class Contract(val payload: TypeExpr) : DefBody()
        data class Instance(val contractId: BindPoint, val argumentsExpr: List<TypeExpr>, val body: Expression) : DefBody() {
            lateinit var arguments: List<Type>
        }
    }

    val is_type: Boolean = body is DefBody.TypeAlias
}

sealed class Instruction {
    data class Let(val pattern: Pattern, val body: Expression) : Instruction()
    data class Evaluate(val expr: Expression) : Instruction()
}

sealed class Pattern : Typeable by typeable() {
    data class BinderPattern(val id: Identifier): Pattern()
    data class LiteralPattern(val literal: Literal): Pattern()
    data class ListPattern(val elements: List<Pattern>): Pattern()
    data class RecordPattern(val fields: List<Pair<Identifier, Pattern>>): Pattern()
    data class CtorPattern(val callee: BindPoint, val args: List<Pattern>): Pattern()
    data class TypeAnnotatedPattern(val pattern: Pattern, val annotatedType: TypeExpr): Pattern()

    // Something is refutable (ie there are values of the type of the pattern that do not match the pattern)
    // as soon as it contains a literal.
    val isRefutable: Boolean
        get() = when(this) {
            is BinderPattern -> false
            is LiteralPattern -> !literal.isUnit // the unit literal is not a refutable pattern
            is ListPattern -> elements.any { it.isRefutable }
            is RecordPattern -> fields.any { it.second.isRefutable }
            is CtorPattern -> args.any { it.isRefutable }
            is TypeAnnotatedPattern -> false
        }
}

sealed class Expression : Typeable by typeable() {
    data class QuoteLiteral(val literal: Literal) : Expression()
    data class QuoteType(val quotedType: TypeExpr) : Expression()

    data class IdentifierRef(val id: BindPoint) : Expression()
    data class ExprSpecialization(val target: IdentifierRef, val arguments: List<TypeExpr>) : Expression()

    data class ListExpression(val elements: List<Expression>, val is_synthesized_invocation_argument_list: Boolean = false) : Expression()
    data class RecordExpression(val fields: List<Pair<Identifier, Expression>>) : Expression()

    data class Projection(val expression: Expression, val id: /** not a bind point because only the type checker can figure it out */Identifier) : Expression()

    data class Invocation(val callee: Expression, val arg: Expression) : Expression()
    data class Function(val param: Pattern, val body: Expression, val returnTypeAnnotation: TypeExpr? = null) : Expression()

    data class Ascription(val expr: Expression, val ascribedType: TypeExpr) : Expression()
    data class Cast(val expr: Expression, val destinationType: TypeExpr) : Expression()

    data class Sequence(val instructions: List<Instruction>, val yieldExpression: Expression?) : Expression()
    data class Conditional(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression()
    data class WhileLoop(val loopCondition: Expression, val body: Expression) : Expression()
}

sealed class TypeExpr {
    data class PrimitiveType(val primitiveType: PrimitiveTypeEnum) : TypeExpr()
    data class TypeNameRef(val callee: BindPoint) : TypeExpr()
    data class TypeSpecialization(val target: TypeNameRef, val arguments: List<TypeExpr>) : TypeExpr()
    data class RecordType(val elements: List<Pair<Identifier, TypeExpr>>) : TypeExpr()
    data class TupleType(val elements: List<TypeExpr>) : TypeExpr() {
        val isUnit: Boolean get() = elements.isEmpty()
    }
    data class ArrayType(val elementType: TypeExpr, val size: Int) : TypeExpr() {
        val isDefinite: Boolean get() = size != 0
    }
    data class EnumType(val elements: List<Pair<Identifier, TypeExpr>>) : TypeExpr()
    data class FnType(val dom: TypeExpr, val codom: TypeExpr) : TypeExpr()
}

sealed class Literal : Typeable by typeable() {
    data class NumLiteral(val number: String): Literal()
    data class StrLiteral(val string: String): Literal()

    // These can't really be parsed in expressions (the parser has no way of knowing if all the parameters are constant)
    data class ListLiteral(val elements: List<Literal>): Literal()
    data class RecordLiteral(val fields: List<Pair<Identifier, Literal>>): Literal()

    val isUnit: Boolean get() = this is ListLiteral && this.elements.isEmpty()
}