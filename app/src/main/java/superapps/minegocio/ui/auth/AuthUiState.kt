package superapps.minegocio.ui.auth

data class AuthUiState(
    val isLoading: Boolean = true,
    val isAnonymous: Boolean = true,
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val errorMessage: String? = null,
)
