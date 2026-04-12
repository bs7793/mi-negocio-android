package superapps.minegocio.ui.productsscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class ProductsContractsTest {

    @Test
    fun `decode list_products happy fixture`() {
        val response = ContractFixtureLoader.decode<ProductsListResponse>(
            "contracts/productsscreen/list_products_rpc_response_fixture.json",
        )
        assertEquals(1, response.total)
        assertEquals("Orange Juice", response.items.first().name)
    }

    @Test
    fun `decode list_products edge fixture`() {
        val response = ContractFixtureLoader.decode<ProductsListResponse>(
            "contracts/productsscreen/list_products_rpc_response_edge_fixture.json",
        )
        assertTrue(response.items.isEmpty())
        assertEquals(0, response.total)
    }

    @Test
    fun `decode product_options_catalog happy fixture`() {
        val response = ContractFixtureLoader.decode<ProductOptionsCatalogResponse>(
            "contracts/productsscreen/get_product_options_catalog_rpc_response_fixture.json",
        )
        assertEquals(1, response.optionTypes.size)
        assertEquals("Size", response.optionTypes.first().name)
    }

    @Test
    fun `decode product_options_catalog edge fixture`() {
        val response = ContractFixtureLoader.decode<ProductOptionsCatalogResponse>(
            "contracts/productsscreen/get_product_options_catalog_rpc_response_edge_fixture.json",
        )
        assertTrue(response.optionTypes.isEmpty())
    }
}
