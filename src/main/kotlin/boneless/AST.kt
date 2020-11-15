package boneless

sealed class Instruction {
    data class Def(val identifier: String, val parameters: List<DefParameter>, val type: Expression?, val body: Expression) : Instruction() {
        data class DefParameter(val identifier: String)
    }
    data class Let(val identifier: String, val isMutable: Boolean, val type: Expression?, val body: Expression) : Instruction()
    data class Var(val identifier: String, val type: Expression?, val defaultValue: Expression?) : Instruction()
}

sealed class Expression {
    object Unit : Expression()
    data class StringLit(val lit: String) : Expression()
    data class NumLit(val lit: String) : Expression()

    data class RefSymbol(val symbol: String) : Expression()

    data class Tuple(val elements: List<Expression>) : Expression()
    data class Invocation(val callee: Expression, val arguments: List<Expression>) : Expression()
    data class Function(val parameters: List<Expression>, val body: Expression) : Expression()

    data class Ascription(val e: Expression, val type: Expression) : Expression()

    data class Sequence(val instructions: List<Instruction>, val yieldValue: Expression?) : Expression()
    data class Conditional(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression()
}