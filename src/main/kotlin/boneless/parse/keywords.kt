package boneless.parse

enum class Keyword(val str: String) {
    /** This is never parsed */
    None(""),

    LParent("("),
    RParent(")"),
    LCurlyBrace("{"),
    RCurlyBrace("}"),
    LSquareBrace("["),
    RSquareBrace("]"),

    Forall("forall"),
    Specialization("::"),

    TopType("Top"),
    Undef("builtin_undef"),

    Contract("contract"),
    Instance("instance"),

    Def("def"),
    Let("let"),
    Var("var"),
    Mut("mut"),
    Fn("fn"),
    As("as"),
    Data("data"),
    Type("type"),
    If("if"),
    Then("then"),
    Else("else"),
    While("while"),
    Do("do"),
    For("for"),

    True("true"),
    False("false"),

    Range(".."),
    Map("=>"),
    Arrow("->"),
    Eq("=="),
    NotEq("!="),
    InfEq("<="),
    GreaterEq(">="),
    Assign(":="),

    Bind("="),
    Inf("<"),
    Greater(">"),
    StatementEnd(";"),
    NextItem(","),
    TypeAnnotation(":"),
    Projection("."),

    Plus("+"),
    Minus("-"),
    Multiply("*"),
    Divide("/"),
    Modulo("%"),

    Cand("&&"),
    Cor("||"),

    Not("!"),
    And("&"),
    Or("|"),
    Xor("^"),

    Reference("&"),
    Dereference("@"),
}