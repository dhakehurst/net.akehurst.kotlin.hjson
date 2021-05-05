package net.akehurst.hjson

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@RunWith(Parameterized::class)
class test_From_HJson_Java_Files(
        val data: Data
) {

    companion object {

        var sourceFiles = this::class.java.getResourceAsStream("/from_hjson-java/").use {
            if (it == null)
                emptyList()
            else
                BufferedReader(InputStreamReader(it)).readLines()
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Iterable<Array<Any>> {
            val col = mutableListOf<Array<Any>>()
            for (sourceFile in sourceFiles) {
                val filePath = "/from_hjson-java/$sourceFile"
                // val inps = ClassLoader.getSystemClassLoader().getResourceAsStream(sourceFile)
                val inps = this::class.java.getResourceAsStream(filePath)

                val br = BufferedReader(InputStreamReader(inps))
                var text = br.readText()
                col.add(arrayOf(Data(sourceFile, text)))
            }
            return col
        }
    }

    class Data(val sourceFile: String, val text: String) {
        // --- Object ---
        override fun toString(): String {
            return this.sourceFile
        }
    }

    @BeforeTest
    fun setup() {
    }

    @Test
    fun test() {
        if (data.sourceFile.startsWith("fail")) {
            assertFailsWith(HJsonParserException::class) {
                HJson.process(data.text)
            }
        } else {
            val doc = HJson.process(data.text)
            assertNotNull(doc)
        }
    }

}