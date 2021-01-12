package boneless

sealed class BoundIdentifier {
    data class ToDef(val def: Def) : BoundIdentifier()
    data class ToLet(val let: Instruction.Let) : BoundIdentifier()
    data class ToFnParam(val fn: Expression.Function, val param: Int) : BoundIdentifier()
}

fun bind(module: Module) {
    for (def in module.defs) {
        Binder(module).bind(def)
    }
}

class Binder(private val module: Module) {
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
        for (def in module.defs) {
            this[def.identifier] = BoundIdentifier.ToDef(def)
        }
    }

    internal fun bind(def: Def) {
        bind(def.body)
    }

    fun bind(i: Instruction) {
        when (i) {
            is Instruction.Let -> {
                bind(i.body)
                this[i.identifier] = BoundIdentifier.ToLet(i)
            }
            is Instruction.Evaluate -> bind(i.e)
        }
    }

    fun bind(expr: Expression) {
        when (expr) {
            is Expression.QuoteValue, is Expression.QuoteType -> {}

            is Expression.Cast -> { bind(expr.e); bind(expr.type) }
            is Expression.Ascription -> { bind(expr.e); bind(expr.type) }

            is Expression.ListExpression -> expr.list.forEach(::bind)
            is Expression.DictionaryExpression -> expr.dict.values.forEach(::bind)
            is Expression.Invocation -> expr.args.forEach(::bind)

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
                for ((i, param) in expr.parameters.withIndex())
                    this[param.first] = BoundIdentifier.ToFnParam(expr, i)
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
                expr.resolved = this[expr.id]
            }
        }
    }

    fun bind(type: Type) {
        when(type) {
            is Type.TypeApplication -> {
                type.resolved = this[type.name]
                type.ops.forEach(::bind)
            }
            is Type.RecordType -> { type.elements.forEach { bind(it.second) } }
            is Type.EnumType -> { type.elements.forEach { bind(it.second) } }
            is Type.TupleType -> { type.elements.forEach(::bind) }
            is Type.ArrayType -> bind(type.elementType)
        }
    }

}