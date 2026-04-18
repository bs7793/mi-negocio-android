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

    @Test
    fun `decode dashboard sales feed happy fixture`() {
        val feed = ContractFixtureLoader.decode<List<DashboardSalesFeedItem>>(
            "contracts/dashboardscreen/get_dashboard_sales_feed_rpc_response_fixture.json",
        )
        assertEquals(2, feed.size)
        assertEquals(101L, feed.first().saleId)
        assertEquals("Jane Doe", feed.first().customerName)
        assertEquals("cash", feed.first().paymentMethod)
        assertEquals(12.0, feed[1].total, 0.0001)
        assertEquals(null, feed[1].customerName)
    }

    @Test
    fun `decode dashboard sales feed edge fixture`() {
        val feed = ContractFixtureLoader.decode<List<DashboardSalesFeedItem>>(
            "contracts/dashboardscreen/get_dashboard_sales_feed_rpc_response_edge_fixture.json",
        )
        assertEquals(0, feed.size)
    }

    @Test
    fun `decode dashboard sale detail happy fixture`() {
        val detail = ContractFixtureLoader.decode<DashboardSaleDetail>(
            "contracts/dashboardscreen/get_dashboard_sale_detail_rpc_response_fixture.json",
        )
        assertEquals(42L, detail.saleId)
        assertEquals("completed", detail.status)
        assertEquals(2, detail.lines.size)
        assertEquals("T-Shirt", detail.lines.first().productName)
        assertEquals("https://example.com/product-images/tshirt.jpg", detail.lines.first().imageUrl)
        assertEquals(null, detail.lines[1].imageUrl)
        assertEquals("Gift wrap", detail.lines[1].notes)
        assertEquals(2, detail.payments.size)
        assertEquals("AUTH123", detail.payments[1].referenceText)
    }

    @Test
    fun `decode dashboard sale detail edge fixture`() {
        val detail = ContractFixtureLoader.decode<DashboardSaleDetail>(
            "contracts/dashboardscreen/get_dashboard_sale_detail_rpc_response_edge_fixture.json",
        )
        assertEquals(99L, detail.saleId)
        assertEquals(null, detail.customerName)
        assertEquals(0, detail.lines.size)
        assertEquals(0, detail.payments.size)
    }
}
