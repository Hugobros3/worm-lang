package boneless.classfile

import org.junit.Test
import java.io.File

class TestClassBuilder {
    val outputDir = File("test_out/")

    init {
        outputDir.mkdir()
    }

    @Test
    fun testClassBuilder() {
        val builder = ClassFileBuilder(className = "GeneratedTestClass")
        val cf = builder.finish()
        cf.dump()
        writeClassFile(cf, File("${outputDir.absoluteFile}/GeneratedTestClass.class"))
    }
}