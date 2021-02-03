package boneless.classfile

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class ClassFileWriter(val classFile: ClassFile, val outputStream: DataOutputStream) {

    fun writeByte(byte: Int) {
        outputStream.write(byte)
    }

    fun writeBytes(vararg bytes: Int) {
        for (byte in bytes)
            writeByte(byte)
    }

    fun writeBytes(bytes: ByteArray) {
        for (byte in bytes)
            writeByte(byte.toInt())
    }

    fun writeShort(v: Int) = outputStream.writeShort(v)
    fun writeShort(v: Short) = outputStream.writeShort(v.toInt())
    fun writeInt(v: Int) = outputStream.writeInt(v)
    fun writeLong(v: Long) = outputStream.writeLong(v)
    fun writeFloat(v: Float) = outputStream.writeFloat(v)
    fun writeDouble(v: Double) = outputStream.writeDouble(v)

    fun write() {
        writeBytes(0xCA, 0xFE, 0xBA, 0xBE)
        writeShort(classFile.version.minor)
        writeShort(classFile.version.major)

        writeShort(classFile.constantPool.size)
        for (entry in classFile.constantPool) {
            writeCpEntry(entry)
        }

        writeClassAccessFlags(classFile.accessFlags)
        writeShort(classFile.thisClass)
        writeShort(classFile.superClass)

        writeShort(classFile.interfaces.size)
        for (int in classFile.interfaces)
            writeShort(int)

        writeShort(classFile.fields.size)
        for (field in classFile.fields) {
            writeField(field)
        }

        writeShort(classFile.methods.size)
        for (m in classFile.methods) {
            writeMethod(m)
        }

        writeAttributes(outputStream, classFile.attributes)
    }

    private fun writeField(field: FieldInfo) {
        writeFieldAccessFlags(field.access_flags)
        writeShort(field.name_index)
        writeShort(field.descriptor_index)
        writeAttributes(outputStream, field.attributes)
    }

    private fun writeMethod(method: MethodInfo) {
        writeMethodAccessFlags(method.access_flags)
        writeShort(method.name_index)
        writeShort(method.descriptor_index)
        writeAttributes(outputStream, method.attributes)
    }

    private fun writeClassAccessFlags(af: ClassAccessFlags) {
        fun bit(mask: Int, b: Boolean) = if (b) mask else 0
        val flags =
            bit(0x0001, af.acc_public) or
                    bit(0x0010, af.acc_final) or
                    bit(0x0020, af.acc_super) or
                    bit(0x0100, af.acc_value_type) or
                    bit(0x0200, af.acc_interface) or
                    bit(0x0400, af.acc_abstract) or
                    bit(0x1000, af.acc_synthetic) or
                    bit(0x2000, af.acc_annotation) or
                    bit(0x4000, af.acc_enum) or
                    bit(0x8000, af.acc_module)
        writeShort(flags)
    }

    private fun writeFieldAccessFlags(af: FieldAccessFlags) {
        fun bit(mask: Int, b: Boolean) = if (b) mask else 0
        val flags =
            bit(0x0001, af.acc_public) or
                    bit(0x0002, af.acc_private) or
                    bit(0x0004, af.acc_protected) or
                    bit(0x0008, af.acc_static) or
                    bit(0x0010, af.acc_final) or
                    bit(0x0040, af.acc_volatile) or
                    bit(0x0080, af.acc_transient) or
                    bit(0x1000, af.acc_synthetic) or
                    bit(0x4000, af.acc_enum)
        writeShort(flags)
    }

    private fun writeMethodAccessFlags(af: MethodAccessFlags) {
        fun bit(mask: Int, b: Boolean) = if (b) mask else 0
        val flags =
            bit(0x0001, af.acc_public) or
                    bit(0x0002, af.acc_private) or
                    bit(0x0004, af.acc_protected) or
                    bit(0x0008, af.acc_static) or
                    bit(0x0010, af.acc_final) or
                    bit(0x0020, af.acc_synchronized) or
                    bit(0x0040, af.acc_bridge) or
                    bit(0x0080, af.acc_varargs) or
                    bit(0x0100, af.acc_native) or
                    bit(0x0400, af.acc_abstract) or
                    bit(0x0800, af.acc_strict) or
                    bit(0x1000, af.acc_synthetic)
        writeShort(flags)
    }

    fun writeCpEntry(entry: ConstantPoolEntry) {
        fun tag(tag: ConstantPoolTag) = writeByte(tag.tagByte)

        when (val inf = entry.data) {
            ConstantPoolData.Dummy -> {
            }
            is ConstantPoolData.ClassInfo -> {
                tag(ConstantPoolTag.Class)
                writeShort(inf.name_index)
            }
            is ConstantPoolData.FieldRefInfo -> {
                tag(ConstantPoolTag.FieldRef)
                writeShort(inf.class_index)
                writeShort(inf.name_and_type_index)
            }
            is ConstantPoolData.MethodRefInfo -> {
                tag(ConstantPoolTag.MethodRef)
                writeShort(inf.class_index)
                writeShort(inf.name_and_type_index)
            }
            is ConstantPoolData.InterfaceRefInfo -> {
                tag(ConstantPoolTag.InterfaceMethodRef)
                writeShort(inf.class_index)
                writeShort(inf.name_and_type_index)
            }
            is ConstantPoolData.StringInfo -> {
                tag(ConstantPoolTag.String)
                writeShort(inf.string_index)
            }
            is ConstantPoolData.IntegerInfo -> {
                tag(ConstantPoolTag.Integer)
                writeInt(inf.int)
            }
            is ConstantPoolData.FloatInfo -> {
                tag(ConstantPoolTag.Float)
                writeFloat(inf.float)
            }
            is ConstantPoolData.LongInfo -> {
                tag(ConstantPoolTag.Long)
                writeLong(inf.long)
            }
            is ConstantPoolData.DoubleInfo -> {
                tag(ConstantPoolTag.Double)
                writeDouble(inf.double)
            }
            is ConstantPoolData.NameAndTypeInfo -> {
                tag(ConstantPoolTag.NameAndType)
                writeShort(inf.name_index)
                writeShort(inf.descriptor_index)
            }
            is ConstantPoolData.Utf8Info -> {
                tag(ConstantPoolTag.Utf8)
                val ba = inf.string.toByteArray()
                writeShort(ba.size)
                writeBytes(ba)
            }
            is ConstantPoolData.MethodHandleInfo -> {
                tag(ConstantPoolTag.MethodHandle)
                writeByte(inf.reference_kind.toInt())
                writeShort(inf.reference_index)
            }
            is ConstantPoolData.MethodTypeInfo -> {
                tag(ConstantPoolTag.MethodType)
                writeShort(inf.descriptor_index)
            }
            is ConstantPoolData.DynamicInfo -> {
                tag(ConstantPoolTag.Dynamic)
                writeShort(inf.boostrap_method_attr_index)
                writeShort(inf.name_and_type_index)
            }
            is ConstantPoolData.InvokeDynamicInfo -> {
                tag(ConstantPoolTag.InvokeDynamic)
                writeShort(inf.boostrap_method_attr_index)
                writeShort(inf.name_and_type_index)
            }
            is ConstantPoolData.ModuleInfo -> {
                tag(ConstantPoolTag.Module)
                writeShort(inf.name_index)
            }
            is ConstantPoolData.PackageInfo -> {
                tag(ConstantPoolTag.Package)
                writeShort(inf.name_index)
            }
        }
    }
}

private fun writeAttributes(dos: DataOutputStream, attributes: List<AttributeInfo>) {
    dos.writeShort(attributes.size)
    for (attribute in attributes) {
        dos.writeShort(attribute.attribute_name_index.toInt())
        if (attribute.interpreted != null) {
            val baos = ByteArrayOutputStream()
            val daos = DataOutputStream(baos)
            writeAttributeBody(daos, attribute.interpreted)
            daos.flush()
            val arr = baos.toByteArray()
            dos.writeInt(arr.size)
            dos.write(arr)
        } else {
            attribute.uninterpreted!!
            dos.writeInt(attribute.uninterpreted.size)
            dos.write(attribute.uninterpreted)
        }
    }
}

fun writeAttributeBody(dos: DataOutputStream, a: Attribute) {
    when (a) {
        is Attribute.ConstantValue -> TODO()
        is Attribute.Code -> {
            dos.writeShort(a.max_stack.toInt())
            dos.writeShort(a.max_locals.toInt())
            dos.writeInt(a.code.size)
            dos.write(a.code)

            dos.writeShort(a.exception_table.size)
            for (exc in a.exception_table) {
                dos.writeShort(exc.start_pc.toInt())
                dos.writeShort(exc.end_pc.toInt())
                dos.writeShort(exc.handler_pc.toInt())
                dos.writeShort(exc.catch_type.toInt())
            }

            writeAttributes(dos, a.attributes)
        }
        is Attribute.StackMapTable -> {
            dos.writeShort(a.entries.size)
            for (entry in a.entries) {
                when (entry) {
                    is Attribute.StackMapTable.StackMapFrame.SameFrame -> {
                        if (entry.offset in 0..63) {
                            dos.writeByte(entry.offset)
                        } else {
                            dos.writeByte(251)
                            dos.writeShort(entry.offset)
                        }
                    }
                    is Attribute.StackMapTable.StackMapFrame.SameLocals1StackItemFrame -> {
                        if (entry.offset in 0..63) {
                            dos.writeByte(entry.offset + 64)
                            writeVerificationType(dos, entry.newStackElement)
                        } else {
                            dos.writeByte(247)
                            dos.writeShort(entry.offset)
                            writeVerificationType(dos, entry.newStackElement)
                        }
                    }
                    is Attribute.StackMapTable.StackMapFrame.ChopFrame -> {
                        dos.writeByte(251 - entry.k)
                        dos.writeShort(entry.offset)
                    }
                    is Attribute.StackMapTable.StackMapFrame.AppendFrame -> {
                        assert(entry.newLocals.size <= 3)
                        dos.writeByte(251 + entry.newLocals.size)
                        dos.writeShort(entry.offset)
                        entry.newLocals.forEach { writeVerificationType(dos, it) }
                    }
                    is Attribute.StackMapTable.StackMapFrame.FullFrame -> {
                        dos.writeByte(255)
                        dos.writeShort(entry.offset)
                        dos.writeShort(entry.locals.size)
                        entry.locals.forEach { writeVerificationType(dos, it) }
                        dos.writeShort(entry.stack.size)
                        entry.stack.forEach { writeVerificationType(dos, it) }
                    }
                }
            }
        }
        else -> throw Exception("Unhandled attribute $a")
    }
}

fun writeVerificationType(dos: DataOutputStream, verificationType: Attribute.StackMapTable.VerificationType) {
    var short = -1
    val tag = when (verificationType) {
        is Attribute.StackMapTable.VerificationType.Top -> 0
        is Attribute.StackMapTable.VerificationType.Integer -> 1
        is Attribute.StackMapTable.VerificationType.Float -> 2
        is Attribute.StackMapTable.VerificationType.Double -> 3
        is Attribute.StackMapTable.VerificationType.Long -> 4
        is Attribute.StackMapTable.VerificationType.Null -> 5
        is Attribute.StackMapTable.VerificationType.UninitializedThis -> 6
        is Attribute.StackMapTable.VerificationType.Object -> {short = verificationType.cpool_index ; 7}
        is Attribute.StackMapTable.VerificationType.Uninitialized -> { short = verificationType.offset ; 8}
        else -> throw Exception("Unhandled verif type tag $verificationType")
    }
    dos.writeByte(tag)
    if (short != -1)
        dos.writeShort(short)
}

fun writeClassFile(classFile: ClassFile, outputStream: DataOutputStream) =
    ClassFileWriter(classFile, outputStream).write()

fun writeClassFile(classFile: ClassFile, file: File) {
    val fos = FileOutputStream(file)
    try {
        val dos = DataOutputStream(fos)
        writeClassFile(classFile, dos)
    } finally {
        fos.close()
    }
}