package boneless.emit

import boneless.*
import boneless.bind.BoundIdentifier
import boneless.classfile.*
import boneless.type.PrimitiveTypeEnum
import boneless.type.Type
import boneless.type.unit_type
import java.io.File

typealias PutOnStack = () -> Unit
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
                    val code = FunctionEmitter(def.body, builder).emit()
                    builder.staticMethod(def.identifier, descriptor.toString(), code)
                }
                is Def.DefBody.TypeAlias -> {}
            }
        }

        return builder.finish()
    }

    inner class FunctionEmitter(val def_body: Def.DefBody.FnBody, val cfBuilder: ClassFileBuilder) {
        val fn = def_body.fn
        val type = fn.type!! as Type.FnType
        val builder = BytecodeBuilder(cfBuilder)

        //val parameter: Int
        val patternsAccess = mutableListOf<Map<Pattern, PutOnStack>>()

        init {
            if (type.dom != unit_type()) {
                val argument_local_var = builder.reserveVariable(convertFieldType(type.dom)!!.toActualJVMType().comp)
                val procedure: PutOnStack = {
                    builder.loadVariable(argument_local_var)
                }
                val m = mutableMapOf<Pattern, PutOnStack>()
                registerPattern(m, fn.parameters, procedure)
                patternsAccess += m
            }
            println(patternsAccess)
        }

        fun registerPattern(map: MutableMap<Pattern, PutOnStack>, pattern: Pattern, procedure: PutOnStack) {
            map[pattern] = procedure
            when (pattern) {
                is Pattern.BinderPattern -> {} // that's it
                is Pattern.LiteralPattern -> TODO()
                is Pattern.ListPattern -> TODO()
                is Pattern.RecordPattern -> TODO()
                is Pattern.CtorPattern -> TODO()
                is Pattern.TypeAnnotatedPattern -> registerPattern(map, pattern.pattern, procedure)
            }
        }

        fun accessPtrn(pattern: Pattern) {
            for (frame in patternsAccess) {
                (frame[pattern] ?: continue).invoke()
                return
            }
            throw Exception("$pattern is not accessible")
        }

        fun emit(): Attribute.Code {
            emit(fn.body)
            if (fn.body.type!! == unit_type()) {
                builder.return_void()
            } else {
                builder.return_value(convertFieldType(type.codom)!!.toActualJVMType())
            }

            return builder.finish()
        }

        private fun emit(expr: Expression) {
            when (expr) {
                is Expression.QuoteLiteral -> emit(builder, expr.literal)
                is Expression.QuoteType -> TODO()
                is Expression.IdentifierRef -> when(val r = expr.id.resolved) {
                    is BoundIdentifier.ToDef -> TODO()
                    is BoundIdentifier.ToPatternBinder -> accessPtrn(r.binder)
                    is BoundIdentifier.ToBuiltinFn -> throw Exception("Not allowed / (should not be) possible")
                }
                is Expression.ListExpression -> TODO()
                is Expression.RecordExpression -> TODO()
                is Expression.Invocation -> {
                    when {
                        expr.args.isEmpty() -> {}
                        expr.args.size == 1 -> emit(expr.args.first())
                        else -> emit_tuple(expr.args)
                    }
                    TODO("perform actual call")
                }
                is Expression.Function -> TODO()
                is Expression.Ascription -> TODO()
                is Expression.Cast -> TODO()
                is Expression.Sequence -> {
                    builder.enterScope {
                        for (instruction in expr.instructions)
                            emit(instruction)
                        if (expr.yieldExpression != null)
                            emit(expr.yieldExpression)
                    }
                }
                is Expression.Conditional -> TODO()
                is Expression.WhileLoop -> TODO()
            }
        }

        fun emit_tuple(elements: List<Expression>) {
            TODO()
        }

        fun emit(instruction: Instruction) {
            when(instruction) {
                is Instruction.Let -> {
                    val argument_local_var = builder.reserveVariable(convertFieldType(instruction.pattern.type!!)!!.toActualJVMType().comp)

                    emit(instruction.body)
                    builder.setVariable(argument_local_var)
                    val procedure: PutOnStack = {
                        builder.loadVariable(argument_local_var)
                    }
                    val m = mutableMapOf<Pattern, PutOnStack>()
                    registerPattern(m, instruction.pattern, procedure)
                    patternsAccess += m
                }
                is Instruction.Evaluate -> TODO()
            }
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