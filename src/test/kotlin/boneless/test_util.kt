package boneless

import org.junit.Assert

fun expectFailure(f: () -> Unit) {
    try {
        f()
        Assert.assertTrue(false)
    } catch (e: Exception) {
        println("Fails as expected: $e")
    }
}