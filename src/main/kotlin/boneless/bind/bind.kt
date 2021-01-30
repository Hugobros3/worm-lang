package boneless.bind

import boneless.*
import boneless.core.BuiltinFn
import boneless.core.prelude_modules

data class BindPoint private constructor(val identifier: Identifier) {
    internal var resolved_: TermLocation? = null
    val resolved: TermLocation
        get() = resolved_ ?: throw Exception("This bind point was not resolved, did the bind pass run ?")

    companion object {
        fun new(id: Identifier) = BindPoint(id)
    }
}

/** Contains information to locate the AST node referenced by an identifier */
sealed class TermLocation {
    data class DefRef(val def: Def) : TermLocation()
    data class BinderRef(val binder: Pattern.BinderPattern) : TermLocation()
    data class BuiltinFnRef(val fn: BuiltinFn) : TermLocation()
    data class TypeParamRef(val def: Def, val index: Int) : TermLocation() {
        override fun toString(): String {
            return def.typeParamsNames[index]
        }
    }
}

fun get_def(loc: TermLocation): Def? = when (loc) {
    is TermLocation.DefRef -> loc.def
    else -> null
}

fun bind(module: Module) {
    for (def in module.defs) {
        BindHelper(module).bind(def)
    }
}

class BindHelper(private val module: Module) {
    private val scopes = mutableListOf<MutableList<Pair<Identifier, TermLocation>>>()

    fun push() = scopes.add(0, mutableListOf())
    fun pop() = scopes.removeAt(0)

    operator fun get(id: Identifier): TermLocation {
        // TODO could be cool to get Levenshtein distance in there
        for (scope in scopes.reversed()) {
            val match = scope.find { it.first == id } ?: continue
            return match.second
        }
        throw Exception("unbound identifier: $id")
    }

    operator fun set(id: Identifier, bindTo: TermLocation) {
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
                TermLocation.BuiltinFnRef(builtin_fn)
        }
        if (!module.name.startsWith("Prelude"))
            for (prelude_mod in prelude_modules) {
                for (def in prelude_mod.defs) {
                    if (def.body !is Def.DefBody.Instance)
                        this[def.identifier] = TermLocation.DefRef(def)
                }
            }

        for (def in module.defs) {
            if (def.body !is Def.DefBody.Instance)
                this[def.identifier] = TermLocation.DefRef(def)
        }
    }

    internal fun bind(def: Def) {
        push()
        for ((i, typeParam) in def.typeParamsNames.withIndex()) {
            this[typeParam] = TermLocation.TypeParamRef(def, i)
        }

        when (def.body) {
            is Def.DefBody.ExprBody -> bind(def.body.expr)
            is Def.DefBody.DataCtor -> bind(def.body.datatype)
            is Def.DefBody.TypeAlias -> bind(def.body.aliasedType)
            is Def.DefBody.FnBody -> bind(def.body.fn)
            is Def.DefBody.Contract -> bind(def.body.payload)
            is Def.DefBody.Instance -> {
                def.body.contractId.resolved_ = this[def.body.contractId.identifier]
                def.body.argumentsExpr.forEach(::bind)
                bind(def.body.body)
            }
            else -> throw Exception("Unhandled ast node ${def.body}")
        }
        pop()
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
            is Expression.QuoteLiteral -> {
            }
            is Expression.QuoteType -> bind(expr.quotedType)

            is Expression.Cast -> {
                bind(expr.expr); bind(expr.destinationType)
            }
            is Expression.Ascription -> {
                bind(expr.expr); bind(expr.ascribedType)
            }

            is Expression.ListExpression -> expr.elements.forEach(::bind)
            is Expression.RecordExpression -> expr.fields.forEach { bind(it.second) }
            is Expression.Invocation -> {
                bind(expr.callee); bind(expr.arg)
            }

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
                bind(expr.param)
                bind(expr.body)
                pop()
            }
            is Expression.Sequence -> {
                push()
                for (i in expr.instructions)
                    bind(i)
                if (expr.yieldExpression != null)
                    bind(expr.yieldExpression)
                pop()
            }
            is Expression.IdentifierRef -> {
                expr.id.resolved_ = this[expr.id.identifier]
            }
            is Expression.ExprSpecialization -> {
                bind(expr.target)
                expr.arguments.forEach(::bind)
            }
            is Expression.Projection -> {
                bind(expr.expression)
            }
            else -> throw Exception("Unhandled ast node $expr")
        }
    }

    fun bind(type: TypeExpr) {
        when (type) {
            is TypeExpr.RecordType -> {
                type.elements.forEach { bind(it.second) }
            }
            is TypeExpr.EnumType -> {
                type.elements.forEach { bind(it.second) }
            }
            is TypeExpr.TupleType -> {
                type.elements.forEach(::bind)
            }
            is TypeExpr.ArrayType -> bind(type.elementType)
            is TypeExpr.TypeNameRef -> {
                type.callee.resolved_ = this[type.callee.identifier]
            }
            is TypeExpr.TypeSpecialization -> {
                bind(type.target)
                type.arguments.forEach(::bind)
            }
            is TypeExpr.PrimitiveType -> {
            }
            is TypeExpr.FnType -> {
                bind(type.dom); bind(type.codom)
            }
            else -> throw Exception("Unhandled ast node $type")
        }
    }

    fun bind(pattern: Pattern) {
        when (pattern) {
            is Pattern.BinderPattern -> {
                this[pattern.id] = TermLocation.BinderRef(pattern)
            }
            is Pattern.LiteralPattern -> {
            }
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