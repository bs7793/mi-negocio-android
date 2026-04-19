package superapps.minegocio.ui

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class WorkspaceGuardsContractsTest {

    @Serializable
    private data class RpcErrorFixture(
        val code: String? = null,
        val message: String,
    )

    @Test
    fun `decode workspace guard fixtures`() {
        val fixtures = listOf(
            "contracts/salesscreen/create_sale_workspace_forbidden_error_fixture.json" to "workspace_forbidden",
            "contracts/productsscreen/create_product_cross_workspace_error_fixture.json" to "cross_workspace_reference",
            "contracts/productsscreen/update_product_cross_workspace_error_fixture.json" to "cross_workspace_reference",
            "contracts/categoriesscreen/create_category_cross_workspace_error_fixture.json" to "cross_workspace_reference",
            "contracts/warehousesscreen/create_warehouse_cross_workspace_error_fixture.json" to "cross_workspace_reference",
            "contracts/employeesscreen/update_employee_workspace_forbidden_error_fixture.json" to "workspace_forbidden",
        )

        fixtures.forEach { (path, expectedMessage) ->
            val decoded = ContractFixtureLoader.decode<RpcErrorFixture>(path)
            assertEquals(expectedMessage, decoded.message)
        }
    }
}
