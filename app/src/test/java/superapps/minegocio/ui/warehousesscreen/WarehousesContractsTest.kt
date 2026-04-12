package superapps.minegocio.ui.warehousesscreen

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class WarehousesContractsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decode warehouses happy fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/warehousesscreen/list_warehouses_response_fixture.json",
        )
        val warehouses = json.decodeFromString(ListSerializer(Warehouse.serializer()), raw)
        assertEquals(1, warehouses.size)
        assertEquals("Main Store", warehouses.first().name)
    }

    @Test
    fun `decode warehouses edge fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/warehousesscreen/list_warehouses_response_edge_fixture.json",
        )
        val warehouses = json.decodeFromString(ListSerializer(Warehouse.serializer()), raw)
        assertEquals(1, warehouses.size)
        assertNull(warehouses.first().location)
    }
}
