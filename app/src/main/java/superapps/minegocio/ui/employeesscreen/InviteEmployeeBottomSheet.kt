package superapps.minegocio.ui.employeesscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import superapps.minegocio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteEmployeeBottomSheet(
    isInviting: Boolean,
    errorMessage: String?,
    onDismissRequest: () -> Unit,
    onInvite: (email: String, role: String) -> Unit,
    onClearError: () -> Unit,
) {
    val scroll = rememberScrollState()
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("member") }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }
    val normalizedEmail = email.trim()
    val isEmailValid = normalizedEmail.contains("@") && normalizedEmail.contains(".")
    val showEmailError = submitAttempted && !isEmailValid

    LaunchedEffect(isInviting, errorMessage) {
        if (submitAttempted && !isInviting && errorMessage == null) {
            onDismissRequest()
            submitAttempted = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.employees_invite_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.employees_invite_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (errorMessage != null) onClearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.employees_field_email)) },
                placeholder = { Text(stringResource(R.string.employees_field_email_placeholder)) },
                singleLine = true,
                isError = showEmailError,
            )
            if (showEmailError) {
                Text(
                    text = stringResource(R.string.employees_field_email_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = stringResource(R.string.employees_field_role),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AssistChip(
                    onClick = { role = "member" },
                    label = { Text("Member") },
                )
                AssistChip(
                    onClick = { role = "admin" },
                    label = { Text("Admin") },
                )
            }
            Text(
                text = stringResource(R.string.employees_selected_role, role.replaceFirstChar { it.uppercase() }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    submitAttempted = true
                    onInvite(normalizedEmail, role)
                },
                enabled = !isInviting && isEmailValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                if (isInviting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.employees_action_inviting))
                    }
                } else {
                    Text(stringResource(R.string.employees_action_invite))
                }
            }
        }
    }
}
