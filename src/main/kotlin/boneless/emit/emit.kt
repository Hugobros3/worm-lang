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
        for (cf in type_classes.values) {
            val outputFile = File("${outputDir.absoluteFile}/${cf.name}.class")
            writeClassFile(cf, outputFile)
        }
    }

    fun getFieldDescriptor(type: Type): FieldDescriptor? {
        return when(type) {
            is Type.PrimitiveType -> when(type.primitiveType) {
                PrimitiveTypeEnum.Bool -> TODO()
                PrimitiveTypeEnum.I32 -> FieldDescriptor.BaseType.I
                PrimitiveTypeEnum.I64 -> TODO()
                PrimitiveTypeEnum.F32 -> TODO()
            }
            is Type.TypeApplication -> TODO()
            is Type.RecordType -> TODO()
            is Type.TupleType -> if (type.isUnit) null else FieldDescriptor.ReferenceType.NullFreeClassType(mangled_datatype_name(type))
            is Type.ArrayType -> TODO()
            is Type.EnumType -> TODO()
            is Type.NominalType -> TODO()
            is Type.FnType -> TODO()
        }
    }

    fun getMethodDescriptor(fnType: Type.FnType): MethodDescriptor {
        val dom = getFieldDescriptor(fnType.dom)
        val codom = if (fnType.codom == unit_type()) ReturnDescriptor.V else ReturnDescriptor.NonVoidDescriptor(getFieldDescriptor(fnType.codom)!!)
        return MethodDescriptor(listOf(dom).filterNotNull(), codom)
    }

    fun mangled_datatype_name(type: Type): String = when(type) {
        is Type.PrimitiveType -> "Primitive${type.primitiveType.name}"
        is Type.TypeApplication -> throw Exception("TypeApp not supported")
        is Type.RecordType -> "RecordType__" + type.elements.joinToString("") { (f, t) -> "${f}_${mangled_datatype_name(t)}__" }
        is Type.TupleType -> "TupleType__" + type.elements.joinToString("") { t -> "${mangled_datatype_name(t)}__" }
        is Type.ArrayType -> TODO()
        is Type.EnumType -> TODO()
        is Type.NominalType -> "BL_${type.name}"
        is Type.FnType -> throw Exception("Not a data type")
    }

    fun tuple_type_init_descriptor(tupleType: Type.TupleType) = MethodDescriptor(tupleType.elements.mapNotNull { getFieldDescriptor(it) }, ReturnDescriptor.NonVoidDescriptor(getFieldDescriptor(tupleType)!!))

    fun emit_datatype_classfile_if_needed(type: Type) {
        when (type) {
            is Type.PrimitiveType -> {}
            is Type.TypeApplication -> throw Exception("TypeApp not supported")
            is Type.RecordType -> TODO()
            is Type.TupleType -> {
                // Unit type does not get a class
                if (type == unit_type())
                    return

                type_classes.getOrPut(type) {
                    val builder = ClassFileBuilder(className = mangled_datatype_name(type), accessFlags = defaultClassAccessFlags.copy(acc_value_type = true))
                    for ((i, element) in type.elements.withIndex()) {
                        if (element == unit_type())
                            continue
                        builder.field("_$i", getFieldDescriptor(element)!!, defaultFieldAccessFlags.copy(acc_final = true))
                    }

                    val initDescriptor = tuple_type_init_descriptor(type)
                    val initCodeBuilder = BytecodeBuilder(builder)
                    val params = type.elements.mapIndexed { i, t ->
                        val fd = getFieldDescriptor(t)
                        if (fd != null) {
                            initCodeBuilder.reserveVariable(fd.toActualJVMType().asComputationalType)
                        } else null
                    }

                    val mangled_dt = mangled_datatype_name(type)
                    initCodeBuilder.pushDefaultValueType(mangled_dt)
                    for ((i, element) in type.elements.withIndex()) {
                        if (element == unit_type())
                            continue
                        initCodeBuilder.loadVariable(params[i]!!)
                        val fieldName = "_$i"
                        initCodeBuilder.mutateSetFieldName(mangled_dt, fieldName, getFieldDescriptor(element)!!)
                    }
                    initCodeBuilder.return_value(getFieldDescriptor(type)!!.toActualJVMType())

                    builder.method("<init>", initDescriptor, defaulMethodAccessFlags.copy(acc_static = true, acc_public = true), initCodeBuilder.finish())
                    builder.finish()
                }
                for (element in type.elements)
                    emit_datatype_classfile_if_needed(element)
            }
            is Type.ArrayType -> TODO()
            is Type.EnumType -> TODO()
            is Type.NominalType -> TODO()
            is Type.FnType -> {
                emit_datatype_classfile_if_needed(type.dom)
                emit_datatype_classfile_if_needed(type.codom)
            }
        }
    }

    fun emit(module: Module): ClassFile {
        val builder = ClassFileBuilder(className = module.name, accessFlags = defaultClassAccessFlags)

        for (def in module.defs) {
            when(def.body) {
                is Def.DefBody.ExprBody -> TODO()
                is Def.DefBody.DataCtor -> TODO()
                is Def.DefBody.FnBody -> {
                    val descriptor = getMethodDescriptor(def.type as Type.FnType)
                    val code = FunctionEmitter(def.body, builder).emit()
                    builder.method(def.identifier, descriptor, defaulMethodAccessFlags.copy(acc_final = true, acc_static = true), code)
                }
                is Def.DefBody.TypeAlias -> {
                    emit_datatype_classfile_if_needed(def.body.aliasedType)
                }
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
                val argument_local_var = builder.reserveVariable(getFieldDescriptor(type.dom)!!.toActualJVMType().asComputationalType)
                val procedure: PutOnStack = {
                    builder.loadVariable(argument_local_var)
                }
                val m = mutableMapOf<Pattern, PutOnStack>()
                registerPattern(m, fn.param, procedure)
                patternsAccess += m
            }
            println(patternsAccess)
        }

        fun registerPattern(map: MutableMap<Pattern, PutOnStack>, pattern: Pattern, procedure: PutOnStack) {
            map[pattern] = procedure
            when (pattern) {
                is Pattern.BinderPattern -> {} // that's it
                is Pattern.LiteralPattern -> TODO()
                is Pattern.ListPattern -> {
                    when (val type = pattern.type!!) {
                        is Type.TupleType -> {
                            for ((i, subpattern) in pattern.elements.withIndex()) {
                                if (subpattern.type!! == unit_type())
                                    continue
                                val extract_element_procedure: PutOnStack = {
                                    procedure()
                                    builder.getField(mangled_datatype_name(type), "_$i", getFieldDescriptor(subpattern.type!!)!!)
                                }
                                registerPattern(map, subpattern, extract_element_procedure)
                            }
                        }
                        else -> throw Exception("Can't emit a list pattern as a $type")
                    }
                }
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
                builder.return_value(getFieldDescriptor(type.codom)!!.toActualJVMType())
            }

            return builder.finish()
        }

        private fun emit(expr: Expression) {
            emit_datatype_classfile_if_needed(expr.type!!)
            when (expr) {
                is Expression.QuoteLiteral -> emit(builder, expr.literal)
                is Expression.QuoteType -> TODO()
                is Expression.IdentifierRef -> when(val r = expr.id.resolved) {
                    is BoundIdentifier.ToDef -> TODO()
                    is BoundIdentifier.ToPatternBinder -> accessPtrn(r.binder)
                    is BoundIdentifier.ToBuiltinFn -> throw Exception("Not allowed / (should not be) possible")
                }
                is Expression.ListExpression -> {
                    when (val type = expr.type!!) {
                        is Type.TupleType -> {
                            if (type == unit_type())
                                return
                            // Because of reasons (I suspect having to do with ABI stability), we do not actually have the permission to build a tuple "manually"
                            // outside of its declaring class. Instead, we are required to call the constructor for it, which is mildly ugly but hopefully
                            // the JVM's good reputation for being really good at optmizing away fn calls will save our bacon :)
                            for (element in expr.elements)
                                emit(element)
                            builder.callStatic(mangled_datatype_name(expr.type!!), "<init>", tuple_type_init_descriptor(type))
                        }
                        else -> throw Exception("cannot emit a list expression as a ${expr.type}")
                    }
                }
                is Expression.RecordExpression -> TODO()
                is Expression.Invocation -> {
                    emit(expr.arg)
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
                    val argument_local_var = builder.reserveVariable(getFieldDescriptor(instruction.pattern.type!!)!!.toActualJVMType().asComputationalType)

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