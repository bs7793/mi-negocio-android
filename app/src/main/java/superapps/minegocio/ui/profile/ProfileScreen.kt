package superapps.minegocio.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import superapps.minegocio.R
import superapps.minegocio.ui.auth.AuthUiState

@Composable
fun ProfileScreen(
    authUiState: AuthUiState,
    onSignInWithGoogle: () -> Unit,
    onDismissAuthError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_screen_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        val authStatus = if (authUiState.isAnonymous) {
            stringResource(R.string.auth_status_anonymous)
        } else {
            val displayValue = listOf(
                authUiState.email,
                authUiState.displayName,
                authUiState.uid,
            ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
            stringResource(R.string.auth_status_signed_in, displayValue)
        }
        Text(
            text = authStatus,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (authUiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }

        if (authUiState.isAnonymous) {
            Button(
                onClick = onSignInWithGoogle,
                enabled = !authUiState.isLoading,
            ) {
                Text(text = stringResource(R.string.auth_action_sign_in_google))
            }
        }

        if (!authUiState.errorMessage.isNullOrBlank()) {
            Text(
                text = authUiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismissAuthError) {
                Text(text = stringResource(R.string.auth_action_dismiss_error))
            }
        }
    }
}
