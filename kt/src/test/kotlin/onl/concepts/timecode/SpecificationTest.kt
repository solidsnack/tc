package onl.concepts.timecode

import java.io.File
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.yaml.snakeyaml.Yaml

typealias Case = Map<String, String>

// Runs specification/tests.yaml, whose path the build passes in.
class SpecificationTest {
    private val suite: Map<String, Map<String, List<Case>>> =
        File(System.getProperty("timecode.specification"))
            .reader()
            .use { Yaml().load(it) }

    // Print a time with each method that gives a code of this group's
    // length.
    private val encoders: Map<String, (Instant, Int) -> List<String>> =
        mapOf(
            "tc5" to { t, _ -> listOf(TimeCode.of(t).tc5(), TimeCode.tc5(t)) },
            "tc8" to { t, _ -> listOf(TimeCode.of(t).tc8(), TimeCode.tc8(t)) },
            "tcx" to { t, _ -> listOf(TimeCode.of(t).tcX(), TimeCode.tcX(t)) },
            "tcn" to { t, n ->
                listOfNotNull(
                    TimeCode.of(t).display(n),
                    TimeCode.tc(t, n),
                    if (n >= 12) TimeCode.of(t).tcX(n - 11) else null,
                )
            },
        )

    @TestFactory
    fun groups(): List<DynamicContainer> =
        encoders.map { (name, encoder) ->
            val group = suite.getValue(name)
            val decode = group.getValue("decode").map { case ->
                val input = case.getValue("input")
                val output = case.getValue("output")

                dynamicTest("decode $input") {
                    val t = TimeCode.parse(input).toInstant()
                    assertEquals(Instant.parse(output), t)
                }
            }
            val encode = group.getValue("encode").map { case ->
                val input = case.getValue("input")
                val output = case.getValue("output")

                dynamicTest("encode $input") {
                    val t = Instant.parse(input)

                    for (code in encoder(t, output.length)) {
                        assertEquals(output, code)
                    }
                }
            }

            dynamicContainer("tests.yaml: $name", decode + encode)
        }

    // java.time has no leap seconds: Instant.parse reads 23:59:60 as
    // 23:59:59. TimeCodes treat a leap second as the last second of its half
    // month, so both ways round, the leap seconds land on 23:59:59.
    @TestFactory
    fun leapSeconds(): DynamicContainer {
        val group = suite.getValue("leap-seconds")
        val decode = group.getValue("decode").map { case ->
            val input = case.getValue("input")
            val output = case.getValue("output")

            dynamicTest("decode $input as the last second") {
                val t = TimeCode.parse(input).toInstant()
                assertEquals(Instant.parse(output.replace(":60", ":59")), t)
            }
        }
        val encode = group.getValue("encode").map { case ->
            val input = case.getValue("input")
            val output = case.getValue("output")

            dynamicTest("encode $input as the last second") {
                val t = Instant.parse(input)
                assertEquals(Instant.parse(input.replace(":60", ":59")), t)
                assertEquals(
                    TimeCode.parse(output).tcX(),
                    TimeCode.of(t).tcX(),
                )
            }
        }

        return dynamicContainer("tests.yaml: leap-seconds", decode + encode)
    }
}
