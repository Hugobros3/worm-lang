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

    Def("def"),
    Let("let"),
    Var("var"),
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

    // DefinitionSeparator("::"),
    Range(".."),
    Map("=>"),
    Arrow("->"),
    Eq("=="),
    NotEq("!="),
    InfEq("<="),
    GreaterEq(">="),

    Assign("="),
    Inf("<"),
    Greater(">"),
    StatementEnd(";"),
    NextItem(","),
    TypeAnnotation(":"),

    Plus("+"),
    Minus("-"),
    Multiply("*"),
    Divide("/"),
    Modulo("%"),

    Not("!"),
    And("^"),
    Or("|"),

    Reference("&"),
    Dereference("@"),
}