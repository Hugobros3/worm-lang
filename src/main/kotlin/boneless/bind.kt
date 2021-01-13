package boneless

data class BindPoint private constructor(val identifier: Identifier, internal var resolved_: BoundIdentifier? = null) {
    val resolved: BoundIdentifier get() = resolved_ ?: throw Exception("This bind point was not resolved, did the bind pass run ?")
    companion object {
        fun new(id: Identifier) = BindPoint(id, null)
    }
}

sealed class BoundIdentifier {
    data class ToDef(val def: Def) : BoundIdentifier()
    data class ToPatternBinder(val binder: Pattern.Binder) : BoundIdentifier()
    data class ToBuiltinFn(val fn: BuiltinFn) : BoundIdentifier()
}

fun bind(module: Module) {
    for (def in module.defs) {
        BindHelper(module).bind(def)
    }
}

class BindHelper(private val module: Module) {
    private val scopes = mutableListOf<MutableList<Pair<Identifier, BoundIdentifier>>>()

    fun push() = scopes.add(0, mutableListOf())
    fun pop() = scopes.removeAt(0)

    operator fun get(id: Identifier): BoundIdentifier {
        // TODO could be cool to get Levenshtein distance in there
        for (scope in scopes.reversed()) {
            val match = scope.find { it.first == id } ?: continue
            return match.second
        }
        throw Exception("unbound identifier: $id")
    }

    operator fun set(id: Identifier, bindTo: BoundIdentifier) {
        for (scope in scopes.reversed()) {
            val match = scope.find { it.first == id } ?: continue
            throw Exception("shadowed identifier: $id")
        }
        scopes.last().add(Pair(id, bindTo))
    }

    init {
        push()
        for (builtin_fn in BuiltinFn.values()) {
            this[builtin_fn.name.toLowerCase()] = BoundIdentifier.ToBuiltinFn(builtin_fn)
        }
        for (def in module.defs) {
            this[def.identifier] = BoundIdentifier.ToDef(def)
        }
    }

    internal fun bind(def: Def) {
        when(def.body) {
            is Def.DefBody.ExprBody -> bind(def.body.expr)
            is Def.DefBody.DataCtor -> bind(def.body.type)
            is Def.DefBody.TypeAlias -> bind(def.body.type)
            is Def.DefBody.FnBody -> bind(def.body.fn)
            else -> throw Exception("Unhandled ast node ${def.body}")
        }
    }

    fun bind(inst: Instruction) {
        when (inst) {
            is Instruction.Let -> {
                bind(inst.body)
                bind(inst.binder)
                //this[inst.identifier] = BoundIdentifier.ToLet(inst)
            }
            is Instruction.Evaluate -> bind(inst.e)
            else -> throw Exception("Unhandled ast node $inst")
        }
    }

    fun bind(expr: Expression) {
        when (expr) {
            is Expression.QuoteValue -> {}
            is Expression.QuoteType -> bind(expr.quotedType)

            is Expression.Cast -> { bind(expr.e); bind(expr.destinationType) }
            is Expression.Ascription -> { bind(expr.e); bind(expr.ascribedType) }

            is Expression.ListExpression -> expr.list.forEach(::bind)
            is Expression.RecordExpression -> expr.fields.forEach { bind(it.second) }
            is Expression.Invocation -> { bind(expr.callee) ; expr.args.forEach(::bind) }

            is Expression.Conditional -> {
                bind(expr.condition)
                push()
                bind(expr.ifTrue)
                pop()
                push()
                bind(expr.ifFalse)
                pop()
            }
            is Expression.Function -> {
                push()
                bind(expr.parameters)
                bind(expr.body)
                pop()
            }
            is Expression.Sequence -> {
                push()
                for (i in expr.instructions)
                    bind(i)
                if(expr.yieldValue != null)
                    bind(expr.yieldValue)
                pop()
            }
            is Expression.IdentifierRef -> {
                expr.referenced.resolved_ = this[expr.referenced.identifier]
            }
            else -> throw Exception("Unhandled ast node $expr")
        }
    }

    fun bind(type: Type) {
        when(type) {
            is Type.RecordType -> { type.elements.forEach { bind(it.second) } }
            is Type.EnumType -> { type.elements.forEach { bind(it.second) } }
            is Type.TupleType -> { type.elements.forEach(::bind) }
            is Type.ArrayType -> bind(type.elementType)
            is Type.TypeApplication -> {
                type.callee.resolved_ = this[type.callee.identifier]
                type.args.forEach(::bind)
            }
            is Type.PrimitiveType -> {}
            else -> throw Exception("Unhandled ast node $type")
        }
    }

    fun bind(pattern: Pattern) {
        when(pattern) {
            is Pattern.Binder -> {
                this[pattern.id] = BoundIdentifier.ToPatternBinder(pattern)
            }
            is Pattern.Literal -> {}
            is Pattern.ListPattern -> pattern.list.forEach(::bind)
            is Pattern.RecordPattern -> pattern.fields.forEach { bind(it.second) }
            is Pattern.CtorPattern -> {
                pattern.callee.resolved_ = this[pattern.callee.identifier]
                pattern.args.forEach(::bind)
            }
            is Pattern.TypeAnnotatedPattern -> {
                bind(pattern.inside)
                bind(pattern.annotatedType)
            }
            else -> throw Exception("Unhandled ast node $pattern")
        }
    }

}