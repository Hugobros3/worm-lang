package boneless.parse

enum class PrefixOperator(val token: Keyword, val rewrite: String) {
    Negate(Keyword.Minus, "negate"),

    Not(Keyword.Not, "not"),
    Reference(Keyword.Reference, "ref"),
    Dereference(Keyword.Dereference, "deref"),
}

enum class InfixOperator(val token: Keyword, val priority: Int, val rewrite: String?) {
    // Map(Keyword.Map, 0, null),
    Ascription(Keyword.TypeAnnotation, 80, null),
    Cast(Keyword.As, 80, null),

    Add(Keyword.Plus, 40, "add"),
    Subtract(Keyword.Minus, 40, "subtract"),
    Multiply(Keyword.Multiply, 60, "multiply"),
    Divide(Keyword.Divide, 60, "divide"),
    Modulo(Keyword.Modulo, 60, "modulo"),

    InfEq(Keyword.InfEq, 20, "infeq"),
    Inf(Keyword.Inf, 20, "inf"),
    Eq(Keyword.Eq, 20, "eq"),

    And(Keyword.And, 80, "and"),
    Or(Keyword.Or, 40, "or"),

    Projection(Keyword.Projection, 100, null),
    Application(Keyword.None, 100, null),
}