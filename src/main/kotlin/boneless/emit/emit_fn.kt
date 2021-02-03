package boneless.emit

import boneless.Def
import boneless.Expression
import boneless.Instruction
import boneless.Pattern
import boneless.bind.TermLocation
import boneless.classfile.Attribute
import boneless.classfile.BytecodeBuilder
import boneless.classfile.ClassFileBuilder
import boneless.type.Type
import boneless.type.unit_type

class FunctionEmitter(private val emitter: Emitter, val fn: Expression.Function, private val cfBuilder: ClassFileBuilder) {
    val type = fn.type!! as Type.FnType
    private val builder = BytecodeBuilder(cfBuilder)

    //val parameter: Int
    val patternsAccess = mutableListOf<Map<Pattern, PutOnStack>>()

    init {
        if (type.dom != unit_type()) {
            val argument_local_var = builder.reserveVariable(type.dom)
            val procedure: PutOnStack = {
                builder.loadVariable(argument_local_var)
            }
            val m = mutableMapOf<Pattern, PutOnStack>()
            emitter.registerPattern(m, fn.param, procedure)
            patternsAccess += m
        }
        // println(patternsAccess)
    }

    fun accessPtrn(pattern: Pattern) {
        for (frame in patternsAccess) {
            (frame[pattern] ?: continue).invoke(builder)
            return
        }
        throw Exception("$pattern is not accessible")
    }

    fun emit(): List<Attribute> {
        emit(fn.body)
        if (fn.body.type!! == unit_type()) {
            builder.return_void()
        } else {
            builder.return_value(type.codom)
        }

        return builder.finish()
    }

    private fun emit(expr: Expression) {
        emitter.emit_datatype_classfile_if_needed(expr.type!!)
        when (expr) {
            is Expression.QuoteLiteral -> emitter.emit_literal(builder, expr.literal)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> when(val r = expr.id.resolved) {
                is TermLocation.DefRef -> TODO()
                is TermLocation.BinderRef -> accessPtrn(r.binder)
                is TermLocation.BuiltinFnRef -> throw Exception("Not allowed / (should not be) possible")
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
                        builder.callStaticInternal(mangled_datatype_name(expr.type!!), "<init>", getTupleInitializationMethodDescriptor(type), cfBuilder.getVerificationType(type))
                    }
                    else -> throw Exception("cannot emit a list expression as a ${expr.type}")
                }
            }
            is Expression.RecordExpression -> TODO()
            is Expression.Invocation -> {
                when {
                    expr.callee is Expression.IdentifierRef -> when (val r = expr.callee.id.resolved) {
                        is TermLocation.DefRef -> TODO()
                        is TermLocation.BinderRef -> TODO()
                        is TermLocation.BuiltinFnRef -> {
                            emit(expr.arg)
                            builder.callStatic("BuiltinFns", r.fn.name, r.fn.type)
                            return
                        }
                        is TermLocation.TypeParamRef -> TODO()
                    }
                    expr.callee is Expression.Projection && expr.callee.expression is Expression.IdentifierRef -> when(val r = expr.callee.expression.id.resolved) {
                        is TermLocation.DefRef -> {
                            val def = r.def
                            if (def.body is Def.DefBody.Contract) {
                                emit(expr.arg)
                                builder.callStatic(mangled_contract_instance_name(def.identifier, expr.callee.expression.deducedImplicitSpecializationArguments!!), expr.callee.id, expr.callee.type as Type.FnType)
                                return
                            }
                        }
                    }
                }

                TODO("perform actual call")
            }
            is Expression.Function -> TODO()
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Sequence -> {
                builder.enterScope(expr.type!!) {
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
                val argument_local_var = builder.reserveVariable(instruction.pattern.type!!)

                emit(instruction.body)
                builder.setVariable(argument_local_var)
                val procedure: PutOnStack = {
                    builder.loadVariable(argument_local_var)
                }
                val m = mutableMapOf<Pattern, PutOnStack>()
                emitter.registerPattern(m, instruction.pattern, procedure)
                patternsAccess += m
            }
            is Instruction.Evaluate -> TODO()
        }
    }
}