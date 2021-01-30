package boneless.type

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