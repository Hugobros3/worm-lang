package boneless.classfile

import org.junit.Test
import java.io.File

class TestClassFileSerdes {
    @Test
    fun testReader() {
        val cf = readClassfile(File("Vec2.class"))
        println("success!")
        cf.dump()
    }

    @Test
    fun testWriter() {
        val cf = readClassfile(File("Vec2.class"))
        writeClassFile(cf, File("Vec2_again.class"))
    }
}