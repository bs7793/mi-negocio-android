package superapps.minegocio.contracts

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object ContractFixtureLoader {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    fun readRaw(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Fixture not found: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun parseJsonElement(path: String): JsonElement {
        return json.parseToJsonElement(readRaw(path))
    }

    inline fun <reified T> decode(path: String): T {
        return json.decodeFromString(readRaw(path))
    }
}
