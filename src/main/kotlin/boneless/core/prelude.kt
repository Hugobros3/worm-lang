package boneless.core

import boneless.bind.bind
import boneless.parse.createModule
import boneless.type.type

val prelude_math = """
forall T
contract Add = [ add = fn [T, T] -> T ];

forall T
contract Sub = [ sub = fn [T, T] -> T ];

forall T
contract Mul = [ mul = fn [T, T] -> T ];

forall T
contract Div = [ div = fn [T, T] -> T ];

forall T
contract Mod = [ mod = fn [T, T] -> T ];

forall T
contract Neg = [ neg = fn T -> T ];

instance Add::I32 = ( add = jvm_add_i32 );
instance Sub::I32 = ( sub = jvm_sub_i32 );
instance Mul::I32 = ( mul = jvm_mul_i32 );
instance Div::I32 = ( div = jvm_div_i32 );
instance Mod::I32 = ( mod = jvm_mod_i32 );
instance Neg::I32 = ( neg = jvm_neg_i32 );
""".trimIndent()

val prelude_math_module = createModule("Prelude_Math", prelude_math).also { bind(it) ; type(it) }

val prelude_modules = listOf(prelude_math_module)