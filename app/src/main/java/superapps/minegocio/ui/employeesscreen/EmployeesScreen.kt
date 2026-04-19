package superapps.minegocio.ui.employeesscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import superapps.minegocio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmployeesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isInviteOpen by rememberSaveable { mutableStateOf(false) }
    var acceptCode by rememberSaveable { mutableStateOf("") }

    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.employees_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isInviteOpen = true
                            viewModel.clearInviteError()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_employee),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.employees_accept_code_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OutlinedTextField(
                                    value = acceptCode,
                                    onValueChange = {
                                        acceptCode = it
                                        if (uiState.acceptInviteErrorMessage != null) {
                                            viewModel.clearAcceptInviteError()
                                        }
                                    },
                                    label = { Text(stringResource(R.string.employees_accept_code_label)) },
                                    placeholder = { Text(stringResource(R.string.employees_accept_code_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                                if (!uiState.acceptInviteErrorMessage.isNullOrBlank()) {
                                    Text(
                                        text = uiState.acceptInviteErrorMessage ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.acceptInviteCode(acceptCode) {
                                            acceptCode = ""
                                            viewModel.load()
                                        }
                                    },
                                    enabled = !uiState.isAcceptingInviteCode && acceptCode.trim().isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (uiState.isAcceptingInviteCode) {
                                            stringResource(R.string.employees_accept_code_action_loading)
                                        } else {
                                            stringResource(R.string.employees_accept_code_action)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.inviteCodes.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.employees_active_codes_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(uiState.inviteCodes, key = { it.inviteId }) { code ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(code.inviteCode, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${code.role.replaceFirstChar { it.uppercase() }} · ${code.status.replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = stringResource(R.string.employees_generated_code_expires, code.expiresAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    AssistChip(
                                        onClick = { viewModel.revokeInviteCode(code.inviteCode) },
                                        enabled = !uiState.isInvitingEmployee,
                                        label = { Text(stringResource(R.string.employees_action_revoke_code)) },
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.employees.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.employees_empty_state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item { Box(modifier = Modifier.fillMaxWidth().height(4.dp)) }
                    items(uiState.employees, key = { it.userId }) { employee ->
                        EmployeeCard(
                            employee = employee,
                            isUpdating = uiState.isUpdatingEmployee,
                            onPromoteToAdmin = {
                                viewModel.updateEmployee(
                                    targetUserId = employee.userId,
                                    role = "admin",
                                    status = "active",
                                )
                            },
                            onSetMember = {
                                viewModel.updateEmployee(
                                    targetUserId = employee.userId,
                                    role = "member",
                                    status = "active",
                                )
                            },
                            onDisable = {
                                viewModel.updateEmployee(
                                    targetUserId = employee.userId,
                                    role = employee.role,
                                    status = "disabled",
                                )
                            },
                            onEnable = {
                                viewModel.updateEmployee(
                                    targetUserId = employee.userId,
                                    role = employee.role,
                                    status = "active",
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (isInviteOpen) {
        InviteEmployeeBottomSheet(
            isInviting = uiState.isInvitingEmployee,
            errorMessage = uiState.inviteErrorMessage,
            latestInviteCode = uiState.latestInviteCode,
            onDismissRequest = {
                isInviteOpen = false
                viewModel.clearInviteError()
                viewModel.clearLatestInviteCode()
            },
            onCreateInviteCode = { role -> viewModel.createInviteCode(role) },
            onClearError = { viewModel.clearInviteError() },
            onClearLatestCode = { viewModel.clearLatestInviteCode() },
        )
    }
}

@Composable
private fun EmployeeCard(
    employee: Employee,
    isUpdating: Boolean,
    onPromoteToAdmin: () -> Unit,
    onSetMember: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = employee.fullName?.takeIf { it.isNotBlank() }
                    ?: employee.email?.takeIf { it.isNotBlank() }
                    ?: employee.userId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!employee.email.isNullOrBlank()) {
                Text(
                    text = employee.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${employee.role.replaceFirstChar { it.uppercase() }} · ${employee.status.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (employee.role != "admin") {
                AssistChip(
                    onClick = onPromoteToAdmin,
                    enabled = !isUpdating,
                    label = { Text(stringResource(R.string.employees_action_promote_admin)) },
                )
            } else {
                AssistChip(
                    onClick = onSetMember,
                    enabled = !isUpdating,
                    label = { Text(stringResource(R.string.employees_action_set_member)) },
                )
            }

            if (employee.status == "active") {
                AssistChip(
                    onClick = onDisable,
                    enabled = !isUpdating,
                    label = { Text(stringResource(R.string.employees_action_disable)) },
                )
            } else {
                AssistChip(
                    onClick = onEnable,
                    enabled = !isUpdating,
                    label = { Text(stringResource(R.string.employees_action_enable)) },
                )
            }
        }
    }
}
