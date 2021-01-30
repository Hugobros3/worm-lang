package boneless.type

import boneless.core.BuiltinFn
import boneless.Identifier

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
    data class TypeParam(val identifier: Identifier) : Type()
}

fun unit_type() = Type.TupleType(emptyList())