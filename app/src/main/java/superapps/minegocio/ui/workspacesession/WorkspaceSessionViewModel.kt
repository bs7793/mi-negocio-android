package superapps.minegocio.ui.workspacesession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import superapps.minegocio.ui.WorkspaceScopeInvalidationBus

class WorkspaceSessionViewModel(
    private val repository: WorkspaceSessionRepository = WorkspaceSessionRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkspaceSessionUiState())
    val uiState: StateFlow<WorkspaceSessionUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentSelection = WorkspaceSelectionStore.selectedWorkspaceId
                val workspaces = repository.listMyWorkspaces(currentSelection)
                val active = resolveSelection(workspaces, currentSelection)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        workspaces = workspaces,
                        selectedWorkspaceId = active?.workspaceId,
                        selectedWorkspaceName = active?.workspaceName,
                        errorMessage = if (active == null && workspaces.isNotEmpty()) {
                            "Select a workspace to continue."
                        } else {
                            null
                        },
                    )
                }
                WorkspaceSelectionStore.selectedWorkspaceId = active?.workspaceId
                WorkspaceScopeInvalidationBus.invalidate(active?.workspaceId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedWorkspaceId = null,
                        selectedWorkspaceName = null,
                        workspaces = emptyList(),
                        errorMessage = e.message ?: e.toString(),
                    )
                }
                WorkspaceSelectionStore.selectedWorkspaceId = null
                WorkspaceScopeInvalidationBus.invalidate(null)
            }
        }
    }

    fun selectWorkspace(workspaceId: String) {
        if (workspaceId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.setActiveWorkspace(workspaceId)
                val workspaces = repository.listMyWorkspaces(workspaceId)
                val active = resolveSelection(workspaces, workspaceId)
                if (active == null) {
                    throw IllegalStateException("Selected workspace is not available for this account.")
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        workspaces = workspaces,
                        selectedWorkspaceId = active?.workspaceId,
                        selectedWorkspaceName = active?.workspaceName,
                        errorMessage = null,
                    )
                }
                WorkspaceSelectionStore.selectedWorkspaceId = active?.workspaceId
                WorkspaceScopeInvalidationBus.invalidate(active.workspaceId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun resolveSelection(
        workspaces: List<WorkspaceSummary>,
        selectedWorkspaceId: String?,
    ): WorkspaceSummary? {
        val explicit = selectedWorkspaceId?.let { id ->
            workspaces.firstOrNull { it.workspaceId == id }
        }
        if (explicit != null) return explicit
        if (workspaces.size == 1) return workspaces.first()
        return null
    }
}
