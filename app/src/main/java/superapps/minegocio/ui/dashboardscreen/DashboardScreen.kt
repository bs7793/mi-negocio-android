package superapps.minegocio.ui.dashboardscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import superapps.minegocio.R
import java.time.format.DateTimeFormatter

private const val ALL_WAREHOUSES_OPTION_ID: Long = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var warehouseMenuOpen by rememberSaveable { mutableStateOf(false) }
    val selectedWarehouseName = when (uiState.selectedWarehouseId) {
        ALL_WAREHOUSES_OPTION_ID -> stringResource(R.string.dashboard_all_warehouses_option)
        else -> uiState.warehouses.firstOrNull { it.id == uiState.selectedWarehouseId }?.name
            ?: stringResource(R.string.dashboard_all_warehouses_option)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_screen_title)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_period_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = uiState.period.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedWarehouseName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sales_warehouse_label)) },
                        trailingIcon = {
                            IconButton(onClick = { warehouseMenuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.sales_select_warehouse_label),
                                )
                            }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { warehouseMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = warehouseMenuOpen,
                        onDismissRequest = { warehouseMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dashboard_all_warehouses_option)) },
                            onClick = {
                                warehouseMenuOpen = false
                                viewModel.selectWarehouse(ALL_WAREHOUSES_OPTION_ID)
                            },
                        )
                        uiState.warehouses.forEach { warehouse ->
                            DropdownMenuItem(
                                text = { Text(warehouse.name) },
                                onClick = {
                                    warehouseMenuOpen = false
                                    viewModel.selectWarehouse(warehouse.id)
                                },
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    DashboardKpiCard(
                        title = stringResource(R.string.dashboard_income_label),
                        amount = uiState.summary.incomeTotal,
                    )
                }
                item {
                    DashboardKpiCard(
                        title = stringResource(R.string.dashboard_cost_label),
                        amount = uiState.summary.costTotal,
                    )
                }
                item {
                    DashboardKpiCard(
                        title = stringResource(R.string.dashboard_profit_label),
                        amount = uiState.summary.profitTotal,
                    )
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    title: String,
    amount: Double,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.dashboard_amount_value, amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
