package superapps.minegocio.ui.employeesscreen

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import superapps.minegocio.contracts.ContractFixtureLoader

class EmployeesContractsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decode list workspace members fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/employeesscreen/list_workspace_members_rpc_response_fixture.json",
        )
        val employees = json.decodeFromString(ListSerializer(Employee.serializer()), raw)
        assertEquals(2, employees.size)
        assertEquals("owner", employees.first().role)
        assertEquals("invited", employees.last().status)
    }

    @Test
    fun `decode invite workspace member fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/employeesscreen/invite_workspace_member_rpc_response_fixture.json",
        )
        val mutation = json.decodeFromString<EmployeeMutationResult>(raw)
        assertTrue(mutation.success)
        assertEquals("member", mutation.member?.role)
    }

    @Test
    fun `decode list workspace members edge fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/employeesscreen/list_workspace_members_rpc_response_edge_fixture.json",
        )
        val employees = json.decodeFromString(ListSerializer(Employee.serializer()), raw)
        assertEquals(1, employees.size)
        assertEquals("disabled", employees.first().status)
        assertNull(employees.first().email)
    }

    @Test
    fun `decode invite workspace member edge fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/employeesscreen/invite_workspace_member_rpc_response_edge_fixture.json",
        )
        val mutation = json.decodeFromString<EmployeeMutationResult>(raw)
        assertTrue(mutation.success)
        assertNull(mutation.member)
    }

    @Test
    fun `decode create workspace invite code fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/employeesscreen/create_workspace_invite_code_rpc_response_fixture.json",
        )
        val result = json.decodeFromString<InviteCodeResult>(raw)
        assertTrue(result.success)
        assertEquals("member", result.role)
        assertEquals("A1B2C3D4E5", result.inviteCode)
    }

    @Test
    fun `decode list workspace invite codes fixture`() {
        val raw = ContractFixtureLoader.readRaw(
            "contracts/employeesscreen/list_workspace_invite_codes_rpc_response_fixture.json",
        )
        val inviteCodes = json.decodeFromString(ListSerializer(WorkspaceInviteCode.serializer()), raw)
        assertEquals(1, inviteCodes.size)
        assertEquals("pending", inviteCodes.first().status)
    }
}
