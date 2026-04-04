package superapps.minegocio.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

class AuthViewModel(
    private val sessionManager: AuthSessionManager = AuthSessionManager(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val authOperationMutex = Mutex()

    init {
        viewModelScope.launch {
            sessionManager.observeAuthState().collect { user ->
                _uiState.value = user.toUiState(loading = false, errorMessage = _uiState.value.errorMessage)
            }
        }
    }

    fun bootstrapAnonymousSession() {
        viewModelScope.launch {
            if (_uiState.value.uid != null) return@launch
            runAuthOperation(defaultErrorMessage = "Anonymous sign-in failed") {
                sessionManager.ensureAnonymousSession()
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            runAuthOperation(defaultErrorMessage = "Google sign-in failed") {
                sessionManager.signInWithGoogleIdToken(idToken)
            }
        }
    }

    fun signOutAndContinueAnonymously() {
        viewModelScope.launch {
            runAuthOperation(defaultErrorMessage = "Sign out failed") {
                sessionManager.signOutAndRecreateAnonymous()
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }
    }

    private suspend fun runAuthOperation(
        defaultErrorMessage: String,
        operation: suspend () -> Unit,
    ) {
        authOperationMutex.withLock {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                operation()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toAuthMessage(defaultErrorMessage),
                    )
                }
            }
        }
    }
}

private fun FirebaseUser?.toUiState(
    loading: Boolean,
    errorMessage: String?,
): AuthUiState {
    if (this == null) {
        return AuthUiState(
            isLoading = loading,
            errorMessage = errorMessage,
        )
    }
    return AuthUiState(
        isLoading = loading,
        isAnonymous = isAnonymous,
        uid = uid,
        email = email,
        displayName = displayName,
        errorMessage = errorMessage,
    )
}

private fun Throwable.toAuthMessage(defaultMessage: String): String {
    return when (this) {
        is FirebaseNetworkException -> "No network connection. Please try again."
        is FirebaseAuthInvalidCredentialsException -> "Invalid sign-in credentials."
        is FirebaseAuthInvalidUserException -> "The account is not available."
        is FirebaseAuthException -> {
            if (errorCode == "ERROR_TOO_MANY_REQUESTS") {
                "Too many attempts. Please try again later."
            } else {
                message ?: defaultMessage
            }
        }
        else -> defaultMessage
    }
}
