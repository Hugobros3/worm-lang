package boneless

fun Type.normalize(): Type = when {
    // tuples of size 1 do not exist
    this is Type.TupleType && elements.size == 1 -> elements[0]
    // definite arrays of size 1 do not exist
    // this is Type.ArrayType && size == 1 -> elementType
    else -> this
}

fun isSubtype(T: Type, S: Type): Boolean {
    return when {
        // A definite array is a subtype of a tuple type iff that tuple type is not unit, and it has the same data layout as the definite array
        T is Type.ArrayType && S is Type.TupleType && T.isDefinite && T.size == S.elements.size && S.elements.all { it == T.elementType } -> true
        // And vice versa
        T is Type.TupleType && S is Type.ArrayType && S.isDefinite && S.size == T.elements.size && T.elements.all { it == S.elementType } -> true

        // A struct type T is a subtype of a nameless subtype S if they contain the same things, in the same order
        T is Type.RecordType && S is Type.TupleType && T.elements.map { it.second } == S.elements -> true

        // A record type T is a subtype of another record type S iff the elements in T are a superset of the elements in S
        T is Type.RecordType && S is Type.RecordType && T.elements.containsAll(S.elements) -> true
        else -> false
    }
}

fun unit_type() = Type.TupleType(emptyList())

enum class PrimitiveType(val size: Int) {
    /*
    U8(1),
    U16(2),
    U32(4),
    U64(8),*/

    // Not needed for now
    //I8(1),
    //I16(2),
    I32(4),
    //I64(8),

    F32(4),
    // F64(8)
    ;
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
            is Value -> inferValue(node)
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
            is Value -> checkValue(node, expected_type)
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
                body.nominalType = Type.NominalType(def.identifier, resolveType(body.type))
                Type.FnType(resolveType(body.type), body.nominalType, constructorFor = body.nominalType)
            }
            is Def.DefBody.TypeAlias -> body.type
            is Def.DefBody.FnBody -> infer(body.fn)
        }
    }

    fun checkDef(def: Def, expected_type: Type): Type {
        TODO()
    }

    fun inferExpr(expr: Expression): Type {
        return when (expr) {
            is Expression.QuoteValue -> infer(expr.value)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> {
                when (val r = expr.referenced.resolved) {
                    is BoundIdentifier.ToDef -> infer(r.def)
                    is BoundIdentifier.ToPatternBinder -> infer(r.binder)
                    is BoundIdentifier.ToBuiltinFn -> r.fn.type
                }
            }
            is Expression.ListExpression -> {
                val inferred = expr.list.map { infer(it) }
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
                val argsType: Type = when {
                    expr.args.isEmpty() -> unit_type()
                    expr.args.size == 1 -> infer(expr.args[0])
                    else -> infer(Expression.ListExpression(expr.args))
                }
                if (targetType !is Type.FnType)
                    throw Exception("Tried to call non-functional type: $targetType")
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
        val computed = inferExpr(expr)
        expect(computed, expected_type)
        return computed
    }

    fun inferValue(value: Value): Type {
        return when (value) {
            is Value.NumLiteral -> if (value.num.toDouble() == value.num.toInt().toDouble()) Type.PrimitiveType(
                PrimitiveType.I32
            ) else Type.PrimitiveType(PrimitiveType.F32)
            is Value.StrLiteral -> TODO()
            is Value.ListLiteral -> {
                val types = value.list.map { infer(it) }
                when {
                    types.isEmpty() -> unit_type()
                    //types.isNotEmpty() && types.all { it == types[0] } -> Type.ArrayType(types[0], types.size)
                    else -> Type.TupleType(types)
                }
            }
            is Value.RecordLiteral -> {
                val types = value.fields.map { (f, v) -> Pair(f, infer(v)) }
                Type.RecordType(types)
            }
        }
    }

    fun checkValue(value: Value, expected_type: Type): Type {
        TODO()
    }

    fun inferPattern(pattern: Pattern): Type {
        return when (pattern) {
            is Pattern.Binder -> cannot_infer(pattern)
            is Pattern.Literal -> infer(pattern.value)
            is Pattern.ListPattern -> {
                val inferred = pattern.list.map { infer(it) }
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
                nominalTypeCtor.codom
            }
            is Pattern.TypeAnnotatedPattern -> {
                check(pattern.inside, resolveType(pattern.annotatedType))
            }
        }
    }

    fun checkPattern(pattern: Pattern, expected_type: Type): Type {
        return when (pattern) {
            is Pattern.Binder -> expected_type
            is Pattern.Literal -> {
                expect(inferPattern(pattern), expected_type)
                expected_type
            }
            is Pattern.ListPattern -> {
                if (expected_type !is Type.TupleType)
                    type_error("expected not-a-list")
                val checked = pattern.list.mapIndexed { i, p -> check(p, expected_type.elements[i]) }
                Type.TupleType(checked)
            }
            is Pattern.RecordPattern -> {
                if (expected_type !is Type.RecordType)
                    type_error("expected not-a-record")
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
            is Pattern.TypeAnnotatedPattern -> TODO()
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
                    when (val body = resolved.def.body) {
                        is Def.DefBody.ExprBody -> type_error("${type.callee.identifier} does not name a type")
                        is Def.DefBody.DataCtor -> {
                            val defType = infer(resolved.def) as Type.FnType
                            defType.codom
                        }
                        is Def.DefBody.TypeAlias -> {
                            infer(resolved.def)
                        }
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
            is Instruction.Let -> {
            }
            is Instruction.Evaluate -> TODO()
        }
    }
}