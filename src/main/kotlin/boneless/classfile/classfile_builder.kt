package boneless.classfile

val defaultClassAccessFlags = ClassAccessFlags(
    acc_public = true,
    acc_final = true,
    acc_super = true,
    acc_value_type = false,
    acc_interface = false,
    acc_abstract = false,
    acc_synthetic = true,
    acc_annotation = false,
    acc_enum = false,
    acc_module = false,
)

val defaulMethodAccessFlags = MethodAccessFlags(
    acc_public = true,
    acc_private = false,
    acc_protected = false,
    acc_static = false,
    acc_final = false,
    acc_synchronized = false,
    acc_bridge = false,
    acc_varargs = false,
    acc_native = false,
    acc_abstract = false,
    acc_strict = false,
    acc_synthetic = false,
)

val defaultFieldAccessFlags = FieldAccessFlags(
    acc_public = true,
    acc_private = false,
    acc_protected = false,
    acc_static = false,
    acc_final = false,
    acc_synthetic = false,
    acc_enum = false,
    acc_transient = false,
    acc_volatile = false,
)

val lw2jvm = JavaVersion(major = 58, minor = 0)

class ClassFileBuilder(val version: JavaVersion = lw2jvm, val className: String, val accessFlags: ClassAccessFlags, val superName: String = "java.lang.Object") {
    val constantPool = mutableListOf<ConstantPoolEntry>(dummyCPEntry)
    val methods = mutableListOf<MethodInfo>()
    val fields = mutableListOf<FieldInfo>()

    inline fun <reified E: ConstantPoolData> findOrPutInCPool(check: (E) -> Boolean, create: () -> E): Short {
        for ((i, entry) in constantPool.withIndex()) {
            if (entry.data is E) {
                if (check(entry.data)) {
                    return i.toShort()
                }
            }
        }
        constantPool.add(ConstantPoolEntry(create()))
        return (constantPool.size - 1).toShort()
    }

    fun constantUTF(text: String) = findOrPutInCPool({ it.string == text }){ ConstantPoolData.Utf8Info(text)}
    fun constantClass(className: String): Short {
        val index = constantUTF(className.replace(".", "/"))
        return findOrPutInCPool({ it.name_index == index }){ ConstantPoolData.ClassInfo(index)}
    }
    private fun constantNameAndType(fieldName: String, descriptor: String): Short {
        val fieldIndex = constantUTF(fieldName)
        val descIndex = constantUTF(descriptor)
        return findOrPutInCPool({ it.name_index == fieldIndex && it.descriptor_index == descIndex }){ConstantPoolData.NameAndTypeInfo(fieldIndex, descIndex)}
    }
    fun constantFieldRef(className: String, fieldName: String, fieldDescriptor: FieldDescriptor): Short {
        val classIndex = constantClass(className)
        val nameAndTypeIndex = constantNameAndType(fieldName, fieldDescriptor.toString())
        return findOrPutInCPool({ it.class_index == classIndex && it.name_and_type_index == nameAndTypeIndex }){ConstantPoolData.FieldRefInfo(classIndex, nameAndTypeIndex)}
    }
    fun constantMethodRef(className: String, fieldName: String, methodDescriptor: MethodDescriptor): Short {
        val classIndex = constantClass(className)
        val nameAndTypeIndex = constantNameAndType(fieldName, methodDescriptor.toString())
        return findOrPutInCPool({ it.class_index == classIndex && it.name_and_type_index == nameAndTypeIndex }){ConstantPoolData.MethodRefInfo(classIndex, nameAndTypeIndex)}
    }

    fun method(methodName: String, descriptor: MethodDescriptor, accessFlags: MethodAccessFlags, code: Attribute.Code) {
        val nameIndex = constantUTF(methodName)
        val descriptor_index = constantUTF(descriptor.toString())
        methods.add(MethodInfo(accessFlags, nameIndex, descriptor_index, listOf(AttributeInfo(constantUTF("Code"), interpreted = code, uninterpreted = null))))
    }

    fun field(fieldName: String, descriptor: FieldDescriptor, accessFlags: FieldAccessFlags) {
        val nameIndex = constantUTF(fieldName)
        val descriptor_index = constantUTF(descriptor.toString())
        fields.add(FieldInfo(accessFlags, nameIndex, descriptor_index, listOf()))
    }

    fun finish(): ClassFile {
        val thisIndex = constantClass(className)
        val superIndex = constantClass(superName)
        return ClassFile(version, constantPool, accessFlags, thisIndex, superIndex, emptyList(), fields, methods, emptyList())
    }
}