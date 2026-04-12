package superapps.minegocio.ui.categoriesscreen

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class CategoriesContractsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decode categories happy fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/categoriesscreen/list_categories_response_fixture.json",
        )
        val categories = json.decodeFromString(ListSerializer(Category.serializer()), raw)
        assertEquals(1, categories.size)
        assertEquals("Beverages", categories.first().name)
    }

    @Test
    fun `decode categories edge fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/categoriesscreen/list_categories_response_edge_fixture.json",
        )
        val categories = json.decodeFromString(ListSerializer(Category.serializer()), raw)
        assertEquals(1, categories.size)
        assertNull(categories.first().description)
    }
}
