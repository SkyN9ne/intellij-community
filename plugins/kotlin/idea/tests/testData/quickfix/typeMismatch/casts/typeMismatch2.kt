// "Cast expression 'Foo<Number>()' to 'Foo<Int>'" "false"
// ACTION: Change return type of enclosing function 'foo' to 'Foo<Number>'
// ACTION: Change return type of enclosing function 'foo' to 'Number'
// ACTION: Introduce import alias
// ERROR: Type mismatch: inferred type is Foo<Number> but Foo<Int> was expected
// ERROR: Type mismatch: inferred type is Number but Int was expected
class Foo<T>

fun foo(): Foo<Int> {
    return <caret>Foo<Number>()
}