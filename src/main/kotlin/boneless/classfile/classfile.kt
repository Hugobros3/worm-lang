package boneless.classfile

data class JavaVersion(val major: Int, val minor: Int)

data class ClassFile(
    val version: JavaVersion,
    val constantPool: List<ConstantPoolEntry>,
    val accessFlags: ClassAccessFlags,
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
    val cpe = constantPool[index].data as? ConstantPoolData.Utf8Info ?: throw Exception("Malformed class")
    return cpe.string
}

fun resolveClassNameUsingCP(constantPool: List<ConstantPoolEntry>, index: Int): String {
    val cpe = constantPool[index].data as? ConstantPoolData.ClassInfo ?: throw Exception("Malformed class")
    return resolveNameUsingCP(constantPool, cpe.name_index.toInt())
}

data class ClassAccessFlags(
    val acc_public: Boolean,
    val acc_final: Boolean,
    val acc_super: Boolean,
    /** The secret sauce */
    val acc_value_type: Boolean,
    val acc_interface: Boolean,
    val acc_abstract: Boolean,
    val acc_synthetic: Boolean,
    val acc_annotation: Boolean,
    val acc_enum: Boolean,
    val acc_module: Boolean,
)

/** Used for slot zero of the CP */
val dummyCPEntry = ConstantPoolEntry(/*ConstantPoolTag.Dummy, */ConstantPoolData.Dummy)
data class ConstantPoolEntry(/*val tag: ConstantPoolTag, */val data: ConstantPoolData)
enum class ConstantPoolTag(val tagByte: Int) {
    /** Not a real tag defined by the JVM, used for slot zero */
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

sealed class ConstantPoolData {
    object Dummy : ConstantPoolData()

    data class ClassInfo(val name_index: Short): ConstantPoolData()

    data class FieldRefInfo(val class_index: Short, val name_and_type_index: Short): ConstantPoolData()
    data class MethodRefInfo(val class_index: Short, val name_and_type_index: Short): ConstantPoolData()
    data class InterfaceRefInfo(val class_index: Short, val name_and_type_index: Short): ConstantPoolData()

    data class StringInfo(val string_index: Short): ConstantPoolData()

    data class IntegerInfo(val int: Int): ConstantPoolData()
    data class FloatInfo(val float: Float): ConstantPoolData()

    data class LongInfo(val long: Long): ConstantPoolData()
    data class DoubleInfo(val double: Double): ConstantPoolData()

    data class NameAndTypeInfo(val name_index: Short, val descriptor_index: Short): ConstantPoolData()
    data class Utf8Info(val string: String): ConstantPoolData()

    data class MethodHandleInfo(val reference_kind: Byte, val reference_index: Short): ConstantPoolData()
    data class MethodTypeInfo(val descriptor_index: Short): ConstantPoolData()

    data class DynamicInfo(val boostrap_method_attr_index: Short, val name_and_type_index: Short): ConstantPoolData()
    data class InvokeDynamicInfo(val boostrap_method_attr_index: Short, val name_and_type_index: Short): ConstantPoolData()

    data class ModuleInfo(val name_index: Short): ConstantPoolData()
    data class PackageInfo(val name_index: Short): ConstantPoolData()
}

data class FieldInfo(val access_flags: FieldAccessFlags, val name_index: Short, val descriptor_index: Short, val attributes: List<AttributeInfo>)

data class FieldAccessFlags(
    val acc_public: Boolean,
    val acc_private: Boolean,
    val acc_protected: Boolean,
    val acc_static: Boolean,
    val acc_final: Boolean,
    val acc_volatile: Boolean,
    val acc_transient: Boolean,
    val acc_synthetic: Boolean,
    val acc_enum: Boolean,
)

data class MethodInfo(val access_flags: MethodAccessFlags, val name_index: Short, val descriptor_index: Short, val attributes: List<AttributeInfo>)
data class MethodAccessFlags(
    val acc_public: Boolean,
    val acc_private: Boolean,
    val acc_protected: Boolean,
    val acc_static: Boolean,
    val acc_final: Boolean,
    val acc_synchronized: Boolean,
    val acc_bridge: Boolean,
    val acc_varargs: Boolean,
    val acc_native: Boolean,
    val acc_abstract: Boolean,
    val acc_strict: Boolean,
    val acc_synthetic: Boolean,
)

data class AttributeInfo(val attribute_name_index: Short, val uninterpreted: ByteArray?, val interpreted: Attribute?)

sealed class Attribute {
    val serializedName: String
        get() = javaClass.simpleName

    data class ConstantValue(val constant_value_index: Short): Attribute()
    data class Code(val max_stack: Short, val max_locals: Short, val code: ByteArray, val exception_table: List<ExceptionTableEntry>, val attributes: List<AttributeInfo>): Attribute() {
        data class ExceptionTableEntry(val start_pc: Short, val end_pc: Short, val handler_pc: Short, val catch_type: Short)
    }
    data class StackMapTable(val entries: List<StackMapFrame>): Attribute() {
        sealed class StackMapFrame {
            data class SameFrame(override val offset: Int): StackMapFrame()
            data class SameLocals1StackItemFrame(override val offset: Int, val newStackElement: VerificationType): StackMapFrame()
            data class ChopFrame(override val offset: Int, val k: Int): StackMapFrame()
            data class AppendFrame(override val offset: Int, val newLocals: List<VerificationType>): StackMapFrame()
            data class FullFrame(override val offset: Int, val locals: List<VerificationType>, val stack: List<VerificationType>): StackMapFrame()

            abstract val offset: Int
        }
    }

    fun wrap(cfb: ClassFileBuilder) = AttributeInfo(cfb.constantUTF(this.serializedName), interpreted = this, uninterpreted = null)
}

fun ClassFile.dump() {
    println("Class $name")
    println(accessFlags)
    var cpe = 0
    for (entry in constantPool) {
        println("entry ${cpe++} $entry")
    }
    println("Methods:")
    fun dumpAttribute(of: String, attribute: AttributeInfo) {
        println("$of attribute " + attribute.name + " :")
        when (attribute.interpreted) {
            is Attribute.Code -> {
                dump_bytecode(attribute.interpreted.code, constantPool)
                for (a in attribute.interpreted.attributes) {
                    dumpAttribute("code", a)
                }
            }
            else -> println(attribute.interpreted)
        }
        if (attribute.uninterpreted != null) {
            println(attribute.uninterpreted)
        }
    }

    for (attribute in attributes)
        dumpAttribute("class", attribute)

    for (field in fields) {
        println("Field: " + field.name)
        for (attribute in field.attributes) {
            dumpAttribute("field", attribute)
        }
    }

    for (method in methods) {
        println("Method: " + method.name)
        for (attribute in method.attributes) {
            dumpAttribute("method", attribute)
        }
    }
}