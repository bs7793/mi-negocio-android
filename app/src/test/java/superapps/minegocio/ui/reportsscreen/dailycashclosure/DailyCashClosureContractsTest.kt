package superapps.minegocio.ui.reportsscreen.dailycashclosure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class DailyCashClosureContractsTest {

    @Test
    fun `decode list warehouses happy fixture`() {
        val warehouses = ContractFixtureLoader.decode<List<DailyCashClosureWarehouse>>(
            "contracts/dailycashclosure/list_warehouses_response_fixture.json",
        )
        assertEquals(2, warehouses.size)
        assertEquals("Main Store", warehouses.first().name)
    }

    @Test
    fun `decode list warehouses edge fixture`() {
        val warehouses = ContractFixtureLoader.decode<List<DailyCashClosureWarehouse>>(
            "contracts/dailycashclosure/list_warehouses_response_edge_fixture.json",
        )
        assertEquals(1, warehouses.size)
        assertTrue(warehouses.first().location == null)
    }

    @Test
    fun `decode get sales daily summary happy fixture`() {
        val summary = ContractFixtureLoader.decode<DailyCashClosureSummary>(
            "contracts/dailycashclosure/get_sales_daily_summary_rpc_response_fixture.json",
        )
        assertEquals(7, summary.salesCount)
        assertEquals(389.99, summary.grossTotal, 0.0001)
    }

    @Test
    fun `decode get sales daily summary edge fixture`() {
        val summary = ContractFixtureLoader.decode<DailyCashClosureSummary>(
            "contracts/dailycashclosure/get_sales_daily_summary_rpc_response_edge_fixture.json",
        )
        assertEquals(0, summary.salesCount)
        assertEquals(0.0, summary.grossTotal, 0.0001)
    }
}
