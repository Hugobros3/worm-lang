package boneless.core

import boneless.bind.bind
import boneless.parse.createModule
import boneless.type.type

val prelude_math = """
forall T contract Add = [ add = fn [T, T] -> T ];
forall T contract Sub = [ sub = fn [T, T] -> T ];
forall T contract Mul = [ mul = fn [T, T] -> T ];
forall T contract Div = [ div = fn [T, T] -> T ];
forall T contract Mod = [ mod = fn [T, T] -> T ];
forall T contract Neg = [ neg = fn T -> T ];

instance Add::I32 = ( add = jvm_add_i32 );
instance Sub::I32 = ( sub = jvm_sub_i32 );
instance Mul::I32 = ( mul = jvm_mul_i32 );
instance Div::I32 = ( div = jvm_div_i32 );
instance Mod::I32 = ( mod = jvm_mod_i32 );
instance Neg::I32 = ( neg = jvm_neg_i32 );

instance Add::F32 = ( add = jvm_add_f32 );
instance Sub::F32 = ( sub = jvm_sub_f32 );
instance Mul::F32 = ( mul = jvm_mul_f32 );
instance Div::F32 = ( div = jvm_div_f32 );
instance Mod::F32 = ( mod = jvm_mod_f32 );
instance Neg::F32 = ( neg = jvm_neg_f32 );
""".trimIndent()

val prelude_math_module = createModule("Prelude_Math", prelude_math).also { bind(it) ; type(it) }

val prelude_logic = """
forall T contract And = [ and = fn [T, T] -> T ];
forall T contract Or = [ or = fn [T, T] -> T ];
forall T contract Xor = [ xor = fn [T, T] -> T ];
forall T contract Not = [ not = fn T -> T ];

instance And::Bool = ( and = jvm_and_bool );
instance Or::Bool = ( or = jvm_or_bool );
instance Xor::Bool = ( xor = jvm_xor_bool );
instance Not::Bool = ( not = jvm_not_bool );
""".trimIndent()

val prelude_logic_module = createModule("Prelude_Logic", prelude_logic).also { bind(it) ; type(it) }

val prelude_cmp = """
forall T contract InfEq = [ infeq = fn [T, T] -> Bool ];
forall T contract Inf = [ inf = fn [T, T] -> Bool ];
forall T contract Eq = [ eq = fn [T, T] -> Bool ];
forall T contract Neq = [ neq = fn [T, T] -> Bool ];
forall T contract Grt = [ grt = fn [T, T] -> Bool ];
forall T contract GrtEq = [ grteq = fn [T, T] -> Bool ];

instance InfEq::I32 = ( infeq = jvm_infeq_i32 );
instance   Inf::I32 = (   inf = jvm_inf_i32 );
instance    Eq::I32 = (    eq = jvm_eq_i32 );
instance   Neq::I32 = (   neq = jvm_neq_i32 );
instance   Grt::I32 = (   grt = jvm_grt_i32 );
instance GrtEq::I32 = ( grteq = jvm_grteq_i32 );

instance InfEq::F32 = ( infeq = jvm_infeq_f32 );
instance   Inf::F32 = (   inf = jvm_inf_f32 );
instance    Eq::F32 = (    eq = jvm_eq_f32 );
instance   Neq::F32 = (   neq = jvm_neq_f32 );
instance   Grt::F32 = (   grt = jvm_grt_f32 );
instance GrtEq::F32 = ( grteq = jvm_grteq_f32 );
""".trimIndent()

val prelude_cmp_module = createModule("Prelude_Cmp", prelude_cmp).also { bind(it) ; type(it) }

val prelude_modules = listOf(prelude_math_module, prelude_logic_module, prelude_cmp_module)