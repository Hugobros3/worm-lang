package boneless.type

import boneless.Expression
import boneless.bind.TermLocation
import boneless.bind.get_def
import boneless.util.prettyPrint

fun specializeType(type: Type, substitutions: Map<Type.TypeParam, Type>): Type = when {
    substitutions.contains(type) -> substitutions[type]!!
    type is Type.RecordType -> type.copy(elements = type.elements.map { (n, t) -> Pair(n, specializeType(t, substitutions)) })
    type is Type.TupleType -> type.copy(elements = type.elements.map { specializeType(it, substitutions) })
    type is Type.ArrayType -> type.copy(elementType = specializeType(type.elementType, substitutions))
    type is Type.EnumType -> type.copy(elements = type.elements.map { (n, t) -> Pair(n, specializeType(t, substitutions)) })
    type is Type.FnType -> type.copy(dom = specializeType(type.dom, substitutions), codom = specializeType(type.codom, substitutions))
    type is Type.NominalType -> type.copy(name = "${type.name}$${substitutions.map { (k, v) -> "${k.prettyPrint()} -> ${v.prettyPrint()}" }}", dataType = specializeType(type.dataType, substitutions))
    type is Type.PrimitiveType ||
    type is Type.TypeParam -> type
    type is Type.Mut -> type.copy(elementType = specializeType(type.elementType, substitutions))
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
    is Type.Mut -> findTypeParams(type.elementType)
    is Type.Top -> emptyList()
}

// assumes 'expected' does not feature type parameters itself
fun unify(type: Type, expected: Type, invertSubtypingRelation: Boolean): Map<Type.TypeParam, Type> {
    assert(findTypeParams(expected).isEmpty())
    return unify_(type, expected, invertSubtypingRelation)
}

private fun unify_(type: Type, expected: Type, invertSubtypingRelation: Boolean): Map<Type.TypeParam, Type> {
    return when {
        isSubtype(expected, type) && invertSubtypingRelation -> emptyMap()
        isSubtype(type, expected) && !invertSubtypingRelation -> emptyMap()
        type is Type.TypeParam -> mapOf(type to expected)
        type is Type.FnType && expected is Type.FnType -> mergeConstraints(
            unify(type.dom, expected.dom, true),
            unify(type.codom, expected.codom, false)
        )
        type is Type.TupleType && expected is Type.TupleType -> type.elements.zip(expected.elements)
            .map { (l, r) -> unify(l, r, invertSubtypingRelation) }.fold(emptyMap(), ::mergeConstraints)
        else -> TODO()
        /*type is Type.PrimitiveType -> TODO()
        type is Type.RecordType -> TODO()
        type is Type.ArrayType -> TODO()
        type is Type.EnumType -> TODO()
        type is Type.NominalType -> TODO()*/
    }
}

fun mergeConstraints(a: Map<Type.TypeParam, Type>, b: Map<Type.TypeParam, Type>): MutableMap<Type.TypeParam, Type> {
    val merged = mutableMapOf<Type.TypeParam, Type>()
    for (constraint in a.toList() + b.toList()) {
        val (from, to) = constraint
        if (merged[from] != null && merged[from] != to)
            throw Exception("Can't unify " + merged[from] + " and " + to)
        merged[from] = to
    }
    return merged
}

fun create_visitor_for_unification_constraints(constraints: Map<Type.TypeParam, Type>) = default_visit_all_typeable_visitor.copy(
    exprVisitor = {
        if (it is Expression.IdentifierRef) {
            val def = get_def(it)
            if (def != null) {
                if (def.typeParams.isNotEmpty()) {
                    assert(it.deducedImplicitSpecializationArguments == null)
                    it.deducedImplicitSpecializationArguments2 = def.typeParams.map { constraints[it]!! }
                    it.deducedImplicitSpecializationArguments = constraints.filter { it.key in def.typeParams }
                }
            }
        }
        true
    },
    // TODO implicit specialization for typeExprs & patterns
)