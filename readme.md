# worm-lang

*(Worms are  boneless snakes)*

This is a toy programming language featuring weird syntax and targeting an experimental version of an unpopular VM (the JVM).

The name is an unfunny private joke and literally nothing about it is set is stone, please do not read more into this than is reasonnable.

## Cool shizz:

 * The syntax discriminates clearly and consistently between scopes `{}`, value aggregates `()`, and aggregate types `[]`. There are no exceptions, all the syntax adheres to these basic rules.
 * Supports tuple, records and enumerations with a coherent and unified syntax
 * All datatypes are structural, until you make them nominal using `data`
 * Local type inference inside definitions and for return types
 * Targets the LW2 prototype from OpenJDK's project valhalla, to get proper value types support out of the JVM: this means types like `[I32, I32]` do not require heap allocations and instead work like primitive types.
 * Uses basic blocks internally to encode control flow inside functions
 * Support for parametric polymorphism, including a contract system (in the same vein as Rust traits and Haskell typeclasses) and has basic type parameters inference
 * The built-in operators (`+`, `<=`, `^`, ...) use said contracts system, so they work on custom types too
 * The syntax makes a lot of effort to be not only easy to read, but *simple, full stop*. The need for heavy syntactic sugar is not present, the syntax was carefully studied to offer flexibility without sacrificing much verbosity or allowing ambiguity.

## Syntax Examples

```haskell
fn foo(a: I32, b: I32) => {
    if a < b then 42
    else {
        a * (b - 1)
    }
};
```

```haskell
fn fac(n: I32) -> I32 = {
    if n <= 1 then 1 else fac (n-1) * n
};
```

```haskell
data Point = [I32, I32];
data Rectangle = [min = Point, max = Point];

forall T
contract Shape = [ get_area = fn T -> I32 ];

instance Shape::Rectangle = (
    get_area = fn Rectangle(min = Point (sx, sy), max = Point (ex, ey)) => (ex - sx) * (ey - sy)
);
```


```haskell
forall T
fn mk_pair (t: T) => (t, t);
```
