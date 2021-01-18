package boneless.classfile

import boneless.classfile.ConstantPoolEntryInfo.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClassFileReader(val file: File) {
    val bytes = file.readBytes()
    var pos = 0

    fun expectByte(byte: Int) {
        val byte = byte.toByte()
        if (bytes[pos] != byte)
            throw Exception("Expected byte $byte at $pos")
        pos++
    }

    fun expectBytes(vararg bytes: Int) {
        for (byte in bytes)
            expectByte(byte)
    }

    fun readByte(): Byte {
        return bytes[pos++]
    }

    fun readBytes(n: Int): ByteArray {
        val arr = ByteArray(n)
        for (i in 0 until n) {
            arr[i] = readByte()
        }
        return arr
    }

    fun readShort(): Short {
        val b1 = readByte()
        val b2 = readByte()
        val s = b1.toInt() shl 8 or b2.toInt()
        return s.toShort()
    }

    fun readInt(): Int {
        val b1 = readByte().toInt() and 0xFF
        val b2 = readByte().toInt() and 0xFF
        val b3 = readByte().toInt() and 0xFF
        val b4 = readByte().toInt() and 0xFF
        val s = (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        return s
    }

    fun readLong(): Long {
        val b1 = readByte().toLong()
        val b2 = readByte().toLong()
        val b3 = readByte().toLong()
        val b4 = readByte().toLong()
        val b5 = readByte().toLong()
        val b6 = readByte().toLong()
        val b7 = readByte().toLong()
        val b8 = readByte().toLong()
        val s = (b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8
        return s
    }

    fun readFloat(): Float {
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val b4 = readByte()
        val buf = ByteBuffer.wrap(byteArrayOf(b1, b2, b3, b4))
        buf.order(ByteOrder.BIG_ENDIAN)
        val fb = buf.asFloatBuffer()
        fb.position(0)
        return fb.get(0)
    }

    fun readDouble(): Double {
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val b4 = readByte()
        val b5 = readByte()
        val b6 = readByte()
        val b7 = readByte()
        val b8 = readByte()
        val buf = ByteBuffer.wrap(byteArrayOf(b1, b2, b3, b4, b5, b6, b7, b8))
        buf.order(ByteOrder.BIG_ENDIAN)
        val fb = buf.asDoubleBuffer()
        fb.position(0)
        return fb.get(0)
    }

    fun <A> readN(n: Int, f: () -> A): List<A> {
        val list = mutableListOf<A>()
        for (i in 0 until n)
            list += f()
        return list
    }

    internal fun read(): ClassFile {
        expectBytes(0xCA, 0xFE, 0xBA, 0xBE)
        val minor = readShort()
        val major = readShort()
        val constant_pool_count = readShort()
        //println(minor)
        //println(major)
        //println(constant_pool_count)

        var cpe = 1
        val constant_pool = listOf(ConstantPoolEntry(CpInfoTags.Dummy, ConstantPoolEntryInfo.Dummy)) + (readN(constant_pool_count.toInt() - 1) {
            val entry = readCpInfo()
            //println("entry ${cpe++} $entry")
            entry
        })
        //println("read cpool !")

        val access_flags = readShort()
        val this_class = readShort()
        val super_class = readShort()

        val interface_count = readShort()
        val interfaces = readN(interface_count.toInt()) { readShort() }
        //println("read $interface_count interfaces !")

        val field_count = readShort()
        val fields = readN(field_count.toInt()) { readFieldInfo(constant_pool) }
        //println(fields)
        //println("read $field_count fields !")

        val method_count = readShort()
        val methods = readN(method_count.toInt()) { readMethodInfo(constant_pool) }
        //println(methods)
        //println("read $method_count methods !")

        val attributes = readAttributes(constant_pool)

        assert(pos == bytes.size)

        return ClassFile(JavaVersion(major.toInt(), minor.toInt()), constant_pool, readClassAccesFlags(access_flags), this_class, super_class, interfaces, fields, methods, attributes)
    }

    fun readAttributes(cp: List<ConstantPoolEntry>): List<AttributeInfo> {
        val attributes_count = readShort()
        val attributes = readN(attributes_count.toInt()) { readAttributeInfo(cp) }
        //println(attributes)
        //println("read $attributes_count attributes !")
        return attributes
    }

    fun readCpInfo(): ConstantPoolEntry {
        val tagByte = readByte().toInt()
        val tag = CpInfoTags.values().find { it.tagByte == tagByte } ?: throw Exception("Unknown constant pool entry tag: $tagByte")
        val info = when (tag) {
            CpInfoTags.Class -> ClassInfo(readShort())
            CpInfoTags.FieldRef -> FieldRefInfo(readShort(), readShort())
            CpInfoTags.MethodRef -> MethodRefInfo(readShort(), readShort())
            CpInfoTags.InterfaceMethodRef -> InterfaceRefInfo(readShort(), readShort())
            CpInfoTags.String -> StringInfo(readShort())
            CpInfoTags.Integer -> IntegerInfo(readInt())
            CpInfoTags.Float -> FloatInfo(readFloat())
            CpInfoTags.Long -> LongInfo(readLong())
            CpInfoTags.Double -> DoubleInfo(readDouble())
            CpInfoTags.NameAndType -> NameAndTypeInfo(readShort(), readShort())
            CpInfoTags.Utf8 -> {
                val length = readShort().toInt()
                val bytes = readBytes(length)
                Utf8Info(String(bytes))
            }
            CpInfoTags.MethodHandle -> MethodHandleInfo(readByte(), readShort())
            CpInfoTags.MethodType -> MethodTypeInfo(readShort())
            CpInfoTags.Dynamic -> DynamicInfo(readShort(), readShort())
            CpInfoTags.InvokeDynamic -> InvokeDynamicInfo(readShort(), readShort())
            CpInfoTags.Module -> ModuleInfo(readShort())
            CpInfoTags.Package -> PackageInfo(readShort())
            CpInfoTags.Dummy -> throw Exception("lol no")
        }
        return ConstantPoolEntry(tag, info)
    }

    fun readFieldInfo(cp: List<ConstantPoolEntry>): FieldInfo {
        val access_flags = readShort()
        val name_index = readShort()
        val descriptor_index = readShort()

        return FieldInfo(readFieldAccesFlags(access_flags), name_index, descriptor_index, readAttributes(cp))
    }

    fun readMethodInfo(cp: List<ConstantPoolEntry>): MethodInfo {
        val access_flags = readShort()
        val name_index = readShort()
        val descriptor_index = readShort()

        return MethodInfo(readMethodAccesFlags(access_flags), name_index, descriptor_index, readAttributes(cp))
    }

    fun readAttributeInfo(cp: List<ConstantPoolEntry>): AttributeInfo {
        val attribute_name_index = readShort()
        val attribute_length = readInt()
        var info: ByteArray? = null
        val interp: Attribute? = when(resolveNameUsingCP(cp, attribute_name_index.toInt())) {
            "Code" -> {
                val max_stack = readShort()
                val max_locals = readShort()
                val code_length = readInt()
                val code = readBytes(code_length)
                val exception_table_length = readShort()
                val exceptions = readN(exception_table_length.toInt()) {
                    Attribute.Code.ExceptionTableEntry(readShort(), readShort(), readShort(), readShort())
                }
                val attributes = readAttributes(cp)
                Attribute.Code(max_stack, max_locals, code, exceptions, attributes)
            }
            else -> {
                info = readBytes(attribute_length)
                null
            }
        }
        return AttributeInfo(attribute_name_index, info, interp)
    }
}

fun readClassAccesFlags(flags: Short): ClassAccessFlags {
    fun bit(m: Int) = (flags.toInt() and m) != 0
    return ClassAccessFlags(
        acc_public = bit(0x0001),
        acc_final = bit(0x0010),
        acc_super = bit(0x0020),
        acc_interface = bit(0x0200),
        acc_abstract = bit(0x0400),
        acc_synthetic = bit(0x1000),
        acc_annotation = bit(0x2000),
        acc_enum = bit(0x4000),
        acc_module = bit(0x8000),
    )
}

fun readFieldAccesFlags(flags: Short): FieldAccessFlags {
    fun bit(m: Int) = (flags.toInt() and m) != 0
    return FieldAccessFlags(
        acc_public = bit(0x0001),
        acc_private = bit(0x0002),
        acc_protected = bit(0x0004),
        acc_static = bit(0x0008),
        acc_final = bit(0x0010),
        acc_volatile = bit(0x0040),
        acc_transient = bit(0x0080),
        acc_synthetic = bit(0x1000),
        acc_enum = bit(0x4000),
    )
}

fun readMethodAccesFlags(flags: Short): MethodAccessFlags {
    fun bit(m: Int) = (flags.toInt() and m) != 0
    return MethodAccessFlags(
        acc_public = bit(0x0001),
        acc_private = bit(0x0002),
        acc_protected = bit(0x0004),
        acc_static = bit(0x0008),
        acc_final = bit(0x0010),
        acc_synchronized = bit(0x0020),
        acc_bridge = bit(0x0040),
        acc_varargs = bit(0x0080),
        acc_native = bit(0x0100),
        acc_abstract = bit(0x0400),
        acc_strict = bit(0x0800),
        acc_synthetic = bit(0x1000),
    )
}

fun readClassfile(file: File) = ClassFileReader(file).read()