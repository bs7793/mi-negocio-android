package superapps.minegocio.ui.employeesscreen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Employee(
    @SerialName("user_id")
    val userId: String,
    val email: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    val role: String,
    val status: String,
)

@Serializable
data class EmployeeMutationResult(
    val success: Boolean,
    val message: String? = null,
    val member: Employee? = null,
)

@Serializable
data class InviteCodeResult(
    val success: Boolean,
    val message: String? = null,
    @SerialName("invite_code")
    val inviteCode: String? = null,
    val role: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
)

@Serializable
data class WorkspaceInviteCode(
    @SerialName("invite_id")
    val inviteId: String,
    @SerialName("invite_code")
    val inviteCode: String,
    val role: String,
    val status: String,
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("created_at")
    val createdAt: String,
)
