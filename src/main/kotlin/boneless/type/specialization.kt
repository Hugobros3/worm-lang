package boneless.type

import boneless.Expression
import boneless.bind.TermLocation
import boneless.bind.get_def
import boneless.util.prettyPrint

fun specializeType(type: Type, substitutions: Map<Type, Type>): Type = when {
    substitutions.contains(type) -> substitutions[type]!!
    type is Type.RecordType -> type.copy(elements = type.elements.map { (n, t) -> Pair(n, specializeType(t, substitutions)) })
    type is Type.TupleType -> type.copy(elements = type.elements.map { specializeType(it, substitutions) })
    type is Type.ArrayType -> type.copy(elementType = specializeType(type.elementType, substitutions))
    type is Type.EnumType -> type.copy(elements = type.elements.map { (n, t) -> Pair(n, specializeType(t, substitutions)) })
    type is Type.FnType -> type.copy(dom = specializeType(type.dom, substitutions), codom = specializeType(type.codom, substitutions))
    type is Type.NominalType -> type.copy(name = "${type.name}$${substitutions.map { (k, v) -> "${k.prettyPrint()} -> ${v.prettyPrint()}" }}", dataType = specializeType(type.dataType, substitutions))
    type is Type.PrimitiveType ||
    type is Type.TypeParam -> type
    else -> throw Exception("Unhandled $type")
}

fun findTypeParams(type: Type): List<TermLocation.TypeParamRef> = when(type) {
    is Type.PrimitiveType -> emptyList()
    is Type.RecordType -> type.elements.flatMap { findTypeParams(it.second) }
    is Type.TupleType -> type.elements.flatMap { findTypeParams(it) }
    is Type.ArrayType -> findTypeParams(type.elementType)
    is Type.EnumType -> type.elements.flatMap { findTypeParams(it.second) }
    is Type.NominalType -> findTypeParams(type.dataType)
    is Type.FnType -> findTypeParams(type.dom) + findTypeParams(type.codom)
    is Type.TypeParam -> listOf(type.bound)
    is Type.Top -> emptyList()
}

/** Heuristic used to auto-infer type params in certain situations */
fun needTypeParamInference(expr: Expression): Boolean = when(expr) {
    is Expression.QuoteLiteral -> false
    is Expression.QuoteType -> TODO()
    is Expression.IdentifierRef -> {
        val def = get_def(expr.id.resolved)
        def?.typeParamsNames?.isNotEmpty() ?: false
    }
    is Expression.ExprSpecialization -> false
    is Expression.Projection -> needTypeParamInference(expr.expression)
    else -> false
}

fun idk(expr: Expression, target: Expression): Map<TermLocation.TypeParamRef, Type> = when {
    expr is Expression.QuoteLiteral -> TODO()
    expr is Expression.QuoteType -> TODO()
    expr is Expression.IdentifierRef -> TODO()
    expr is Expression.ExprSpecialization -> TODO()
    expr is Expression.ListExpression -> TODO()
    expr is Expression.RecordExpression -> TODO()
    expr is Expression.Projection -> TODO()
    expr is Expression.Invocation -> TODO()
    expr is Expression.Function -> TODO()
    expr is Expression.Ascription -> TODO()
    expr is Expression.Cast -> TODO()
    expr is Expression.Sequence -> TODO()
    expr is Expression.Conditional -> TODO()
    expr is Expression.WhileLoop -> TODO()
    else -> TODO()
}

fun unify(type: Type, expected: Type): Map<Type, Type> = when {
    expected is Type.Top -> emptyMap()
    type is Type.TypeParam -> mapOf(type to expected)
    type is Type.FnType && expected is Type.FnType -> unifyConstraints(unify(type.dom, expected.dom), unify(type.codom, expected.codom))
    type is Type.TupleType && expected is Type.TupleType -> type.elements.zip(expected.elements).map { (l, r) -> unify(l, r) }.fold(emptyMap(), ::unifyConstraints)
    else -> TODO()
    /*type is Type.PrimitiveType -> TODO()
    type is Type.RecordType -> TODO()
    type is Type.ArrayType -> TODO()
    type is Type.EnumType -> TODO()
    type is Type.NominalType -> TODO()
    type == Type.Top -> TODO()*/
}

fun unifyConstraints(a: Map<Type, Type>, b: Map<Type, Type>): MutableMap<Type, Type> {
    val m = mutableMapOf<Type, Type>()
    for (c in a.toList() + b.toList()) {
        val (from, to) = c
        if (m[from] != null && m[from] != to)
            throw Exception("Can't unify")
        m[from] = to
    }
    return m
}