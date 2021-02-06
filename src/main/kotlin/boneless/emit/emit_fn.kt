package boneless.emit

import boneless.Def
import boneless.Expression
import boneless.Instruction
import boneless.Pattern
import boneless.bind.TermLocation
import boneless.classfile.*
import boneless.type.Type
import boneless.type.unit_type
import java.io.Writer

class FunctionEmitter private constructor(private val emitter: Emitter, private val cfBuilder: ClassFileBuilder) {
    lateinit var builder: MethodBuilder private set
    val patternsAccess = mutableListOf<Map<Pattern, PutOnStack>>()

    lateinit var bb: BasicBlock

    constructor(emitter: Emitter, cfBuilder: ClassFileBuilder, fn: Expression.Function) : this(emitter, cfBuilder) {
        var initialLocals = emptyList<VerificationType>()
        val fnType = fn.type as Type.FnType
        if (fnType.dom != unit_type()) {
            initialLocals = listOf(cfBuilder.getVerificationType(fnType.dom)!!)

            val procedure: PutOnStack = { bbb ->
                bbb.loadVariable(0)
            }
            val m = mutableMapOf<Pattern, PutOnStack>()
            emitter.registerPattern(m, fn.param, procedure)
            patternsAccess += m
        }

        builder = MethodBuilder(cfBuilder, initialLocals)
        bb = builder.initialBasicBlock
    }

    constructor(emitter: Emitter, cfBuilder: ClassFileBuilder, params: List<VerificationType>) : this(emitter, cfBuilder) {
        builder = MethodBuilder(cfBuilder, params)
        bb = builder.initialBasicBlock
    }

    fun accessPtrn(pattern: Pattern) {
        for (frame in patternsAccess) {
            (frame[pattern] ?: continue).invoke(bb)
            return
        }
        throw Exception("$pattern is not accessible")
    }

    fun emit(fn: Expression.Function) {
        val fnType = fn.type as Type.FnType
        emit(fn.body)
        if (fn.body.type!! == unit_type()) {
            bb.return_void()
        } else {
            bb.return_value(cfBuilder.getVerificationType(fnType.codom) ?: throw Exception("Return can't be zero sized: ${fnType.codom}"))
        }
    }

    fun finish(dumpDot: Writer?) = builder.finish(dumpDot)

    fun emit(expr: Expression) {
        emitter.emit_datatype_classfile_if_needed(expr.type!!)
        when (expr) {
            is Expression.QuoteLiteral -> emitter.emit_literal(bb, expr.literal)
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
                        // the JVM's good reputation for being really good at optimizing away fn calls will save our bacon :)
                        for (element in expr.elements)
                            emit(element)
                        bb.callStaticInternal(mangled_datatype_name(expr.type!!), "<init>", getTupleInitializationMethodDescriptor(type), cfBuilder.getVerificationType(type))
                    }
                    else -> throw Exception("cannot emit a list expression as a ${expr.type}")
                }
            }
            is Expression.RecordExpression -> TODO()
            is Expression.Invocation -> {
                when {
                    expr.callee is Expression.IdentifierRef -> when (val r = expr.callee.id.resolved) {
                        is TermLocation.DefRef -> {
                            if (r.def.body is Def.DefBody.FnBody) {
                                // TODO specialization garbage
                                assert(r.def.typeParams.isEmpty())
                                emit(expr.arg)
                                val methodDescriptor = getMethodDescriptor((expr.callee.type as Type.FnType))
                                val expectedReturn = cfBuilder.getVerificationType((expr.callee.type as Type.FnType).codom)
                                bb.callStaticInternal(r.def.module_, r.def.identifier, methodDescriptor, expectedReturn)
                                return
                            } else throw Exception("Can't call that def")
                        }
                        is TermLocation.BinderRef -> TODO()
                        is TermLocation.BuiltinFnRef -> {
                            emit(expr.arg)
                            val methodDescriptor = getMethodDescriptor((r.fn.type as Type.FnType))
                            val expectedReturn = cfBuilder.getVerificationType((r.fn.type as Type.FnType).codom)
                            bb.callStaticInternal("BuiltinFns", r.fn.name, methodDescriptor, expectedReturn)
                            return
                        }
                        is TermLocation.TypeParamRef -> TODO()
                    }
                    expr.callee is Expression.Projection && expr.callee.expression is Expression.IdentifierRef -> when(val r = expr.callee.expression.id.resolved) {
                        is TermLocation.DefRef -> {
                            val def = r.def
                            if (def.body is Def.DefBody.Contract) {
                                emit(expr.arg)
                                val methodDescriptor = getMethodDescriptor((expr.callee.type as Type.FnType))
                                val expectedReturn = cfBuilder.getVerificationType((expr.callee.type as Type.FnType).codom)
                                bb.callStaticInternal(mangled_contract_instance_name(def.identifier, expr.callee.expression.deducedImplicitSpecializationArguments!!), expr.callee.id, methodDescriptor, expectedReturn)
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
                val prev = bb
                val after_seq = builder.basicBlock(prev, "after_seq", additionalStackInputs = listOf(cfBuilder.getVerificationType(expr.type!!)).filterNotNull() )
                val inside_seq = builder.basicBlock(prev, "inside_seq")
                prev.jump(inside_seq)
                bb = inside_seq
                for (instruction in expr.instructions)
                    emit(instruction)
                if (expr.yieldExpression != null)
                    emit(expr.yieldExpression)
                bb.jump(after_seq)
                bb = after_seq
            }
            is Expression.Conditional -> {
                val yieldType = expr.type!!
                val additionalStack = listOf(cfBuilder.getVerificationType(yieldType)).filterNotNull()
                val ifTrueBB = builder.basicBlock(bb, bbName = "ifTrue")
                val ifFalseBB = builder.basicBlock(bb, bbName = "ifFalse")
                val joinBB = builder.basicBlock(bb, additionalStackInputs = additionalStack, bbName = "join")
                emit(expr.condition)
                bb.branch(BranchType.IF_NEQ, ifTrueBB, ifFalseBB)

                bb = ifTrueBB
                emit(expr.ifTrue)
                ifTrueBB.jump(joinBB)

                bb = ifFalseBB
                emit(expr.ifFalse)
                ifFalseBB.jump(joinBB)
                bb = joinBB
            }
            is Expression.WhileLoop -> {
                val loopBody = builder.basicBlock(bb, bbName = "loop_body")
                val loopJoin = builder.basicBlock(bb, bbName = "loop_join")
                emit(expr.loopCondition)
                bb.branch(BranchType.IF_NEQ, loopBody, loopJoin)
                bb = loopBody

                emit(expr.body)
                val vt = cfBuilder.getVerificationType(expr.body.type!!)
                if (vt != null) {
                    bb.drop_value(vt)
                }

                emit(expr.loopCondition)
                bb.branch(BranchType.IF_NEQ, loopBody, loopJoin)
                bb = loopJoin
            }
            is Expression.ExprSpecialization -> TODO()
            is Expression.Projection -> TODO()
            else -> throw Exception("Unhandled expression ast node: $expr")
        }
    }

    fun emit(instruction: Instruction) {
        when(instruction) {
            is Instruction.Let -> {
                val vt = cfBuilder.getVerificationType(instruction.pattern.type!!)
                val procedure: PutOnStack = if (vt != null) {
                    val localVar = bb.reserveVariable(vt)
                    emit(instruction.body)
                    bb.setVariable(localVar)
                    ({
                        it.loadVariable(localVar)
                    })
                } else {
                    emit(instruction.body)
                    ({
                        // fuckall, it is void !
                    })
                }

                val m = mutableMapOf<Pattern, PutOnStack>()
                emitter.registerPattern(m, instruction.pattern, procedure)
                patternsAccess += m
            }
            is Instruction.Evaluate -> {
                emit(instruction.expr)
                val vt = cfBuilder.getVerificationType(instruction.expr.type!!)
                if (vt != null) {
                    bb.drop_value(vt)
                }
            }
        }
    }
}