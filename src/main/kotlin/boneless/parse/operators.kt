package boneless.parse

import boneless.Expression
import boneless.bind.BindPoint

enum class PrefixOperator(val token: Keyword, val rewrite_: String) {
    Negate(Keyword.Minus, "Neg.neg"),

    Not(Keyword.Not, "not"),
    Reference(Keyword.Reference, "ref"),
    Dereference(Keyword.Dereference, "deref"),
    ;

    fun rewrite() = run {
        val s = rewrite_.split(".")
        if (s.size == 1)
            Expression.IdentifierRef(BindPoint.new(s[0]))
        else
            Expression.Projection(Expression.IdentifierRef(BindPoint.new(s[0])), s[1])
    }
}

enum class InfixOperator(val token: Keyword, val priority: Int, val rewrite_: String?) {
    // Map(Keyword.Map, 0, null),
    Ascription(Keyword.TypeAnnotation, 80, null),
    Cast(Keyword.As, 80, null),

    Add(Keyword.Plus, 40, "Add.add"),
    Subtract(Keyword.Minus, 40, "Sub.sub"),
    Multiply(Keyword.Multiply, 60, "Mul.mul"),
    Divide(Keyword.Divide, 60, "Div.div"),
    Modulo(Keyword.Modulo, 60, "Mod.mod"),

    InfEq(Keyword.InfEq, 20, "infeq"),
    Inf(Keyword.Inf, 20, "inf"),
    Eq(Keyword.Eq, 20, "eq"),

    And(Keyword.And, 80, "and"),
    Or(Keyword.Or, 40, "or"),

    Projection(Keyword.Projection, 100, null),
    Application(Keyword.None, 100, null),
    ;

    fun rewrite() = if(rewrite_ == null) null else {
        val s = rewrite_.split(".")
        if (s.size == 1)
            Expression.IdentifierRef(BindPoint.new(s[0]))
        else
            Expression.Projection(Expression.IdentifierRef(BindPoint.new(s[0])), s[1])
    }
}