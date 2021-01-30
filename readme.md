# worm-lang

this is a toy programming language featuring weird syntax and targeting an experimental version of an unpopular VM

the name is an unfunny private joke and literally nothing about it is set is stone

## Cool shizz:

 * The syntax discriminates clearly and consistently between scopes `{}`, value aggregates `()`, and aggregate types `[]`. There are no exceptions, all the syntax adheres to these basic rules.
 * Supports tuple, records and enumerations with a coherent and unified syntax
 * All datatypes are structural until you make them nominal using `data`
 * Local type inference inside definitions and for return types
 * Targets the LW2 prototype from OpenJDK's project valhalla, to get proper value types support out of the JVM