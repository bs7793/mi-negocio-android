package superapps.minegocio.ui.employeesscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
    latestInviteCode: InviteCodeResult?,
    onDismissRequest: () -> Unit,
    onCreateInviteCode: (role: String) -> Unit,
    onClearError: () -> Unit,
    onClearLatestCode: () -> Unit,
) {
    val scroll = rememberScrollState()
    var role by rememberSaveable { mutableStateOf("member") }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isInviting, errorMessage) {
        if (submitAttempted && !isInviting && errorMessage == null) {
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
                    label = { Text(stringResource(R.string.employees_role_member)) },
                )
                AssistChip(
                    onClick = { role = "admin" },
                    label = { Text(stringResource(R.string.employees_role_admin)) },
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
                    onCreateInviteCode(role)
                },
                enabled = !isInviting,
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

    if (latestInviteCode?.success == true && !latestInviteCode.inviteCode.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onClearLatestCode,
            confirmButton = {
                Button(onClick = onClearLatestCode) {
                    Text(stringResource(R.string.employees_action_done))
                }
            },
            title = { Text(stringResource(R.string.employees_generated_code_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latestInviteCode.inviteCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.employees_generated_code_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.employees_generated_code_expires,
                            latestInviteCode.expiresAt ?: "",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }
}
