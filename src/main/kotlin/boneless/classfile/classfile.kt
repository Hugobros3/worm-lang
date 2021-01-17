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
) {
    val MethodInfo.name: String
        get() = resolveNameUsingCP(constantPool, name_index.toInt())
    val FieldInfo.name: String
        get() = resolveNameUsingCP(constantPool, name_index.toInt())
    val AttributeInfo.name: String
        get() = resolveNameUsingCP(constantPool, attribute_name_index.toInt())

    val name: String
        get() = resolveClassNameUsingCP(constantPool, thisClass.toInt())
}

fun resolveNameUsingCP(constantPool: List<ConstantPoolEntry>, index: Int): String {
    val cpe = constantPool[index].info as? ConstantPoolEntryInfo.Utf8Info ?: throw Exception("Malformed class")
    return cpe.string
}

fun resolveClassNameUsingCP(constantPool: List<ConstantPoolEntry>, index: Int): String {
    val cpe = constantPool[index].info as? ConstantPoolEntryInfo.ClassInfo ?: throw Exception("Malformed class")
    return resolveNameUsingCP(constantPool, cpe.name_index.toInt())
}

data class ConstantPoolEntry(val tag: CpInfoTags, val info: ConstantPoolEntryInfo)
enum class CpInfoTags(val tagByte: Int) {
    Dummy(0xDEADBEEF.toInt()),

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
    object Dummy : ConstantPoolEntryInfo()

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

data class AttributeInfo(val attribute_name_index: Short, val uninterpreted: ByteArray?, val interpreted: Attribute?)

sealed class Attribute {
    data class ConstantValue(val constant_value_index: Short): Attribute()
    data class Code(val max_stack: Short, val max_locals: Short, val code: ByteArray, val exception_table: List<ExceptionTableEntry>, val attributes: List<AttributeInfo>): Attribute() {
        data class ExceptionTableEntry(val start_pc: Short, val end_pc: Short, val handler_pc: Short, val catch_type: Short)
    }
}

fun ClassFile.dump() {
    println("Class $name")
    println("Methods:")
    fun dumpAttribute(attribute: AttributeInfo) {
        println("attribute " + attribute.name + " :")
        when (attribute.interpreted) {
            is Attribute.Code -> {
                dump_bytecode(attribute.interpreted.code, constantPool)
            }
        }
        if (attribute.uninterpreted != null) {
            println(attribute.uninterpreted)
        }
    }

    for (attribute in attributes)
        dumpAttribute(attribute)

    for (field in fields) {
        println("Field: " + field.name)
        for (attribute in field.attributes) {
            dumpAttribute(attribute)
        }
    }

    for (method in methods) {
        println("Method: " + method.name)
        for (attribute in method.attributes) {
            dumpAttribute(attribute)
        }
    }
}