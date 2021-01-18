package boneless.emit

import boneless.Def
import boneless.Expression
import boneless.Literal
import boneless.Module
import boneless.classfile.*
import boneless.type.PrimitiveTypeEnum
import boneless.type.Type
import boneless.type.unit_type
import java.io.File

class Emitter(val modules: List<Module>, val outputDir: File) {
    val mod_classes = mutableMapOf<Module, ClassFile>()
    val type_classes = mutableMapOf<Type, ClassFile>()

    fun emit() {
        for (module in modules)
            mod_classes[module] = emit(module)

        for (cf in mod_classes.values) {
            val outputFile = File("${outputDir.absoluteFile}/${cf.name}.class")
            writeClassFile(cf, outputFile)
        }
    }

    fun convertFieldType(type: Type): FieldDescriptor? {
        return when(type) {
            is Type.PrimitiveType -> when(type.primitiveType) {
                PrimitiveTypeEnum.Bool -> TODO()
                PrimitiveTypeEnum.I32 -> FieldDescriptor.BaseType.I
                PrimitiveTypeEnum.I64 -> TODO()
                PrimitiveTypeEnum.F32 -> TODO()
            }
            is Type.TypeApplication -> TODO()
            is Type.RecordType -> TODO()
            is Type.TupleType -> if (type.isUnit) null else TODO()
            is Type.ArrayType -> TODO()
            is Type.EnumType -> TODO()
            is Type.NominalType -> TODO()
            is Type.FnType -> TODO()
        }
    }

    fun convertMethodType(fnType: Type.FnType): MethodDescriptor {
        val dom = convertFieldType(fnType.dom)
        val codom = if (fnType.codom == unit_type()) ReturnDescriptor.V else ReturnDescriptor.NonVoidDescriptor(convertFieldType(fnType.codom)!!)
        return MethodDescriptor(listOf(dom).filterNotNull(), codom)
    }

    fun emit(module: Module): ClassFile {
        val builder = ClassFileBuilder(className = module.name)

        for (def in module.defs) {
            when(def.body) {
                is Def.DefBody.ExprBody -> TODO()
                is Def.DefBody.DataCtor -> TODO()
                is Def.DefBody.FnBody -> {
                    val descriptor = convertMethodType(def.type as Type.FnType)
                    val code = emit(builder, def.body.fn)
                    builder.staticMethod(def.identifier, descriptor.toString(), code)
                }
                is Def.DefBody.TypeAlias -> {}
            }
        }

        return builder.finish()
    }

    private fun emit(cfBuilder: ClassFileBuilder, fn: Expression.Function): Attribute.Code {
        val builder = BytecodeBuilder(cfBuilder)

        val fnType = fn.type as Type.FnType

        emit(builder, fn.body)
        if (fn.body.type!! == unit_type()) {
            builder.return_void()
        } else {
            builder.return_value(convertFieldType(fnType.codom)!!.toActualJVMType())
        }

        return builder.finish()
    }

    private fun emit(builder: BytecodeBuilder, expr: Expression) {
        when (expr) {
            is Expression.QuoteLiteral -> emit(builder, expr.literal)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> TODO()
            is Expression.ListExpression -> TODO()
            is Expression.RecordExpression -> TODO()
            is Expression.Invocation -> TODO()
            is Expression.Function -> TODO()
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Sequence -> TODO()
            is Expression.Conditional -> TODO()
            is Expression.WhileLoop -> TODO()
        }
    }

    private fun emit(builder: BytecodeBuilder, literal: Literal) {
        when (literal) {
            is Literal.NumLiteral -> {
                when((literal.type as Type.PrimitiveType).primitiveType) {
                    PrimitiveTypeEnum.Bool -> TODO()
                    PrimitiveTypeEnum.I32 -> {
                        builder.pushInt(literal.number.toInt())
                    }
                    PrimitiveTypeEnum.I64 -> TODO()
                    PrimitiveTypeEnum.F32 -> TODO()
                }
            }
            is Literal.StrLiteral -> TODO()
            is Literal.ListLiteral -> if(literal.isUnit) {
                // put nothing on the stack
            } else {
                TODO()
            }
            is Literal.RecordLiteral -> TODO()
        }
    }
}

fun emit(module: Module, outputDir: File) = Emitter(listOf(module), outputDir).emit()