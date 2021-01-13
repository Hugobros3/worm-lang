package boneless

enum class BuiltinFn(type_str: String) {
    ADD("fn [I32, I32] -> I32"),
    MULTIPLY("fn [I32, I32] -> I32"),
    SUBTRACT("fn [I32, I32] -> I32"),
    ;

    val type: Type
    init {
        val p = Parser(type_str, Tokenizer(type_str).tokenize())
        type = p.parseType()
    }
}