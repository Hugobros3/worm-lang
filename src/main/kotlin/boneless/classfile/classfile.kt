package boneless.classfile

data class JavaVersion(val major: Int, val minor: Int)

data class ClassFile(
    val version: JavaVersion,
    val constantPool: List<ConstantPoolEntry>,
    val accessFlags: Short,
    val thisClass: Short,
    val superClass: Short,
    val interfaces: List<Short>,
    val fields: List<FieldInfo>,
    val methods: List<MethodInfo>,
    val attributes: List<AttributeInfo>
)

data class ConstantPoolEntry(val tag: CpInfoTags, val info: ConstantPoolEntryInfo)
enum class CpInfoTags(val tagByte: Int) {
    Class(7),

    FieldRef(9),
    MethodRef(10),
    InterfaceMethodRef(11),

    String(8),
    Integer(3),
    Float(4),
    Long(5),
    Double(6),
    NameAndType(12),
    Utf8(1),
    MethodHandle(15),
    MethodType(16),
    Dynamic(17),
    InvokeDynamic(18),
    Module(19),
    Package(20),
}

sealed class ConstantPoolEntryInfo {
    data class ClassInfo(val name_index: Short): ConstantPoolEntryInfo()

    data class FieldRefInfo(val class_index: Short, val name_and_type_index: Short): ConstantPoolEntryInfo()
    data class MethodRefInfo(val class_index: Short, val name_and_type_index: Short): ConstantPoolEntryInfo()
    data class InterfaceRefInfo(val class_index: Short, val name_and_type_index: Short): ConstantPoolEntryInfo()

    data class StringInfo(val string_index: Short): ConstantPoolEntryInfo()

    data class IntegerInfo(val int: Int): ConstantPoolEntryInfo()
    data class FloatInfo(val float: Float): ConstantPoolEntryInfo()

    data class LongInfo(val long: Long): ConstantPoolEntryInfo()
    data class DoubleInfo(val double: Double): ConstantPoolEntryInfo()

    data class NameAndTypeInfo(val name_index: Short, val descriptor_index: Short): ConstantPoolEntryInfo()
    data class Utf8Info(val string: String): ConstantPoolEntryInfo()

    data class MethodHandleInfo(val reference_kind: Byte, val reference_index: Short): ConstantPoolEntryInfo()
    data class MethodTypeInfo(val descriptor_index: Short): ConstantPoolEntryInfo()

    data class DynamicInfo(val boostrap_method_attr_index: Short, val name_and_type_index: Short): ConstantPoolEntryInfo()
    data class InvokeDynamicInfo(val boostrap_method_attr_index: Short, val name_and_type_index: Short): ConstantPoolEntryInfo()

    data class ModuleInfo(val name_index: Short): ConstantPoolEntryInfo()
    data class PackageInfo(val name_index: Short): ConstantPoolEntryInfo()
}

data class FieldInfo(val access_flags: Short, val name_index: Short, val descriptor_index: Short, val attributes: List<AttributeInfo>)
data class MethodInfo(val access_flags: Short, val name_index: Short, val descriptor_index: Short, val attributes: List<AttributeInfo>)

data class AttributeInfo(val attribute_name_index: Short, val info: ByteArray)
