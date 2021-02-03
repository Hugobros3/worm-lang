package boneless.classfile

import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.*

class TestClassFileSerdes {
    @Test
    fun testReader() {
        val cf = readClassfile(File("Vec2.class"))
        println("success!")
        cf.dump()
    }

    @Test
    fun testWriter() {
        val classes = listOf("Vec2", "Test2")
        for (filename in classes) {
            val input = File("$filename.class")
            val output =  File("${filename}_again.class")

            val cf = readClassfile(input)
            writeClassFile(cf, output)

            val a = Files.readAllBytes(input.toPath())
            val b = Files.readAllBytes(output.toPath())
            assert(Arrays.equals(a, b))
            println("Succesfully rewrote $input")
        }
        run {
        }
    }

    @Test
    fun testReader2() {
        val cf = readClassfile(File("Test2.class"))
        println("success!")
        cf.dump()
    }

}