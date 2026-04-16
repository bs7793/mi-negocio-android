package superapps.minegocio.ui.salesscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class SalesContractsTest {

    @Test
    fun `decode get_sellable_variants happy fixture`() {
        val response = ContractFixtureLoader.decode<SellableVariantsResponse>(
            "contracts/salesscreen/get_sellable_variants_rpc_response_fixture.json",
        )
        assertTrue(response.items.isNotEmpty())
        assertEquals("Colombian Coffee", response.items.first().productName)
        assertEquals(
            "https://example.com/storage/v1/object/public/product-images/products/coffee-250g.jpg",
            response.items.first().imageUrl,
        )
    }

    @Test
    fun `decode get_sellable_variants edge fixture`() {
        val response = ContractFixtureLoader.decode<SellableVariantsResponse>(
            "contracts/salesscreen/get_sellable_variants_rpc_response_edge_fixture.json",
        )
        assertEquals(1, response.items.size)
        assertTrue(response.items.first().options.isEmpty())
        assertEquals(null, response.items.first().imageUrl)
    }

    @Test
    fun `decode get_sales_daily_summary happy fixture`() {
        val summary = ContractFixtureLoader.decode<SalesDailySummary>(
            "contracts/salesscreen/get_sales_daily_summary_rpc_response_fixture.json",
        )
        assertEquals(4, summary.salesCount)
        assertEquals(245.5, summary.grossTotal, 0.0001)
    }

    @Test
    fun `decode get_sales_daily_summary edge fixture`() {
        val summary = ContractFixtureLoader.decode<SalesDailySummary>(
            "contracts/salesscreen/get_sales_daily_summary_rpc_response_edge_fixture.json",
        )
        assertEquals(0, summary.salesCount)
        assertEquals(0.0, summary.grossTotal, 0.0001)
    }

    @Test
    fun `decode create_sale_with_lines_and_payments happy fixture`() {
        val created = ContractFixtureLoader.decode<SaleCreateResponse>(
            "contracts/salesscreen/create_sale_with_lines_and_payments_rpc_response_fixture.json",
        )
        assertEquals(99L, created.saleId)
        assertEquals(created.total, created.paidTotal, 0.0001)
    }

    @Test
    fun `decode create_sale_with_lines_and_payments edge fixture`() {
        val created = ContractFixtureLoader.decode<SaleCreateResponse>(
            "contracts/salesscreen/create_sale_with_lines_and_payments_rpc_response_edge_fixture.json",
        )
        assertEquals(100L, created.saleId)
        assertEquals(0.0, created.changeTotal, 0.0001)
    }
}
