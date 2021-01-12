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

class TypeChecker(val module: Module) {
    val map = mutableMapOf<Any, Type>()
    val stack = mutableListOf<Frame>()

    class Frame(val typingWhat: Any)

    private fun cannot_infer(node: Any): Nothing {
        throw Exception("Cannot infer")
    }
    private fun type_error(error: String): Nothing {
        throw Exception("Error while typing: $error")
    }

    fun enter(what: Any): Boolean {
        val oh_no = stack.any { it.typingWhat == what }
        if (oh_no) {
            //error("The type checker has run into recursive problems typing $what")
        }
        stack.add(0, Frame(what))
        return oh_no
    }

    fun leave() = stack.removeAt(0)
    fun <R: Type> infer_wrap(what: Any, f: () -> R): R {
        var type = map[what]
        if (type != null)
            return type as R
        enter(what)
        type = f()
        map[what] = type
        leave()
        return type
    }
    fun <R: Type> check_wrap(what: Any, f: () -> R): R {
        var type = map[what]
        //if (type != null)
        //    throw Exception("Called check but a type already exists")
        enter(what)
        type = f()
        map[what] = type
        leave()
        return type
    }

    fun type() {
        for (def in module.defs) {
            def.type = infer(def)
        }
    }

    fun expect(type: Type, expected_type: Type) {
        if (type != expected_type)
            throw Exception("Expected $expected_type but got $type")
    }

    fun infer(def: Def): Type = infer_wrap(def) {
        when(val body = def.body) {
            is Def.DefBody.ExprBody -> { infer(body.expr) }
            is Def.DefBody.DataCtor -> {
                body.nominalType = Type.NominalType(def.identifier, body.type)
                Type.FnType(body.type, body.nominalType, constructorFor = body.nominalType)
            }
            is Def.DefBody.TypeAlias -> body.type
        }
    }

    fun infer(expr: Expression): Type = infer_wrap(expr) {
        when (expr) {
            is Expression.QuoteValue -> infer(expr.value)
            is Expression.QuoteType -> TODO()
            is Expression.IdentifierRef -> {
                when(val r = expr.resolved) {
                    is BoundIdentifier.ToDef -> infer(r.def)
                    is BoundIdentifier.ToLet -> infer(r.let)
                    is BoundIdentifier.ToPatternBinder -> infer(r.binder)
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
                val targetType = infer(expr.target)
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
                val codom = infer(expr.body)
                Type.FnType(dom, codom)
            }
            is Expression.Ascription -> TODO()
            is Expression.Cast -> TODO()
            is Expression.Sequence -> {
                for (inst in expr.instructions)
                    infer(inst)
                if(expr.yieldValue == null)
                    unit_type()
                else
                    infer(expr.yieldValue)
            }
            is Expression.Conditional -> TODO()
        }
    }

    fun infer(value: Value): Type = infer_wrap(value) {
        when (value) {
            is Value.NumLiteral -> if (value.num.toDouble() == value.num.toInt().toDouble()) Type.PrimitiveType(PrimitiveType.I32) else Type.PrimitiveType(PrimitiveType.F32)
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

    fun infer(pattern: Pattern): Type = infer_wrap(pattern) {
        when(pattern) {
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
                val fn_type: Type = when(val r = pattern.resolved) {
                    is BoundIdentifier.ToDef -> infer(r.def)
                    is BoundIdentifier.ToLet -> TODO()
                    is BoundIdentifier.ToPatternBinder -> TODO()
                }
                if (fn_type !is Type.FnType)
                    type_error("$fn_type is not a function type")
                assert(pattern.args.size > 0)
                val arg_ptrn: Pattern = when {
                    //pattern.args.isEmpty() -> Pattern.Literal(Value.ListLiteral(emptyList()))
                    pattern.args.size == 1 -> pattern.args[0]
                    else -> Pattern.ListPattern(pattern.args)
                }
                check(arg_ptrn, fn_type.dom)
                //expect(arg_type, fn_type.dom)
                fn_type.codom
            }
            is Pattern.TypeAnnotatedPattern -> {
                val type = infer(pattern.type)
                check(pattern.inside, type)
            }
        }
    }

    fun check(pattern: Pattern, expected_type: Type): Type = check_wrap(pattern) {
        when(pattern) {
            is Pattern.Binder -> expected_type
            is Pattern.Literal -> {
                expect(infer(pattern), expected_type)
                expected_type
            }
            is Pattern.ListPattern -> {
                if (expected_type !is Type.TupleType)
                    type_error("expected not-a-list")
                val checked = pattern.list.mapIndexed { i, p -> check(p, expected_type.elements[i]) }
                Type.TupleType(checked)
            }
            is Pattern.RecordPattern -> TODO()
            is Pattern.CtorPattern -> TODO()
            is Pattern.TypeAnnotatedPattern -> TODO()
        }
    }

    fun infer(type: Type): Type = infer_wrap(type) {
        when(type) {
            is Type.TypeApplication -> {
                when(val resolved = type.resolved) {
                    is BoundIdentifier.ToDef -> {
                        val defType = infer(resolved.def)
                        if (!resolved.def.is_type)
                            type_error("${type.name} does not name a type")
                        defType
                    }
                    // (let & pattern binders are not supported in typing ... for now anyways)
                    else -> type_error("${type.name} does not name a type")
                }
            }
            else -> type
        }
    }

    fun infer(inst: Instruction): Type = infer_wrap(inst) {
        when(inst) {
            is Instruction.Let -> TODO()
            is Instruction.Evaluate -> TODO()
        }
    }

    /*fun check(inst: Instruction): Type = check_wrap(inst) {
        when(inst) {
            is Instruction.Let -> {
                val expected_type = inst.type
                if (expected_type != null)
                    check(inst.body, expected_type)
            }
            is Instruction.Evaluate -> {
                val inferred = infer(inst.e)
            }
        }
    }*/

    fun check(expr: Expression, expected_type: Type) {

    }
}