package superapps.minegocio.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import superapps.minegocio.R
import superapps.minegocio.ui.auth.AuthUiState
import superapps.minegocio.ui.workspacesession.WorkspaceSummary

@Composable
fun ProfileScreen(
    authUiState: AuthUiState,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onDismissAuthError: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenWarehouses: () -> Unit,
    onOpenEmployees: () -> Unit,
    inviteCodeError: String?,
    isSubmittingInviteCode: Boolean,
    workspaceName: String?,
    workspaces: List<WorkspaceSummary>,
    onSubmitInviteCode: (String) -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onClearInviteCodeError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inviteCode by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.workspace_active_label, workspaceName ?: "-"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                workspaces.forEach { workspace ->
                    OutlinedButton(
                        onClick = { onSelectWorkspace(workspace.workspaceId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (workspace.isActive) {
                                "${workspace.workspaceName} (${stringResource(R.string.workspace_active_badge)})"
                            } else {
                                workspace.workspaceName
                            },
                        )
                    }
                }
            }
        }

        if (authUiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (authUiState.isAnonymous) {
            Surface(
                onClick = onSignInWithGoogle,
                enabled = !authUiState.isLoading,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.auth_action_sign_in_google),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (!authUiState.isAnonymous) {
            OutlinedButton(
                onClick = onSignOut,
                enabled = !authUiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.auth_action_sign_out))
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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.employees_accept_code_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = {
                        inviteCode = it
                        if (!inviteCodeError.isNullOrBlank()) onClearInviteCodeError()
                    },
                    label = { Text(stringResource(R.string.employees_accept_code_label)) },
                    placeholder = { Text(stringResource(R.string.employees_accept_code_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (!inviteCodeError.isNullOrBlank()) {
                    Text(
                        text = inviteCodeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = { onSubmitInviteCode(inviteCode.trim()) },
                    enabled = !isSubmittingInviteCode && inviteCode.trim().isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (isSubmittingInviteCode) {
                            stringResource(R.string.employees_accept_code_action_loading)
                        } else {
                            stringResource(R.string.employees_accept_code_action)
                        },
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.profile_section_management),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ProfileManagementRow(
                    icon = Icons.Filled.Category,
                    label = stringResource(R.string.profile_menu_categories),
                    onClick = onOpenCategories,
                    showDividerBelow = true,
                )
                ProfileManagementRow(
                    icon = Icons.Filled.Inventory2,
                    label = stringResource(R.string.profile_menu_products),
                    onClick = onOpenProducts,
                    showDividerBelow = true,
                )
                ProfileManagementRow(
                    icon = Icons.Filled.ShoppingCart,
                    label = stringResource(R.string.profile_menu_sales),
                    onClick = onOpenSales,
                    showDividerBelow = true,
                )
                ProfileManagementRow(
                    icon = Icons.Filled.Warehouse,
                    label = stringResource(R.string.profile_menu_warehouses),
                    onClick = onOpenWarehouses,
                    showDividerBelow = true,
                )
                ProfileManagementRow(
                    icon = Icons.Filled.People,
                    label = stringResource(R.string.profile_menu_employees),
                    onClick = onOpenEmployees,
                    showDividerBelow = false,
                )
            }
        }
    }
}

@Composable
private fun ProfileManagementRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    showDividerBelow: Boolean,
) {
    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, role = Role.Button),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
        if (showDividerBelow) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}
