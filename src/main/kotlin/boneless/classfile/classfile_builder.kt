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
    acc_static = true,
    acc_final = true,
    acc_synchronized = false,
    acc_bridge = false,
    acc_varargs = false,
    acc_native = false,
    acc_abstract = false,
    acc_strict = false,
    acc_synthetic = false,
)

val lw2jvm = JavaVersion(minor = 0, major = 58)

class ClassFileBuilder(val version: JavaVersion = lw2jvm, val className: String, val superName: String = "java.lang.Object") {
    val constantPool = mutableListOf<ConstantPoolEntry>(dummyCPEntry)
    val methods = mutableListOf<MethodInfo>()

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

    fun staticMethod(methodName: String, descriptor: String, code: Attribute.Code) {
        val nameIndex = constantUTF(methodName)
        val descriptor_index = constantUTF(descriptor)
        methods.add(MethodInfo(defaulMethodAccessFlags, nameIndex, descriptor_index, listOf(AttributeInfo(constantUTF("Code"), interpreted = code, uninterpreted = null))))
    }

    fun finish(): ClassFile {
        val thisIndex = constantClass(className)
        val superIndex = constantClass(superName)
        return ClassFile(version, constantPool, defaultClassAccessFlags, thisIndex, superIndex, emptyList(), emptyList(), methods, emptyList())
    }
}