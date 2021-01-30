package boneless.type

import boneless.*
import boneless.bind.TermLocation
import boneless.bind.get_def
import boneless.util.prettyPrint

fun Type.normalize(): Type = when {
    // tuples of size 1 do not exist
    this is Type.TupleType && elements.size == 1 -> elements[0]
    // definite arrays of size 1 do not exist
    // this is Type.ArrayType && size == 1 -> elementType
    else -> this
}

fun type(module: Module) {
    TypeChecker(module).type()
}

interface Typeable {
    val type: Type?
    fun set_type(type: Type)
}

fun typeable() = object : Typeable {
    private var _type: Type? = null

    override val type: Type?
        get() = _type

    override fun set_type(type: Type) {
        if (_type != null)
            throw Exception("Attempted to set type twice")
        _type = type
    }

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

    fun coerce(expr: Expression, type: Type, expected_type: Type) {
        if (type == expected_type)
            return Unit
        if (isSubtype(type, expected_type)) {
            expr.deducedImplicitCast = expected_type
        } else
            throw Exception("Expected $expected_type but got $type")
    }

    fun infer(node: Typeable): Type {
        node.type?.let { return (it) }
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
        node.type?.let { throw Exception("The type already exists!") }
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
            infer(def)
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
            is Def.DefBody.FnBody -> infer(body.fn)
            is Def.DefBody.Contract -> resolveTypeExpression(body.payload)
            is Def.DefBody.Instance -> {
                val contract_def = get_def(body.contractId.resolved)
                contract_def?.body as? Def.DefBody.Contract
                    ?: throw Exception("Instances must reference contract definitions")
                val contract_type = infer(contract_def)

                body.arguments = body.argumentsExpr.map { resolveTypeExpression(it) }

                val substitutions = contract_def.typeParamsNames.mapIndexed { i, _ ->
                    Pair(
                        Type.TypeParam(
                            TermLocation.TypeParamRef(
                                contract_def,
                                i
                            )
                        ) as Type, body.arguments[i]
                    )
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
                    is TermLocation.DefRef -> infer(r.def)
                    is TermLocation.BinderRef -> infer(r.binder)
                    is TermLocation.BuiltinFnRef -> resolveTypeExpression(r.fn.typeExpr) // TODO this is garbage
                    is TermLocation.TypeParamRef -> Type.TypeParam(r)
                }
            }
            is Expression.Projection -> {
                val inside = infer(expr.expression)
                inferProjection(inside, expr.id)
            }
            is Expression.ExprSpecialization -> {
                infer(expr.target)
                val def = (expr.target.id.resolved as? TermLocation.DefRef)?.def
                    ?: throw Exception("Can only specialize defs")
                if (def.typeParamsNames.size != expr.arguments.size)
                    throw Exception("Given ${expr.arguments} arguments but ${def.identifier} only has ${def.typeParamsNames.size} type arguments")

                val typeArguments = expr.arguments.map { resolveTypeExpression(it) }

                if (def.body is Def.DefBody.Contract) findInstance(module, def, typeArguments)
                        ?: throw Exception("No instance for contract ${def.identifier} with type arguments ${typeArguments.map { it.prettyPrint() }}")

                val genericType = infer(def)
                val substitutions = def.typeParamsNames.mapIndexed { i, _ ->
                    Pair(Type.TypeParam(TermLocation.TypeParamRef(def, i)) as Type, typeArguments[i])
                }.toMap()
                specializeType(genericType, substitutions)
            }
            is Expression.ListExpression -> {
                val inferred = expr.elements.map { infer(it) }
                assert(inferred.size > 1)
                when {
                    //inferred.isEmpty() -> unit_type()
                    else -> Type.TupleType(inferred)
                }
            }
            is Expression.RecordExpression -> {
                val inferred = expr.fields.map { (f, e) -> Pair(f, infer(e)) }
                Type.RecordType(inferred)
            }
            is Expression.Invocation -> {
                if (needTypeParamInference(expr.callee)) {
                    val argsType = infer(expr.arg)
                    val targetType = check(expr.callee, Type.FnType(argsType, Type.Top))
                    if (targetType !is Type.FnType)
                        throw Exception("invocation callee is not a function $targetType")
                    targetType.codom
                } else {
                    val targetType = infer(expr.callee)
                    if (targetType !is Type.FnType)
                        throw Exception("invocation callee is not a function $targetType")

                    if (targetType.containsUnboundTypeParams()) {
                        assert(false)
                    }

                    val argsType = infer(expr.arg)
                    expect(argsType, targetType.dom)
                    targetType.codom
                }
            }
            is Expression.Function -> {
                val dom = infer(expr.param)
                val codom = if (expr.returnTypeAnnotation == null)
                    infer(expr.body)
                else
                    check(expr.body, resolveTypeExpression(expr.returnTypeAnnotation))
                Type.FnType(dom, codom)
            }
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
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
        }
    }

    fun checkExpr(expr: Expression, expected_type: Type): Type {
        return when (expr) {
            is Expression.QuoteLiteral -> checkValue(expr.literal, expected_type)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> {
                expect(
                    when (val r = expr.id.resolved) {
                        is TermLocation.DefRef -> {
                            val t = infer(r.def)
                            val def = r.def
                            if (def.typeParamsNames.isNotEmpty()) {
                                val substitutions = unify(t, expected_type)
                                val st = specializeType(t, substitutions)
                                val typeArguments = def.typeParams.map { substitutions[it]!! }
                                expr.deducedImplicitSpecializationArguments = typeArguments
                                if (def.body is Def.DefBody.Contract)
                                    findInstance(module, def, typeArguments) ?: throw Exception("No instance for contract ${def.identifier} with type arguments ${typeArguments.map { it.prettyPrint() }}")
                                coerce(expr, st, expected_type)
                                return st
                            }
                            t
                        }
                        is TermLocation.BinderRef -> infer(r.binder)
                        is TermLocation.BuiltinFnRef -> {
                            resolveTypeExpression(r.fn.typeExpr)
                        }
                        is TermLocation.TypeParamRef -> Type.TypeParam(r)
                    }, expected_type
                )
                expected_type
            }
            is Expression.Projection -> {
                val t = inferExpr(expr)

                if (expr.expression is Expression.IdentifierRef) {
                    val def = get_def(expr.expression.id.resolved)
                    if (def != null && def.typeParamsNames.isNotEmpty()) {
                        val substitutions = unify(t, expected_type)
                        val st = specializeType(t, substitutions)
                        val typeArguments = def.typeParams.map { substitutions[it]!! }
                        expr.expression.deducedImplicitSpecializationArguments = typeArguments

                        if (def.body is Def.DefBody.Contract)
                            findInstance(module, def, typeArguments) ?: throw Exception("No instance for contract ${def.identifier} with type arguments ${typeArguments.map { it.prettyPrint() }}")

                        coerce(expr.expression, st, expected_type)
                        return st
                    }
                }

                expect(t, expected_type)
                t
            }
            is Expression.ExprSpecialization -> {
                val t = inferExpr(expr)
                expect(t, expected_type)
                t
            }
            is Expression.ListExpression -> {
                if (expected_type !is Type.TupleType)
                    type_error("expected type of list expression is not a tuple")
                if (expected_type.elements.size != expr.elements.size)
                    type_error("expression has ${expr.elements.size} elements but expected type ${expected_type.prettyPrint()} has ${expected_type.elements.size}")
                val checked = expr.elements.zip(expected_type.elements).map { (e, et) -> check(e, et) }
                Type.TupleType(checked)
            }
            is Expression.RecordExpression -> {
                if (expected_type !is Type.RecordType)
                    type_error("expected type of record expression is not a record")
                if (expected_type.elements.size != expr.fields.size)
                    type_error("expression has ${expr.fields.size} elements but expected type ${expected_type.prettyPrint()} has ${expected_type.elements.size}")
                val checked = expected_type.elements.mapIndexed { i, (fieldName, expFieldType) ->
                    if (fieldName != expr.fields[i].first)
                        throw Exception("Field names $fieldName and ${expr.fields[i].first} do not match")
                    Pair(fieldName, check(expr.fields[i].second, expFieldType))
                }
                Type.RecordType(checked)
            }
            is Expression.Invocation -> {
                if (needTypeParamInference(expr.callee)) {
                    val argsType = infer(expr.arg)
                    val targetType = check(expr.callee, Type.FnType(argsType, Type.Top))
                    if (targetType !is Type.FnType)
                        throw Exception("invocation callee is not a function $targetType")
                    expect(targetType.codom, expected_type)
                    targetType.codom
                } else {
                    val targetType = infer(expr.callee)
                    if (targetType !is Type.FnType)
                        type_error("invocation callee is not a function $targetType")
                    check(expr.arg, targetType.dom)
                    expect(targetType.codom, expected_type)
                    targetType.codom
                }
            }
            is Expression.Function -> {
                if (expected_type !is Type.FnType)
                    type_error("expected type of fn expression is not a function type")
                val dom = check(expr.param, expected_type.dom)
                val codom = check(expr.body, expected_type.codom)
                Type.FnType(dom, codom)
            }
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Sequence -> {
                for (inst in expr.instructions)
                    typeInstruction(inst)
                if (expr.yieldExpression == null) {
                    expect(unit_type(), expected_type); expected_type
                } else
                    check(expr.yieldExpression, expected_type)
            }
            is Expression.Conditional -> TODO()
            is Expression.WhileLoop -> TODO()
        }
    }

    fun inferValue(literal: Literal): Type {
        return when (literal) {
            is Literal.NumLiteral -> if (literal.number.toIntOrNull() != null) Type.PrimitiveType(
                PrimitiveTypeEnum.I32
            ) else Type.PrimitiveType(PrimitiveTypeEnum.F32)
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
            is Literal.NumLiteral -> {
                if (expected_type !is Type.PrimitiveType)
                    type_error("Cannot type numerical literal '${literal.number}' as a ${expected_type.prettyPrint()}")
                expected_type
            }
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
            is Pattern.BinderPattern -> cannot_infer(pattern)
            is Pattern.LiteralPattern -> infer(pattern.literal)
            is Pattern.ListPattern -> {
                val inferred = pattern.elements.map { infer(it) }
                assert(inferred.size > 1)
                when {
                    //inferred.isEmpty() -> unit_type()
                    //inferred.all { it == inferred[0] } -> Type.ArrayType(inferred[0], inferred.size)
                    else -> Type.TupleType(inferred)
                }
            }
            is Pattern.RecordPattern -> {
                val inferred = pattern.fields.map { (f, p) -> Pair(f, infer(p)) }
                Type.RecordType(inferred)
            }
            is Pattern.CtorPattern -> {
                val nominalTypeCtor = inferTypeOfBinding(pattern.callee.resolved) as? Type.FnType
                if (nominalTypeCtor == null)
                    type_error("a constructor pattern has to produce a nominal type")
                assert(pattern.args.size > 0)
                val arg_ptrn: Pattern = when {
                    pattern.args.size == 1 -> pattern.args[0]
                    else -> Pattern.ListPattern(pattern.args)
                }
                check(arg_ptrn, nominalTypeCtor.dom)
                nominalTypeCtor.codom as Type.NominalType
            }
            is Pattern.TypeAnnotatedPattern -> {
                check(pattern.pattern, resolveTypeExpression(pattern.annotatedType))
            }
        }
    }

    fun checkPattern(pattern: Pattern, expected_type: Type): Type {
        return when (pattern) {
            is Pattern.BinderPattern -> expected_type
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
                val nominalTypeCtor = inferTypeOfBinding(pattern.callee.resolved) as? Type.FnType
                if (expected_type !is Type.NominalType || nominalTypeCtor == null)
                    type_error("a constructor pattern has to produce a nominal type")
                expect(nominalTypeCtor.codom, expected_type)
                assert(pattern.args.size > 0)
                val arg_ptrn: Pattern = when {
                    pattern.args.size == 1 -> pattern.args[0]
                    else -> Pattern.ListPattern(pattern.args)
                }
                check(arg_ptrn, nominalTypeCtor.dom)
                expected_type
            }
            is Pattern.TypeAnnotatedPattern -> {
                // test me
                expect(resolveTypeExpression(pattern.annotatedType), expected_type);
                check(pattern.pattern, expected_type)
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
                check(expr, infer(pattern))
            }
        }
    }

    fun inferTypeOfBinding(boundIdentifier: TermLocation): Type? {
        return when (boundIdentifier) {
            is TermLocation.DefRef -> if (boundIdentifier.def.is_type) null else infer(boundIdentifier.def)
            is TermLocation.BinderRef -> infer(boundIdentifier.binder)
            is TermLocation.BuiltinFnRef -> resolveTypeExpression(boundIdentifier.fn.typeExpr)
            is TermLocation.TypeParamRef -> Type.TypeParam(boundIdentifier)
        }
    }

    /** Resolves TypeExprs */
    fun resolveTypeExpression(type: TypeExpr): Type = when (type) {
        is TypeExpr.TypeNameRef -> {
            when (val resolved = type.callee.resolved) {
                is TermLocation.DefRef -> {
                    when (resolved.def.body) {
                        is Def.DefBody.ExprBody -> type_error("${type.callee.identifier} does not name a type")
                        is Def.DefBody.DataCtor -> {
                            val defType = infer(resolved.def) as Type.FnType
                            defType.codom
                        }
                        is Def.DefBody.TypeAlias -> {
                            infer(resolved.def)
                        }
                        is Def.DefBody.FnBody -> infer(resolved.def) as Type.FnType
                        is Def.DefBody.Contract,
                        is Def.DefBody.Instance -> throw Exception("Contracts & instances are not types")
                    }
                }
                is TermLocation.TypeParamRef -> Type.TypeParam(resolved)
                else -> error("let & pattern binders are not supported in typing ... for now anyways")
            }
        }
        is TypeExpr.TypeSpecialization -> {
            val def = (type.target.callee.resolved as? TermLocation.DefRef)?.def
                ?: throw Exception("Can only specialize defs")
            if (def.typeParamsNames.size != type.arguments.size)
                throw Exception("Given ${type.arguments} arguments but ${def.identifier} only has ${def.typeParamsNames.size} type arguments")
            val typeArguments = type.arguments.map { resolveTypeExpression(it) }

            if (def.body is Def.DefBody.Contract)
                findInstance(module, def, typeArguments)
                    ?: throw Exception("No instance for contract ${def.identifier} with type arguments ${typeArguments.map { it.prettyPrint() }}")

            val genericType = resolveTypeExpression(type.target)
            val substitutions = def.typeParamsNames.mapIndexed { i, _ ->
                Pair(
                    Type.TypeParam(TermLocation.TypeParamRef(def, i)) as Type,
                    typeArguments[i]
                )
            }.toMap()
            specializeType(genericType, substitutions)
        }
        is TypeExpr.PrimitiveType -> Type.PrimitiveType(type.primitiveType)
        is TypeExpr.RecordType -> Type.RecordType(elements = type.elements.map { (i, t) ->
            Pair(
                i,
                resolveTypeExpression(t)
            )
        })
        is TypeExpr.TupleType -> Type.TupleType(elements = type.elements.map { t -> resolveTypeExpression(t) })
        is TypeExpr.ArrayType -> Type.ArrayType(elementType = resolveTypeExpression(type.elementType), size = type.size)
        is TypeExpr.EnumType -> Type.EnumType(elements = type.elements.map { (i, t) ->
            Pair(
                i,
                resolveTypeExpression(t)
            )
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
            is Type.EnumType -> TODO()
            is Type.NominalType -> inferProjection(type.dataType, id)
            is Type.TypeParam -> throw Exception("Can't project on type parameters")
            else -> throw Exception("Can't project on ${type.javaClass.simpleName}")
        }
    }

    fun Type.containsUnboundTypeParams(): Boolean {
        val typeParams = findTypeParams(this)
        for (tp in typeParams) {
            val currentDef = current_frame().def
            if (tp.def != currentDef)
                return true
        }
        return false
    }
}