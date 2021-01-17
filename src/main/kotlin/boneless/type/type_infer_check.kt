package boneless.type

import boneless.*
import boneless.bind.BoundIdentifier
import boneless.util.prettyPrint

fun Type.normalize(): Type = when {
    // tuples of size 1 do not exist
    this is Type.TupleType && elements.size == 1 -> elements[0]
    // definite arrays of size 1 do not exist
    // this is Type.ArrayType && size == 1 -> elementType
    else -> this
}

fun unit_type() = Type.TupleType(emptyList())

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
    //val map = mutableMapOf<Any, Type>()
    val stack = mutableListOf<Frame>()

    class Frame(val typingWhat: Typeable)

    private fun cannot_infer(node: Typeable): Nothing {
        throw Exception("Cannot infer $node")
    }

    private fun type_error(error: String): Nothing {
        throw Exception("Error while typing: $error")
    }

    fun enter(what: Typeable): Frame {
        if (stack.any { it.typingWhat == what }) {
            //error("The type checker has run into recursive problems typing $what")
        }
        val frame = Frame(what)
        stack.add(0, frame)
        return frame
    }

    fun leave() = stack.removeAt(0)

    fun expect(type: Type, expected_type: Type) {
        if (type != expected_type)
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
            is Def -> checkDef(node, expected_type)
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
        return when (val body = def.body) {
            is Def.DefBody.ExprBody -> {
                if (body.annotatedType == null)
                    infer(body.expr)
                else
                    check(body.expr, resolveType(body.annotatedType))
            }
            is Def.DefBody.DataCtor -> {
                body.nominalType =
                    Type.NominalType(def.identifier, resolveType(body.datatype))
                Type.FnType(
                    resolveType(body.datatype),
                    body.nominalType,
                    constructorFor = body.nominalType
                )
            }
            is Def.DefBody.TypeAlias -> resolveType(body.aliasedType)
            is Def.DefBody.FnBody -> infer(body.fn)
        }
    }

    fun checkDef(def: Def, expected_type: Type): Type {
        TODO()
    }

    fun inferExpr(expr: Expression): Type {
        return when (expr) {
            is Expression.QuoteLiteral -> infer(expr.literal)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> {
                when (val r = expr.id.resolved) {
                    is BoundIdentifier.ToDef -> infer(r.def)
                    is BoundIdentifier.ToPatternBinder -> infer(r.binder)
                    is BoundIdentifier.ToBuiltinFn -> r.fn.type
                }
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
                val targetType = infer(expr.callee)
                if (targetType !is Type.FnType)
                    throw Exception("invocation callee is not a function $targetType")
                val argsType: Type = when {
                    expr.args.isEmpty() -> unit_type()
                    expr.args.size == 1 -> infer(expr.args[0])
                    else -> infer(Expression.ListExpression(expr.args))
                }
                expect(argsType, targetType.dom)
                targetType.codom
            }
            is Expression.Function -> {
                val dom = infer(expr.parameters)
                val codom = if (expr.returnTypeAnnotation == null)
                    infer(expr.body)
                else
                    check(expr.body, resolveType(expr.returnTypeAnnotation))
                Type.FnType(dom, codom)
            }
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Sequence -> {
                for (inst in expr.instructions)
                    typeInstruction(inst)
                if (expr.yieldValue == null)
                    unit_type()
                else
                    infer(expr.yieldValue)
            }
            is Expression.Conditional -> TODO()
        }
    }

    // TODO mega dumb
    fun checkExpr(expr: Expression, expected_type: Type): Type {
        /*val computed = inferExpr(expr)
        expect(computed, expected_type)
        return computed*/
        return when(expr) {
            is Expression.QuoteLiteral -> checkValue(expr.literal, expected_type)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> {
                expect( when (val r = expr.id.resolved) {
                    is BoundIdentifier.ToDef -> infer(r.def,)
                    is BoundIdentifier.ToPatternBinder -> infer(r.binder)
                    is BoundIdentifier.ToBuiltinFn -> {
                        r.fn.type
                    }
                }, expected_type)
                expected_type
            }
            is Expression.ListExpression -> {
                if (expected_type !is Type.TupleType)
                    type_error("expected type of list expression is not a tuple")
                if (expected_type.elements.size != expr.elements.size)
                    type_error("expression has ${expr.elements.size} elements but exepected type ${expected_type.prettyPrint()} has ${expected_type.elements.size}")
                val checked = expr.elements.zip(expected_type.elements).map { (e, et) -> check(e, et) }
                Type.TupleType(checked)
            }
            is Expression.RecordExpression -> TODO()
            is Expression.Invocation -> {
                val targetType = infer(expr.callee)
                if (targetType !is Type.FnType)
                    type_error("invocation callee is not a function $targetType")
                val argsType: Type = when {
                    expr.args.isEmpty() -> unit_type()
                    expr.args.size == 1 -> infer(expr.args[0])
                    else -> infer(Expression.ListExpression(expr.args))
                }
                expect(argsType, targetType.dom)
                expect(targetType.codom, expected_type)
                targetType.codom
            }
            is Expression.Function -> TODO()
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Sequence -> TODO()
            is Expression.Conditional -> TODO()
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
        return when(literal) {
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
                check(pattern.pattern, resolveType(pattern.annotatedType))
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
                expect(resolveType(pattern.annotatedType), expected_type);
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

    fun inferTypeOfBinding(boundIdentifier: BoundIdentifier): Type? {
        return when (boundIdentifier) {
            is BoundIdentifier.ToDef -> if (boundIdentifier.def.is_type) null else infer(boundIdentifier.def)
            is BoundIdentifier.ToPatternBinder -> infer(boundIdentifier.binder)
            is BoundIdentifier.ToBuiltinFn -> boundIdentifier.fn.type
        }
    }

    /** Removes type applications */
    fun resolveType(type: Type): Type = when (type) {
        is Type.TypeApplication -> {
            when (val resolved = type.callee.resolved) {
                is BoundIdentifier.ToDef -> {
                    when (resolved.def.body) {
                        is Def.DefBody.ExprBody -> type_error("${type.callee.identifier} does not name a type")
                        is Def.DefBody.DataCtor -> {
                            val defType = infer(resolved.def) as Type.FnType
                            defType.codom
                        }
                        is Def.DefBody.TypeAlias -> { infer(resolved.def) }
                        is Def.DefBody.FnBody -> infer(resolved.def) as Type.FnType
                    }
                }
                else -> error("let & pattern binders are not supported in typing ... for now anyways")
            }
        }
        is Type.PrimitiveType -> type
        is Type.RecordType -> type.copy(elements = type.elements.map { (i, t) -> Pair(i, resolveType(t)) })
        is Type.TupleType -> type.copy(elements = type.elements.map { t -> resolveType(t) })
        is Type.ArrayType -> type.copy(elementType = resolveType(type.elementType))
        is Type.EnumType -> type.copy(elements = type.elements.map { (i, t) -> Pair(i, resolveType(t)) })
        is Type.NominalType -> type.copy(dataType = resolveType(type.dataType))
        is Type.FnType -> type.copy(dom = resolveType(type.dom), codom = resolveType(type.codom))
    }

    fun typeInstruction(inst: Instruction) {
        when (inst) {
            is Instruction.Let -> { coInferPtrnExpr(inst.pattern, inst.body) }
            is Instruction.Evaluate -> infer (inst.expr)
        }
    }
}