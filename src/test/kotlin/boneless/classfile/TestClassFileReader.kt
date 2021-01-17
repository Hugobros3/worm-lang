package boneless.classfile

import org.junit.Test
import java.io.File

class TestClassFileReader {
    @Test
    fun testReader() {
        val cf = readClassfile(File("Vec2.class"))
        println("success!")
        cf.dump()
    }
}