package superapps.minegocio.ui.employeesscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmployeesUiState(
    val isLoading: Boolean = true,
    val employees: List<Employee> = emptyList(),
    val errorMessage: String? = null,
    val isInvitingEmployee: Boolean = false,
    val inviteErrorMessage: String? = null,
    val isUpdatingEmployee: Boolean = false,
    val updateErrorMessage: String? = null,
)

class EmployeesViewModel(
    private val repository: EmployeesRepository = EmployeesRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmployeesUiState())
    val uiState: StateFlow<EmployeesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val list = repository.fetchEmployees()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        employees = list,
                        errorMessage = null,
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

    fun inviteEmployee(email: String, role: String) {
        val normalizedEmail = email.trim().lowercase()
        val normalizedRole = role.trim().lowercase()
        if (normalizedEmail.isBlank() || normalizedRole.isBlank()) {
            _uiState.update { it.copy(inviteErrorMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isInvitingEmployee = true, inviteErrorMessage = null) }
            try {
                val result = repository.inviteEmployee(
                    email = normalizedEmail,
                    role = normalizedRole,
                )
                if (!result.success) {
                    _uiState.update {
                        it.copy(
                            isInvitingEmployee = false,
                            inviteErrorMessage = result.message ?: "Could not invite employee",
                        )
                    }
                    return@launch
                }

                val created = result.member
                _uiState.update { state ->
                    state.copy(
                        isInvitingEmployee = false,
                        inviteErrorMessage = null,
                        employees = if (created == null) state.employees else mergeEmployee(state.employees, created),
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

    fun clearUpdateError() {
        _uiState.update { it.copy(updateErrorMessage = null) }
    }

    private fun mergeEmployee(existing: List<Employee>, employee: Employee): List<Employee> {
        val index = existing.indexOfFirst { it.userId == employee.userId }
        if (index == -1) return listOf(employee) + existing
        return existing.map { if (it.userId == employee.userId) employee else it }
    }
}
