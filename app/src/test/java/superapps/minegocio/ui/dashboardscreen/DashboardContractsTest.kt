package superapps.minegocio.ui.dashboardscreen

import org.junit.Assert.assertEquals
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class DashboardContractsTest {

    @Test
    fun `decode list warehouses happy fixture`() {
        val warehouses = ContractFixtureLoader.decode<List<DashboardWarehouse>>(
            "contracts/dashboardscreen/list_warehouses_response_fixture.json",
        )
        assertEquals(2, warehouses.size)
        assertEquals("Main Store", warehouses.first().name)
    }

    @Test
    fun `decode list warehouses edge fixture`() {
        val warehouses = ContractFixtureLoader.decode<List<DashboardWarehouse>>(
            "contracts/dashboardscreen/list_warehouses_response_edge_fixture.json",
        )
        assertEquals(1, warehouses.size)
        assertEquals("Backup", warehouses.first().name)
    }

    @Test
    fun `decode income statement monthly summary happy fixture`() {
        val summary = ContractFixtureLoader.decode<DashboardIncomeStatementSummary>(
            "contracts/dashboardscreen/get_income_statement_monthly_summary_rpc_response_fixture.json",
        )
        assertEquals(1985.75, summary.incomeTotal, 0.0001)
        assertEquals(1240.25, summary.costTotal, 0.0001)
        assertEquals(745.5, summary.profitTotal, 0.0001)
    }

    @Test
    fun `decode income statement monthly summary edge fixture`() {
        val summary = ContractFixtureLoader.decode<DashboardIncomeStatementSummary>(
            "contracts/dashboardscreen/get_income_statement_monthly_summary_rpc_response_edge_fixture.json",
        )
        assertEquals(0.0, summary.incomeTotal, 0.0001)
        assertEquals(0.0, summary.costTotal, 0.0001)
        assertEquals(0.0, summary.profitTotal, 0.0001)
    }
}
