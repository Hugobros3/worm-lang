package boneless.emit

import boneless.Expression
import boneless.Pattern
import boneless.bind.BindPoint
import boneless.bind.TermLocation
import boneless.type.Type

/** GARBAGE GARBAGE GARBAGE CURSED SHIT ZONE DON'T READ */
fun fn_wrapper(fni: Expression.IdentifierRef): Expression.Function {
    val fnt = fni.type as Type.FnType
    var i = 0

    val garbage = mutableMapOf<Int, Pattern.BinderPattern>()
    fun patternFromType(type: Type): Pattern = when(type) {
        is Type.PrimitiveType -> {
            val g = Pattern.BinderPattern("_internal_garbage_${i}")
            g.set_type(type)
            garbage[i++] = g
            g
        }
        is Type.RecordType -> TODO()
        is Type.TupleType -> Pattern.ListPattern(type.elements.map {
            val g = Pattern.BinderPattern("_internal_garbage_${i}")
            g.set_type(it)
            garbage[i++] = g
            g
        }).also { it.set_type(type) }
        is Type.ArrayType -> TODO()
        is Type.EnumType -> TODO()
        is Type.NominalType -> TODO()
        is Type.FnType -> TODO()
        is Type.TypeParam -> TODO()
        Type.Top -> TODO()
    }

    fun plsNo(n: Int, t: Type): Expression.IdentifierRef {
        return Expression.IdentifierRef(BindPoint.new("_internal_garbage_${n}").also { it.resolved_ = TermLocation.BinderRef(garbage[n]!!) }).also { it.set_type(t) }
    }

    var j = 0
    fun exprFromType(type: Type): Expression = when(type) {
        is Type.PrimitiveType -> plsNo(j++, type)
        is Type.RecordType -> TODO()
        is Type.TupleType -> Expression.ListExpression(type.elements.map { plsNo(j++, it) }).also { it.set_type(type) }
        is Type.ArrayType -> TODO()
        is Type.EnumType -> TODO()
        is Type.NominalType -> TODO()
        is Type.FnType -> TODO()
        is Type.TypeParam -> TODO()
        Type.Top -> TODO()
    }

    val exp = Expression.Function(patternFromType(fnt.dom), Expression.Invocation(fni, exprFromType(fnt.dom)).also { it.set_type(fnt.codom) })
    exp.set_type(fnt)
    return exp
}