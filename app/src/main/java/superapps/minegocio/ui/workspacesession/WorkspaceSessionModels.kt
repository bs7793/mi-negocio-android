package superapps.minegocio.ui.workspacesession

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceSummary(
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("workspace_name")
    val workspaceName: String,
    val role: String,
    val status: String,
    @SerialName("is_active")
    val isActive: Boolean = false,
)

@Serializable
data class SetActiveWorkspaceResponse(
    val success: Boolean,
    @SerialName("active_workspace_id")
    val activeWorkspaceId: String,
)

data class WorkspaceSessionUiState(
    val isLoading: Boolean = false,
    val selectedWorkspaceId: String? = null,
    val selectedWorkspaceName: String? = null,
    val workspaces: List<WorkspaceSummary> = emptyList(),
    val errorMessage: String? = null,
)
