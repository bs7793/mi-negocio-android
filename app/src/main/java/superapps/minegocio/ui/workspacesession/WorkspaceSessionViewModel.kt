package superapps.minegocio.ui.workspacesession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceSessionViewModel(
    private val repository: WorkspaceSessionRepository = WorkspaceSessionRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkspaceSessionUiState())
    val uiState: StateFlow<WorkspaceSessionUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val workspaces = repository.listMyWorkspaces()
                val active = workspaces.firstOrNull { it.isActive } ?: workspaces.firstOrNull()
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.toString(),
                    )
                }
                WorkspaceSelectionStore.selectedWorkspaceId = null
            }
        }
    }

    fun selectWorkspace(workspaceId: String) {
        if (workspaceId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.setActiveWorkspace(workspaceId)
                val workspaces = repository.listMyWorkspaces()
                val active = workspaces.firstOrNull { it.isActive } ?: workspaces.firstOrNull()
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
}
