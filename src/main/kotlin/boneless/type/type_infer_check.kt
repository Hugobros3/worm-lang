package boneless.type

import boneless.*
import boneless.bind.TermLocation
import boneless.bind.get_binder
import boneless.bind.get_def
import boneless.util.Visitors
import boneless.util.prettyPrint
import boneless.util.visitAST

fun type(module: Module) {
    TypeChecker(module).type()
}

interface Typeable {
    val type: Type
    fun set_type(type: Type?)
    val is_typed_yet: Boolean
}

fun typeable() = object : Typeable {
    private var _type: Type? = null

    override val type: Type
        get() = _type ?: throw Exception("Type isn't set yet")

    override fun set_type(type: Type?) {
        if (_type != null && type != null)
            throw Exception("Attempted to set type twice")
        _type = type
    }

    override val is_typed_yet: Boolean
        get() = _type != null
}

class TypeChecker(val module: Module) {
    val stack = mutableListOf<Frame>()
    class Frame(val def: Def)

    private fun cannot_infer(node: Typeable): Nothing {
        throw Exception("Cannot infer $node")
    }

    private fun type_error(error: String): Nothing {
        throw Exception("Error while typing: $error")
    }

    fun enter(what: Def): Frame {
        if (stack.any { it.def == what }) {
            error("The type checker has run into recursive problems typing $what")
        }
        val frame = Frame(what)
        stack.add(0, frame)
        return frame
    }

    fun leave() = stack.removeAt(0)
    fun current_frame() = stack[0]

    fun expect(type: Type, expected_type: Type) {
        if (type != expected_type)
            throw Exception("Expected $expected_type but got $type")
    }
    fun expect_subtype(type: Type, expected_type: Type) {
        if (!isSubtype(type, expected_type))
            type_error("Expected ${expected_type.prettyPrint()} but got ${type.prettyPrint()}")
    }

    fun coerce(expr: Expression, type: Type, expected_type: Type): Type {
        if (type == expected_type)
            return expected_type
        else if (isSubtype(type, expected_type)) {
            expr.implicitUpcast = expected_type
            return type
        } else if (type is Type.Mut) {
            expr.implicitDeref++
            return coerce(expr, type.elementType, expected_type)
        } else {
            type_error("Expected ${expected_type.prettyPrint()} (or a subtype) but got ${type.prettyPrint()}")
        }
    }

    fun infer(node: Typeable) = infer_(node, true)
    fun infer_def_lazily(node: Def) = infer_(node, false)

    private fun infer_(node: Typeable, throwIfAlreadyTyped: Boolean): Type {
        if (node.is_typed_yet) {
            if (throwIfAlreadyTyped)
                throw Exception("The type already exists!")
            else return node.type
        }
        val inferred = when (node) {
            is Def -> inferDef(node)
            is Literal -> inferValue(node)
            is Expression -> inferExpr(node)
            is Pattern -> inferPattern(node)
            else -> error("not a typeable node")
        }
        node.set_type(inferred)
        return inferred
    }

    fun check(node: Typeable, expected_type: Type): Type {
        if (node.is_typed_yet) throw Exception("The type already exists!")
        val checked = when (node) {
            is Def -> throw Exception("You may not check a def - this is local type inference only.")
            is Literal -> checkValue(node, expected_type)
            is Expression -> checkExpr(node, expected_type)
            is Pattern -> checkPattern(node, expected_type)
            else -> error("not a typeable node")
        }
        node.set_type(checked)
        return checked
    }

    fun type() {
        for (def in module.defs) {
            infer_def_lazily(def)
        }
    }

    fun inferDef(def: Def): Type {
        enter(def)
        def.typeParams = def.typeParamsNames.mapIndexed { i, n -> Type.TypeParam(TermLocation.TypeParamRef(def, i)) }
        val t = when (val body = def.body) {
            is Def.DefBody.ExprBody -> {
                if (body.annotatedType == null)
                    infer(body.expr)
                else
                    check(body.expr, resolveTypeExpression(body.annotatedType))
            }
            is Def.DefBody.DataCtor -> {
                // TODO return nominal type but allow it to subtype as that
                body.nominalType =
                    Type.NominalType(def.identifier, resolveTypeExpression(body.datatype))
                Type.FnType(
                    resolveTypeExpression(body.datatype),
                    body.nominalType,
                    constructorFor = body.nominalType
                )
            }
            is Def.DefBody.TypeAlias -> resolveTypeExpression(body.aliasedType)
            is Def.DefBody.FnBody -> {
                if (body.annotatedType == null)
                    infer(body.fn)
                else {
                    val resolved = resolveTypeExpression(body.annotatedType)
                    val inferred = infer(body.fn) as Type.FnType
                    expect(inferred.codom, resolved)
                    inferred
                }
            }
            is Def.DefBody.Contract -> resolveTypeExpression(body.payload)
            is Def.DefBody.Instance -> {
                val contract_def = get_def(body.contractId.resolved)
                contract_def?.body as? Def.DefBody.Contract ?: type_error("Instances must reference contract definitions")
                val contract_type = infer_def_lazily(contract_def)

                body.arguments = body.argumentsExpr.map { resolveTypeExpression(it) }

                val substitutions = contract_def.typeParamsNames.mapIndexed {
                        i, _ -> Pair(Type.TypeParam(TermLocation.TypeParamRef(contract_def, i)), body.arguments[i])
                }.toMap()

                val specializedType = specializeType(contract_type, substitutions)
                check(body.body, specializedType)
            }
        }
        leave()
        return t
    }

    fun inferExpr(expr: Expression): Type {
        return when (expr) {
            is Expression.QuoteLiteral -> infer(expr.literal)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> {
                when (val r = expr.id.resolved) {
                    is TermLocation.DefRef -> {
                        if (expr.deducedImplicitSpecializationArguments != null)
                            specializeType(infer_def_lazily(r.def), expr.deducedImplicitSpecializationArguments!!)
                        else
                            infer_def_lazily(r.def)
                    }
                    is TermLocation.BinderRef -> r.binder.type
                    is TermLocation.BuiltinFnRef -> r.fn.type
                    is TermLocation.TypeParamRef -> Type.TypeParam(r)
                }
            }
            is Expression.Projection -> {
                val inside = infer(expr.expression)
                inferProjection(inside, expr.id)
            }
            is Expression.ExprSpecialization -> {
                infer(expr.target)
                val def = get_def(expr.target) ?: throw Exception("Can only specialize defs")
                if (def.typeParamsNames.size != expr.arguments.size)
                    throw Exception("Given ${expr.arguments} arguments but ${def.identifier} only has ${def.typeParamsNames.size} type arguments")

                val typeArguments = expr.arguments.map { resolveTypeExpression(it) }

                if (def.body is Def.DefBody.Contract) findInstance(module, def, typeArguments) ?: throw Exception("No instance for contract ${def.identifier} with type arguments ${typeArguments.map { it.prettyPrint() }}")

                val genericType = infer_def_lazily(def)
                val substitutions = def.typeParamsNames.mapIndexed { i, _ ->
                    Pair(Type.TypeParam(TermLocation.TypeParamRef(def, i)), typeArguments[i])
                }.toMap()
                specializeType(genericType, substitutions)
            }
            is Expression.ListExpression -> {
                val inferred = expr.elements.map { infer(it) }
                assert(inferred.size > 1)
                Type.TupleType(inferred)
            }
            is Expression.RecordExpression -> {
                val inferred = expr.fields.map { (f, e) -> Pair(f, infer(e)) }
                Type.RecordType(inferred)
            }
            is Expression.Invocation -> {
                val argType = infer(expr.arg)
                if (expr.callee is Expression.IdentifierRef) {
                    val def = get_def(expr.callee)
                    if (def?.body is Def.DefBody.FnBody) {
                        val body = def.body as Def.DefBody.FnBody
                        if (body.annotatedType != null) {
                            // First time encountering this ?
                            if (!body.fn.param.is_typed_yet)
                                infer_def_lazily(def)
                            assert(body.fn.param.is_typed_yet)

                            val dom = body.fn.param.type
                            val codom = resolveTypeExpression(body.annotatedType)
                            coerce(expr.arg, argType, dom)
                            expr.callee.set_type(Type.FnType(dom, codom))
                            return codom
                        }
                    }
                }

                var targetType = infer(expr.callee)
                if (targetType !is Type.FnType)
                    throw Exception("invocation callee is not a function $targetType")

                val unbound = targetType.getUnboundTypeParams()
                if (unbound.isNotEmpty()) {
                    reset_types(expr.callee)
                    val unified = unify(targetType.dom, argType, true)
                    println(unified)

                    val visitor = create_visitor_for_unification_constraints(unified)
                    visitAST(expr.callee, visitor)
                    println(expr.callee)

                    targetType = infer(expr.callee) as Type.FnType
                    assert(targetType.getUnboundTypeParams().isEmpty()) { targetType }
                }

                coerce(expr.arg, argType, targetType.dom)
                targetType.codom
            }
            is Expression.Function -> {
                val dom = infer(expr.param)
                val codom = if (expr.returnTypeAnnotation == null)
                    infer(expr.body)
                else
                    check(expr.body, resolveTypeExpression(expr.returnTypeAnnotation))
                Type.FnType(dom, codom)
            }
            is Expression.Ascription -> {
                check(expr.expr, resolveTypeExpression(expr.ascribedType))
            }
            is Expression.Cast -> {
                // TODO have a Cast trait
                check(expr.expr, resolveTypeExpression(expr.destinationType))
            }
            is Expression.Sequence -> {
                for (inst in expr.instructions)
                    typeInstruction(inst)
                if (expr.yieldExpression == null)
                    unit_type()
                else
                    infer(expr.yieldExpression)
            }
            is Expression.Conditional -> {
                check(expr.condition, Type.PrimitiveType(PrimitiveTypeEnum.Bool))
                val left = infer(expr.ifTrue)
                val right = infer(expr.ifFalse)
                expect(left, right)
                left
            }
            is Expression.WhileLoop -> {
                check(expr.loopCondition, Type.PrimitiveType(PrimitiveTypeEnum.Bool))
                infer(expr.body)
                unit_type()
            }
            is Expression.Assignment -> {
                val target_type = infer(expr.target)
                if (target_type is Type.Mut) {
                    if (expr.target !is Expression.IdentifierRef || get_binder(expr.target) == null)
                        throw Exception("Muts should not be stored, something is wrong")
                    expr.mut_binder = get_binder(expr.target)
                    val no_mut = remove_mut(target_type)
                    check(expr.value, no_mut)
                    unit_type()
                } else {
                    TODO("Use traits for custom assignment logic")
                }
            }
        }
    }

    fun checkExpr(expr: Expression, expected_type: Type): Type {
        fun fallback(): Type {
            val inferred = inferExpr(expr)
            return coerce(expr, inferred, expected_type)
        }

        return when (expr) {
            is Expression.QuoteLiteral -> check(expr.literal, expected_type)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef,
            is Expression.Projection,
            is Expression.ExprSpecialization -> {
                return fallback()
            }
            is Expression.ListExpression -> {
                if (expected_type is Type.TupleType && expected_type.elements.size == expr.elements.size) {
                    val checked = expr.elements.zip(expected_type.elements).map { (e, et) -> check(e, et) }
                    Type.TupleType(checked)
                } else {
                    val inferred = inferExpr(expr)
                    coerce(expr, inferred, expected_type)
                }
            }
            is Expression.RecordExpression -> {
                if (expected_type is Type.RecordType && expected_type.elements.size == expr.fields.size) {
                    val checked = expected_type.elements.mapIndexed { i, (fieldName, expFieldType) ->
                        if (fieldName != expr.fields[i].first) {
                            return fallback()
                        }
                        Pair(fieldName, check(expr.fields[i].second, expFieldType))
                    }
                    Type.RecordType(checked)
                }
                else fallback()
            }
            is Expression.Invocation -> {
                var argType = infer(expr.arg)
                var targetType = infer(expr.callee)
                if (targetType !is Type.FnType)
                    type_error("invocation callee is not a function $targetType")

                // Force dereferencing any local mutables in argType:
                reset_types(expr.arg)
                argType = check(expr.arg, remove_mut(argType))
                // assert_no_mut(argType)

                val unbound = targetType.getUnboundTypeParams()
                if (unbound.isNotEmpty()) {
                    reset_types(expr.callee)
                    val unified = mergeConstraints(unify(targetType.dom, argType, true), unify(targetType.codom, expected_type, false))
                    println(unified)

                    val visitor = create_visitor_for_unification_constraints(unified)
                    visitAST(expr.callee, visitor)
                    println(expr.callee)

                    targetType = infer(expr.callee) as Type.FnType
                    assert(targetType.getUnboundTypeParams().isEmpty()) { targetType }
                }

                coerce(expr.arg, argType, targetType.dom)
                coerce(expr, targetType.codom, expected_type)
                targetType.codom
            }
            is Expression.Function -> {
                if (expected_type !is Type.FnType)
                    type_error("expected type of fn expression is not a function type")
                val dom = check(expr.param, expected_type.dom)
                val codom = check(expr.body, expected_type.codom)
                Type.FnType(dom, codom)
            }
            is Expression.Sequence -> {
                for (inst in expr.instructions)
                    typeInstruction(inst)
                if (expr.yieldExpression == null) {
                    expect(unit_type(), expected_type); expected_type
                } else
                    check(expr.yieldExpression, expected_type)
            }
            is Expression.Conditional -> {
                check(expr.condition, Type.PrimitiveType(PrimitiveTypeEnum.Bool))
                val left = infer(expr.ifTrue)
                val right = infer(expr.ifFalse)
                // TODO unify & coerce this crap
                expect(left, right)
                expect(left, expected_type)
                expected_type
            }
            is Expression.Assignment,
            is Expression.Ascription,
            is Expression.Cast,
            is Expression.WhileLoop -> fallback()
        }
    }

    fun inferValue(literal: Literal): Type {
        return when (literal) {
            is Literal.Undef -> cannot_infer(literal)
            is Literal.NumLiteral -> if (literal.number.toIntOrNull() != null) Type.PrimitiveType(
                PrimitiveTypeEnum.I32
            ) else Type.PrimitiveType(PrimitiveTypeEnum.F32)
            is Literal.BoolLiteral -> Type.PrimitiveType(PrimitiveTypeEnum.Bool)
            is Literal.StrLiteral -> TODO()
            is Literal.ListLiteral -> {
                val types = literal.elements.map { infer(it) }
                when {
                    types.isEmpty() -> unit_type()
                    else -> Type.TupleType(types)
                }
            }
            is Literal.RecordLiteral -> {
                val types = literal.fields.map { (f, v) -> Pair(f, infer(v)) }
                Type.RecordType(types)
            }
        }
    }

    fun checkValue(literal: Literal, expected_type: Type): Type {
        return when (literal) {
            is Literal.Undef -> expected_type
            is Literal.NumLiteral -> {
                if (expected_type !is Type.PrimitiveType)
                    type_error("Cannot type numerical literal '${literal.number}' as a ${expected_type.prettyPrint()}")
                expected_type
            }
            is Literal.BoolLiteral -> { expect(Type.PrimitiveType(PrimitiveTypeEnum.Bool), expected_type) ; Type.PrimitiveType(PrimitiveTypeEnum.Bool) }
            is Literal.StrLiteral -> TODO()
            is Literal.ListLiteral -> {
                if (expected_type !is Type.TupleType)
                    type_error("expected type of list expression is not a tuple")
                if (expected_type.elements.size != literal.elements.size)
                    type_error("expression has ${literal.elements.size} elements but exepected type ${expected_type.prettyPrint()} has ${expected_type.elements.size}")
                val checked = literal.elements.zip(expected_type.elements).map { (e, et) -> check(e, et) }
                Type.TupleType(checked)
            }
            is Literal.RecordLiteral -> TODO()
        }
    }

    fun inferPattern(pattern: Pattern): Type {
        return when (pattern) {
            is Pattern.BinderPattern -> type_error("No way to infer the type of $pattern")
            is Pattern.LiteralPattern -> infer(pattern.literal)
            is Pattern.ListPattern -> {
                val inferred = pattern.elements.map { infer(it) }
                assert(inferred.size > 1)
                Type.TupleType(inferred)
            }
            is Pattern.RecordPattern -> {
                val inferred = pattern.fields.map { (f, p) -> Pair(f, infer(p)) }
                Type.RecordType(inferred)
            }
            is Pattern.CtorPattern -> {
                val nominalTypeCtor = when (val r = pattern.callee.resolved) {
                    is TermLocation.DefRef -> if (r.def.is_type) null else infer_def_lazily(r.def)
                    is TermLocation.TypeParamRef -> { Type.TypeParam(r) ; TODO("resolve") }
                    else -> throw Exception("Can't possibly be a nominal type ctor")
                } as? Type.FnType
                if (nominalTypeCtor == null || nominalTypeCtor.codom !is Type.NominalType)
                    type_error("a constructor pattern has to produce a nominal type")
                assert(pattern.args.size > 0)
                val arg_ptrn: Pattern = when {
                    pattern.args.size == 1 -> pattern.args[0]
                    else -> Pattern.ListPattern(pattern.args)
                }
                check(arg_ptrn, nominalTypeCtor.dom)
                nominalTypeCtor.codom
            }
            is Pattern.TypeAnnotatedPattern -> {
                //if (pattern.pattern is Pattern.BinderPattern && pattern.pattern.mutable)
                //    TODO()

                check(pattern.pattern, resolveTypeExpression(pattern.annotatedType))
            }
        }
    }

    fun checkPattern(pattern: Pattern, expected_type: Type): Type {
        return when (pattern) {
            is Pattern.BinderPattern -> {
                if (pattern.mutable)
                    Type.Mut(expected_type)
                else
                    expected_type
            }
            is Pattern.LiteralPattern -> {
                expect(inferPattern(pattern), expected_type)
                expected_type
            }
            is Pattern.ListPattern -> {
                if (expected_type !is Type.TupleType)
                    type_error("expected ${expected_type.prettyPrint()}, got a list pattern")
                val checked = pattern.elements.mapIndexed { i, p -> check(p, expected_type.elements[i]) }
                Type.TupleType(checked)
            }
            is Pattern.RecordPattern -> {
                if (expected_type !is Type.RecordType)
                    type_error("expected ${expected_type.prettyPrint()}, got a record pattern")
                if (pattern.fields.map { it.first } == expected_type.elements.map { it.first }) {
                    val checked = pattern.fields.mapIndexed { i, pair ->
                        Pair(
                            pair.first,
                            check(pair.second, expected_type.elements[i].second)
                        )
                    }
                    Type.RecordType(checked)
                } else
                    type_error("records don't match exactly") // TODO: lol subype
            }
            is Pattern.CtorPattern -> {
                val nominalTypeCtor = when (val r = pattern.callee.resolved) {
                    is TermLocation.DefRef -> if (r.def.is_type) null else infer_def_lazily(r.def)
                    is TermLocation.TypeParamRef -> { Type.TypeParam(r) ; TODO("resolve") }
                    else -> throw Exception("Can't possibly be a nominal type ctor")
                } as? Type.FnType
                if (expected_type !is Type.NominalType || nominalTypeCtor == null || nominalTypeCtor.codom !is Type.NominalType)
                    type_error("a constructor pattern has to produce a nominal type")
                expect(nominalTypeCtor.codom, expected_type)
                assert(pattern.args.isNotEmpty())
                val arg_ptrn: Pattern = when {
                    pattern.args.size == 1 -> pattern.args[0]
                    else -> Pattern.ListPattern(pattern.args)
                }
                check(arg_ptrn, nominalTypeCtor.dom)
                expected_type
            }
            is Pattern.TypeAnnotatedPattern -> {
                if (pattern.pattern is Pattern.BinderPattern && pattern.pattern.mutable)
                    TODO()

                // this should use the inverse typing relation
                val annotatedType = resolveTypeExpression(pattern.annotatedType)
                expect_subtype(expected_type, annotatedType)
                check(pattern.pattern, annotatedType)
                annotatedType
            }
        }
    }

    /** When a pattern is directly assigned an expression, try to co-infer the two together. This may set the types directly. */
    fun coInferPtrnExpr(pattern: Pattern, expr: Expression): Type {
        fun fallback(): Type {
            val inferred = infer(expr)
            return check(pattern, inferred)
        }

        return when (pattern) {
            is Pattern.BinderPattern -> fallback()
            is Pattern.LiteralPattern -> {
                val inferred = infer(pattern.literal)
                check(expr, inferred)
            }
            is Pattern.ListPattern -> {
                if (expr !is Expression.ListExpression)
                    return fallback()
                if (expr.elements.size != pattern.elements.size)
                    type_error("pattern & expression size do not match")
                val co_inferred = pattern.elements.zip(expr.elements).map { (p, e) -> coInferPtrnExpr(p, e) }
                val type = Type.TupleType(co_inferred)
                pattern.set_type(type)
                expr.set_type(type)
                type
            }
            is Pattern.RecordPattern -> {
                if (expr !is Expression.RecordExpression)
                    return fallback()
                val co_inferred = mutableListOf<Pair<Identifier, Type>>()
                for ((field, subpattern) in pattern.fields) {
                    val (_, subexpr) = expr.fields.find { it.first == field } ?: type_error("missing field $field")
                    co_inferred += Pair(field, coInferPtrnExpr(subpattern, subexpr))
                }
                val type = Type.RecordType(co_inferred)
                pattern.set_type(type)
                expr.set_type(type)
                type
            }
            is Pattern.CtorPattern -> {
                val ptrnType = infer(pattern) as Type.NominalType
                check(expr, ptrnType)
            }
            is Pattern.TypeAnnotatedPattern -> {
                val inferred = infer(pattern)
                check(expr, remove_mut(inferred))
            }
        }
    }

    /** Resolves TypeExprs. May still contain unspecialized type variables referring to the outer scope! */
    fun resolveTypeExpression(type: TypeExpr): Type = when (type) {
        is TypeExpr.Top -> Type.Top
        is TypeExpr.TypeNameRef -> {
            when (val resolved = type.callee.resolved) {
                is TermLocation.DefRef -> {
                    when (resolved.def.body) {
                        is Def.DefBody.ExprBody -> type_error("${type.callee.identifier} does not name a type")
                        is Def.DefBody.DataCtor -> {
                            val defType = infer_def_lazily(resolved.def) as Type.FnType
                            defType.codom
                        }
                        is Def.DefBody.TypeAlias -> {
                            infer_def_lazily(resolved.def)
                        }
                        is Def.DefBody.FnBody -> type_error("Functions are not types")
                        is Def.DefBody.Contract,
                        is Def.DefBody.Instance -> type_error("Contracts & instances are not types")
                    }
                }
                is TermLocation.TypeParamRef -> Type.TypeParam(resolved)
                else -> error("let & pattern binders are not supported in typing ... for now anyways")
            }
        }
        is TypeExpr.TypeSpecialization -> {
            val def = get_def(type.target.callee.resolved) ?: throw Exception("Can only specialize defs")
            if (def.typeParamsNames.size != type.arguments.size)
                throw Exception("Given ${type.arguments} arguments but ${def.identifier} only has ${def.typeParamsNames.size} type arguments")

            val genericType = resolveTypeExpression(type.target)
            val typeArguments = type.arguments.map { resolveTypeExpression(it) }

            if (def.body is Def.DefBody.Contract)
                findInstance(module, def, typeArguments) ?: throw Exception("No instance for contract ${def.identifier} with type arguments ${typeArguments.map { it.prettyPrint() }}")

            val substitutions = def.typeParamsNames.mapIndexed { i, _ ->
                Pair(Type.TypeParam(TermLocation.TypeParamRef(def, i)), typeArguments[i])
            }.toMap()
            specializeType(genericType, substitutions)
        }
        is TypeExpr.PrimitiveType -> Type.PrimitiveType(type.primitiveType)
        is TypeExpr.RecordType -> Type.RecordType(elements = type.elements.map { (i, t) ->
            Pair(i, resolveTypeExpression(t))
        })
        is TypeExpr.TupleType -> Type.TupleType(elements = type.elements.map { t -> resolveTypeExpression(t) })
        is TypeExpr.ArrayType -> Type.ArrayType(elementType = resolveTypeExpression(type.elementType), size = type.size)
        is TypeExpr.EnumType -> Type.EnumType(elements = type.elements.map { (i, t) ->
            Pair(i, resolveTypeExpression(t))
        })
        is TypeExpr.FnType -> Type.FnType(
            dom = resolveTypeExpression(type.dom),
            codom = resolveTypeExpression(type.codom)
        )
    }

    fun typeInstruction(inst: Instruction) {
        when (inst) {
            is Instruction.Let -> {
                coInferPtrnExpr(inst.pattern, inst.body)
            }
            is Instruction.Evaluate -> infer(inst.expr)
        }
    }

    fun inferProjection(type: Type, id: String): Type {
        return when (type) {
            is Type.RecordType -> {
                val field = type.elements.find { it.first == id }
                field?.second ?: throw Exception("No field $id in ${type.prettyPrint()}")
            }
            is Type.EnumType -> TODO("Return Option here")
            is Type.NominalType -> inferProjection(type.dataType, id)
            is Type.TypeParam -> type_error("Can't project on type parameters")
            else -> type_error("Can't project on ${type.prettyPrint()}")
        }
    }

    fun Type.getUnboundTypeParams(): List<TermLocation.TypeParamRef> {
        val filtered = mutableListOf<TermLocation.TypeParamRef>()
        val typeParams = findTypeParams(this)
        for (tp in typeParams) {
            val currentDef = current_frame().def
            if (tp.def != currentDef)
                filtered.add(tp)
        }
        return filtered
    }
}

val default_visit_all_typeable_visitor: Visitors = Visitors(
    exprVisitor = {
        true
    },
    instructionVisitor = {
        true
    },
    typeExprVisitor = {
        // TODO: Review if adding dependent types
        false
    },
    patternVisitor = {
        true
    },
    literalVisitor = {
        true
    }
)

fun reset_types(expr: Expression) = visitAST(expr, reset_types_visitors)
val reset_types_visitors: Visitors = default_visit_all_typeable_visitor.copy(
    exprVisitor = {
        it.set_type(null)
        true
    },
    instructionVisitor = {
        true
    },
    patternVisitor = {
        it.set_type(null)
        true
    },
    literalVisitor = {
        it.set_type(null)
        true
    }
)