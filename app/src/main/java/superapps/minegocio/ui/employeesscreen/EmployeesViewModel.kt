package superapps.minegocio.ui.employeesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import superapps.minegocio.ui.WorkspaceScopeInvalidationBus
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore

data class EmployeesUiState(
    val isLoading: Boolean = true,
    val employees: List<Employee> = emptyList(),
    val inviteCodes: List<WorkspaceInviteCode> = emptyList(),
    val errorMessage: String? = null,
    val isInvitingEmployee: Boolean = false,
    val inviteErrorMessage: String? = null,
    val latestInviteCode: InviteCodeResult? = null,
    val isUpdatingEmployee: Boolean = false,
    val updateErrorMessage: String? = null,
    val isAcceptingInviteCode: Boolean = false,
    val acceptInviteErrorMessage: String? = null,
    val activeWorkspaceId: String? = null,
)

class EmployeesViewModel(
    private val repository: EmployeesRepository = EmployeesRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmployeesUiState())
    val uiState: StateFlow<EmployeesUiState> = _uiState.asStateFlow()
    private var activeWorkspaceId: String? = WorkspaceSelectionStore.selectedWorkspaceId

    init {
        load()
        observeWorkspaceChanges()
    }

    private fun observeWorkspaceChanges() {
        viewModelScope.launch {
            WorkspaceScopeInvalidationBus.workspaceChanges.collectLatest { workspaceId ->
                if (workspaceId == activeWorkspaceId) return@collectLatest
                activeWorkspaceId = workspaceId
                _uiState.update {
                    it.copy(
                        activeWorkspaceId = workspaceId,
                        inviteErrorMessage = null,
                        latestInviteCode = null,
                        acceptInviteErrorMessage = null,
                    )
                }
                load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = repository.fetchEmployees()
                val codes = repository.listInviteCodes()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        employees = list,
                        inviteCodes = codes,
                        errorMessage = null,
                        activeWorkspaceId = activeWorkspaceId,
                    )
                }
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

    fun createInviteCode(role: String) {
        val normalizedRole = role.trim().lowercase()
        if (normalizedRole.isBlank()) {
            _uiState.update { it.copy(inviteErrorMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isInvitingEmployee = true, inviteErrorMessage = null) }
            try {
                val result = repository.createInviteCode(role = normalizedRole)
                if (!result.success) {
                    _uiState.update {
                        it.copy(
                            isInvitingEmployee = false,
                            inviteErrorMessage = result.message ?: "Could not invite employee",
                        )
                    }
                    return@launch
                }

                val codes = repository.listInviteCodes()
                _uiState.update { state ->
                    state.copy(
                        isInvitingEmployee = false,
                        inviteErrorMessage = null,
                        latestInviteCode = result,
                        inviteCodes = codes,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInvitingEmployee = false,
                        inviteErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun revokeInviteCode(inviteCode: String) {
        if (inviteCode.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInvitingEmployee = true, inviteErrorMessage = null) }
            try {
                val result = repository.revokeInviteCode(inviteCode)
                if (!result.success) {
                    _uiState.update {
                        it.copy(
                            isInvitingEmployee = false,
                            inviteErrorMessage = result.message ?: "Could not revoke invite code",
                        )
                    }
                    return@launch
                }
                val codes = repository.listInviteCodes()
                _uiState.update {
                    it.copy(
                        isInvitingEmployee = false,
                        inviteErrorMessage = null,
                        inviteCodes = codes,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInvitingEmployee = false,
                        inviteErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun acceptInviteCode(code: String, onAccepted: (() -> Unit)? = null) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(acceptInviteErrorMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAcceptingInviteCode = true, acceptInviteErrorMessage = null) }
            try {
                val result = repository.acceptInviteCode(trimmed)
                if (!result.success) {
                    _uiState.update {
                        it.copy(
                            isAcceptingInviteCode = false,
                            acceptInviteErrorMessage = result.message ?: "Could not accept invite code",
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(isAcceptingInviteCode = false, acceptInviteErrorMessage = null) }
                onAccepted?.invoke()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAcceptingInviteCode = false,
                        acceptInviteErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun updateEmployee(targetUserId: String, role: String, status: String) {
        if (targetUserId.isBlank() || role.isBlank() || status.isBlank()) {
            _uiState.update { it.copy(updateErrorMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingEmployee = true, updateErrorMessage = null) }
            try {
                val result = repository.updateEmployee(
                    targetUserId = targetUserId,
                    role = role,
                    status = status,
                )
                if (!result.success) {
                    _uiState.update {
                        it.copy(
                            isUpdatingEmployee = false,
                            updateErrorMessage = result.message ?: "Could not update employee",
                        )
                    }
                    return@launch
                }

                val updated = result.member
                _uiState.update { state ->
                    state.copy(
                        isUpdatingEmployee = false,
                        updateErrorMessage = null,
                        employees = if (updated == null) state.employees else mergeEmployee(state.employees, updated),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingEmployee = false,
                        updateErrorMessage = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun clearInviteError() {
        _uiState.update { it.copy(inviteErrorMessage = null) }
    }

    fun clearAcceptInviteError() {
        _uiState.update { it.copy(acceptInviteErrorMessage = null) }
    }

    fun clearLatestInviteCode() {
        _uiState.update { it.copy(latestInviteCode = null) }
    }

    fun clearUpdateError() {
        _uiState.update { it.copy(updateErrorMessage = null) }
    }

    private fun mergeEmployee(existing: List<Employee>, employee: Employee): List<Employee> {
        val index = existing.indexOfFirst { it.userId == employee.userId }
        if (index == -1) return listOf(employee) + existing
        return existing.map { if (it.userId == employee.userId) employee else it }
    }
}
