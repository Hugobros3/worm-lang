package boneless.type

import boneless.core.BuiltinFn
import boneless.Identifier
import boneless.bind.TermLocation
import boneless.util.prettyPrint

/** Actual types, not AST type expressions */
sealed class Type {
    data class PrimitiveType(val primitiveType: PrimitiveTypeEnum) : Type()
    data class RecordType(val elements: List<Pair<Identifier, Type>>) : Type()
    data class TupleType(val elements: List<Type>) : Type() {
        val isUnit: Boolean get() = elements.isEmpty()
    }
    data class ArrayType(val elementType: Type, val size: Int) : Type() {
        val isDefinite: Boolean get() = size != 0
    }
    data class EnumType(val elements: List<Pair<Identifier, Type>>) : Type()
    data class NominalType(val name: Identifier, val dataType: Type): Type()
    data class FnType(val dom: Type, val codom: Type, val constructorFor: NominalType? = null, val builtin: BuiltinFn? = null) : Type()
    data class TypeParam(val bound: TermLocation.TypeParamRef) : Type()

    /** Used for mutable local variables */
    data class Mut(val elementType: Type): Type() { fun check() = assert(elementType !is Mut) }
    object Top : Type()
}

fun remove_mut(type: Type): Type = when (type) {
    is Type.Mut -> type.elementType
    is Type.ArrayType -> type.copy(elementType = remove_mut(type.elementType))
    is Type.RecordType -> type.copy(elements = type.elements.map { (n, t) -> Pair(n, remove_mut(t)) })
    is Type.TupleType -> type.copy(elements = type.elements.map { remove_mut(it) })
    is Type.EnumType -> type.copy(elements = type.elements.map { (n, t) -> Pair(n, remove_mut(t)) })
    is Type.FnType -> type.copy(dom = remove_mut(type.dom), codom = remove_mut(type.codom))
    is Type.NominalType -> type
    Type.Top,
    is Type.PrimitiveType,
    is Type.TypeParam -> type
    else -> type
}

fun assert_no_mut(type: Type): Boolean = when (type) {
    is Type.Mut -> throw Exception("Fail!")
    is Type.ArrayType -> assert_no_mut(type.elementType)
    is Type.RecordType -> { type.elements.forEach { (_, t) -> assert_no_mut(t) } ; false }
    is Type.TupleType -> { type.elements.forEach { t -> assert_no_mut(t) } ; false }
    is Type.EnumType -> { type.elements.forEach { (_, t) -> assert_no_mut(t) } ; false }
    is Type.FnType -> { assert_no_mut(type.dom) ; assert_no_mut(type.codom)}
    is Type.NominalType -> assert_no_mut(type.dataType)
    Type.Top,
    is Type.PrimitiveType,
    is Type.TypeParam -> { false }
}

fun unit_type() = Type.TupleType(emptyList())