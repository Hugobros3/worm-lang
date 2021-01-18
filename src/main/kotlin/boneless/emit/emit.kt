package boneless.emit

import boneless.Def
import boneless.Expression
import boneless.Literal
import boneless.Module
import boneless.classfile.*
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

    fun typeDescriptor(type: Type, is_ret_type: Boolean = false): String = when(type) {
        is Type.PrimitiveType -> TODO()
        is Type.TypeApplication -> TODO()
        is Type.RecordType -> TODO()
        is Type.TupleType -> if (type.isUnit) {
            if (is_ret_type) "V" else ""
        } else {
            TODO()
        }
        is Type.ArrayType -> TODO()
        is Type.EnumType -> TODO()
        is Type.NominalType -> TODO()
        is Type.FnType -> "(${typeDescriptor(type.dom)})${typeDescriptor(type.codom, true)}"
    }

    fun emit(module: Module): ClassFile {
        val builder = ClassFileBuilder(className = module.name)

        for (def in module.defs) {
            when(def.body) {
                is Def.DefBody.ExprBody -> TODO()
                is Def.DefBody.DataCtor -> TODO()
                is Def.DefBody.FnBody -> {
                    val descriptor = typeDescriptor(def.type!!)
                    val code = emit(def.body.fn)
                    builder.staticMethod(def.identifier, descriptor, code)
                }
                is Def.DefBody.TypeAlias -> {}
            }
        }

        return builder.finish()
    }

    private fun emit(fn: Expression.Function): Attribute.Code {
        val builder = BytecodeBuilder()

        emit(builder, fn.body)
        if (fn.body.type!! == unit_type()) {
            builder.return_void()
        } else {
            TODO()
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
            is Literal.NumLiteral -> TODO()
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