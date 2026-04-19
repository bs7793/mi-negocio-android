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
