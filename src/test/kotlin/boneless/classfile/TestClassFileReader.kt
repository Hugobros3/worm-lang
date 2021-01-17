package boneless.classfile

import org.junit.Test
import java.io.File

class TestClassFileReader {
    @Test
    fun testReader() {
        readClassfile(File("Vec2.class"))
    }
}