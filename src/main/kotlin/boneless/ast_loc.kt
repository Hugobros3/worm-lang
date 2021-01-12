package boneless

data class ASTLoc(val moduleName: Identifier, val inModule: ModuleLoc)

sealed class ModuleLoc {
    data class DefRef(val defName: Identifier): ModuleLoc()
    data class InsideDefRef(val defName: Identifier, val inside: SeqLoc): ModuleLoc()
}

sealed class SeqLoc {
    data class InstRef(val i: Int)
}