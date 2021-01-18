package boneless.parse

val numbers = "0123456789"

fun Char.canStartIdentifier() = isLetter() || this == '_'
fun Char.canMakeUpIdentifier() = canStartIdentifier() || this in numbers

class Tokenizer(val input: String) {
    data class Token(val pos: Pos, val tokenName: String, val payload: String? = null)
    data class Pos(val absolute: Int, val line: Int, val col: Int) {
        override fun toString() = "line $line, column $col"
    }

    val tokens = mutableListOf<Token>()
    var i = 0
    var line = 0
    var lastLinePos = 0

    fun eat(str: String) {
        if (!input.substring(i).startsWith(str))
            throw Exception()
        i += str.length
    }

    fun accept(str: String): Boolean {
        if (i + str.length > input.length || !input.substring(i).startsWith(str) || (str.last().canMakeUpIdentifier() && i + str.length < input.length && input[i + str.length].canMakeUpIdentifier()))
            return false
        i += str.length
        return true
    }

    fun acceptToken(str: String): Boolean {
        val pos = pos
        if (accept(str)) {
            tokens += Token(pos, str)
            return true
        }
        return false
    }

    fun consumeBlockComment(): Boolean {
        if (accept("/*")) {
            while (true) {
                if (i >= input.length)
                    throw Exception("Reached EOF before finding a matching */ to the current comment block")
                if (accept("*/"))
                    break
                consumeBlockComment()
                i++
            }
            return true
        }
        return false
    }

    val pos: Pos
        get() = Pos(i, line, i - lastLinePos)
    val here: String
        get() = "$pos, starting with \"${input.substring(i, Math.min(input.length, i + 10))}...\""

    fun acceptKeyword(): Boolean {
        for (keyword in Keyword.values()) {
            if (keyword != Keyword.None && acceptToken(keyword.str))
                return true
        }
        return false
    }

    fun tokenize(): List<Token> {
        // not actually unicode compliant but whatever
        while (i < input.length) {
            val tkstart = pos
            when {
                accept(" ") -> {
                }
                accept("\t") -> {
                    throw Exception("tabs bad")
                }
                accept("\n") -> {
                    line++; lastLinePos = i
                }
                accept("//") -> {
                    while (i < input.length) {
                        if (input[i] == '\n')
                            break
                        i++
                    }
                }
                consumeBlockComment() -> {
                }
                accept("\"") -> {
                    var lit = ""
                    while (input[i] != '"' || (i > 0 && input[i - 1] == '\\')) {
                        lit += input[i]
                        i++
                    }
                    eat("\"")
                    tokens += Token(tkstart, "StringLit", lit)
                }
                input[i] in numbers -> {
                    var lit = ""
                    var dots = 0
                    while (input[i] in numbers || (input[i] == '.' && dots++ < 1)) {
                        lit += input[i]
                        i++
                    }
                    tokens += Token(tkstart, "NumLit", lit)
                }
                acceptKeyword() -> {}
                input[i].canStartIdentifier() -> {
                    var identifier = ""
                    while (i < input.length && input[i].canMakeUpIdentifier()) {
                        identifier += input[i]
                        i++
                    }
                    tokens += Token(tkstart, "Identifier", identifier)
                }
                else -> throw Exception("Cannot match a token at $here")
            }
        }

        tokens += Token(pos, "EOF")

        return tokens
    }
}