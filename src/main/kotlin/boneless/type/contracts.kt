package boneless.type

import boneless.Def
import boneless.Module
import boneless.bind.get_def

/** Finds an instance that corresponds to the contract and the type arguments. */
fun findInstance(module: Module, contractDef: Def, typeArguments: List<Type>): Def? {
    // TODO take bounds on the type arguments into consideration
    outer@
    for (def in module.defs) {
        if (def.body is Def.DefBody.Instance) {
            if (get_def(def.body.contractId.resolved) == contractDef) {
                if (typeArguments.size != def.body.arguments.size)
                    throw Exception("Wrong number of type arguments: needed ${def.body.arguments.size} got ${typeArguments.size}")

                loop@ for (i in typeArguments.indices) {
                    val instance_arg: Type = def.body.arguments[i]
                    val supplied_arg: Type = typeArguments[i]

                    when {
                        instance_arg == supplied_arg ||
                                // means we can instanciate the instance with the appropriate argument
                        instance_arg is Type.TypeParam -> { continue@loop }
                        else -> { continue@outer }
                    }
                }
                return def
            }
        }
    }
    return null
}