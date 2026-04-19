package superapps.minegocio.ui.reportsscreen.dailycashclosure

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyCashClosureScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyCashClosureViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var warehouseMenuOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_daily_cash_closure_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up),
                        )
                    }
                },
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
                ReportHeaderCard(
                    dateText = uiState.reportDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    warehouseName = uiState.warehouses.firstOrNull { it.id == uiState.selectedWarehouseId }?.name,
                )
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val selectedWarehouseName = uiState.warehouses
                        .firstOrNull { it.id == uiState.selectedWarehouseId }
                        ?.name
                        ?: stringResource(R.string.sales_select_warehouse_placeholder)
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
                    DropdownMenu(
                        expanded = warehouseMenuOpen,
                        onDismissRequest = { warehouseMenuOpen = false },
                    ) {
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
            } else if (uiState.errorMessage == null) {
                item {
                    TotalsCard(
                        salesCount = uiState.summary.salesCount,
                        unitsSold = uiState.summary.unitsSold,
                        grossTotal = uiState.summary.grossTotal,
                    )
                }
                item {
                    BreakdownCard(
                        cash = uiState.summary.cashTotal,
                        card = uiState.summary.cardTotal,
                        transfer = uiState.summary.transferTotal,
                        other = uiState.summary.otherTotal,
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
private fun ReportHeaderCard(
    dateText: String,
    warehouseName: String?,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.reports_daily_cash_closure_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.reports_date_value, dateText),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.reports_warehouse_value,
                    warehouseName ?: stringResource(R.string.sales_select_warehouse_placeholder),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TotalsCard(
    salesCount: Int,
    unitsSold: Double,
    grossTotal: Double,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.reports_daily_totals_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.reports_sales_count_value, salesCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.reports_units_sold_value, unitsSold),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.reports_gross_total_value, grossTotal),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BreakdownCard(
    cash: Double,
    card: Double,
    transfer: Double,
    other: Double,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.reports_daily_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.reports_cash_total_value, cash),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.reports_card_total_value, card),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.reports_transfer_total_value, transfer),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.reports_other_total_value, other),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
