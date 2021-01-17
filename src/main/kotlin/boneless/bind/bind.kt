package boneless.bind

import boneless.*
import boneless.core.BuiltinFn
import boneless.type.Type

data class BindPoint private constructor(val identifier: Identifier, internal var resolved_: BoundIdentifier? = null) {
    val resolved: BoundIdentifier get() = resolved_ ?: throw Exception("This bind point was not resolved, did the bind pass run ?")
    companion object {
        fun new(id: Identifier) = BindPoint(id, null)
    }
}

sealed class BoundIdentifier {
    data class ToDef(val def: Def) : BoundIdentifier()
    data class ToPatternBinder(val binder: Pattern.BinderPattern) : BoundIdentifier()
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
            this[builtin_fn.name.toLowerCase()] =
                BoundIdentifier.ToBuiltinFn(builtin_fn)
        }
        for (def in module.defs) {
            this[def.identifier] = BoundIdentifier.ToDef(def)
        }
    }

    internal fun bind(def: Def) {
        when(def.body) {
            is Def.DefBody.ExprBody -> bind(def.body.expr)
            is Def.DefBody.DataCtor -> bind(def.body.datatype)
            is Def.DefBody.TypeAlias -> bind(def.body.aliasedType)
            is Def.DefBody.FnBody -> bind(def.body.fn)
            else -> throw Exception("Unhandled ast node ${def.body}")
        }
    }

    fun bind(inst: Instruction) {
        when (inst) {
            is Instruction.Let -> {
                bind(inst.body)
                bind(inst.pattern)
            }
            is Instruction.Evaluate -> bind(inst.expr)
            else -> throw Exception("Unhandled ast node $inst")
        }
    }

    fun bind(expr: Expression) {
        when (expr) {
            is Expression.QuoteLiteral -> {}
            is Expression.QuoteType -> bind(expr.quotedType)

            is Expression.Cast -> { bind(expr.expr); bind(expr.destinationType) }
            is Expression.Ascription -> { bind(expr.expr); bind(expr.ascribedType) }

            is Expression.ListExpression -> expr.elements.forEach(::bind)
            is Expression.RecordExpression -> expr.fields.forEach { bind(it.second) }
            is Expression.Invocation -> { bind(expr.callee) ; expr.args.forEach(::bind) }

            is Expression.Conditional -> {
                bind(expr.condition)
                bind(expr.ifTrue)
                bind(expr.ifFalse)
            }
            is Expression.WhileLoop -> {
                bind(expr.loopCondition)
                bind(expr.body)
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
                expr.id.resolved_ = this[expr.id.identifier]
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
            is Type.FnType -> { bind(type.dom) ; bind(type.codom)}
            is Type.NominalType -> throw Exception("Inacessible: parser emits TypeApplications and type inference generates those !")
            else -> throw Exception("Unhandled ast node $type")
        }
    }

    fun bind(pattern: Pattern) {
        when(pattern) {
            is Pattern.BinderPattern -> {
                this[pattern.id] = BoundIdentifier.ToPatternBinder(pattern)
            }
            is Pattern.LiteralPattern -> {}
            is Pattern.ListPattern -> pattern.elements.forEach(::bind)
            is Pattern.RecordPattern -> pattern.fields.forEach { bind(it.second) }
            is Pattern.CtorPattern -> {
                pattern.callee.resolved_ = this[pattern.callee.identifier]
                pattern.args.forEach(::bind)
            }
            is Pattern.TypeAnnotatedPattern -> {
                bind(pattern.pattern)
                bind(pattern.annotatedType)
            }
            else -> throw Exception("Unhandled ast node $pattern")
        }
    }

}