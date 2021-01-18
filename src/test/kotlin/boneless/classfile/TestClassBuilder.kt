package boneless.classfile

import org.junit.Test
import java.io.File

class TestClassBuilder {
    @Test
    fun testClassBuilder() {
        val builder = ClassFileBuilder(className = "GeneratedTestClass")
        val cf = builder.finish()
        cf.dump()
        writeClassFile(cf, File("GeneratedTestClass.class"))
    }
}