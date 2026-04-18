package superapps.minegocio.ui.dashboardscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import superapps.minegocio.R
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val periodFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    }
    val amountFormatter = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_screen_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                if (!uiState.isLoading && !uiState.isSummaryUpdating) {
                    viewModel.refresh()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uiState.errorMessage != null) {
                    item(key = "error_banner") {
                        DashboardErrorBanner(message = uiState.errorMessage.orEmpty())
                    }
                }

                item(key = "period_hero") {
                    DashboardPeriodHeroCard(
                        periodText = uiState.period.format(periodFormatter),
                    )
                }

                item(key = "warehouse") {
                    WarehouseExposedDropdown(
                        selectedLabel = selectedWarehouseName,
                        expanded = warehouseMenuOpen,
                        onExpandedChange = { warehouseMenuOpen = it },
                        onSelectAllWarehouses = {
                            warehouseMenuOpen = false
                            viewModel.selectWarehouse(ALL_WAREHOUSES_OPTION_ID)
                        },
                        warehouses = uiState.warehouses,
                        onSelectWarehouse = { id ->
                            warehouseMenuOpen = false
                            viewModel.selectWarehouse(id)
                        },
                    )
                }

                if (uiState.isLoading) {
                    item(key = "kpi_skeleton") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(3) {
                                DashboardKpiSkeletonCard()
                            }
                        }
                    }
                } else {
                    item(key = "kpis") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (uiState.isSummaryUpdating) 0.55f else 1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                DashboardKpiCard(
                                    title = stringResource(R.string.dashboard_income_label),
                                    amount = uiState.summary.incomeTotal,
                                    amountFormatter = amountFormatter,
                                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                                    emphasis = false,
                                )
                                DashboardKpiCard(
                                    title = stringResource(R.string.dashboard_cost_label),
                                    amount = uiState.summary.costTotal,
                                    amountFormatter = amountFormatter,
                                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                                    emphasis = false,
                                )
                                DashboardKpiCard(
                                    title = stringResource(R.string.dashboard_profit_label),
                                    amount = uiState.summary.profitTotal,
                                    amountFormatter = amountFormatter,
                                    icon = Icons.Outlined.EmojiEvents,
                                    emphasis = true,
                                )
                            }
                            if (uiState.isSummaryUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    }
                }

                item(key = "sales_feed_header") {
                    Text(
                        text = stringResource(R.string.dashboard_sales_feed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alpha(if (uiState.isSummaryUpdating) 0.55f else 1f),
                    )
                }

                if (uiState.isLoading) {
                    item(key = "sales_feed_skeleton") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(4) {
                                DashboardSalesFeedSkeletonRow()
                            }
                        }
                    }
                } else {
                    uiState.feedErrorMessage?.let { message ->
                        item(key = "sales_feed_error") {
                            DashboardFeedErrorBanner(message = message)
                        }
                    }
                    if (uiState.salesFeed.isEmpty()) {
                        if (uiState.feedErrorMessage == null) {
                            item(key = "sales_feed_empty") {
                                Text(
                                    text = stringResource(R.string.dashboard_sales_feed_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    } else {
                        items(
                            items = uiState.salesFeed,
                            key = { it.saleId },
                        ) { sale ->
                            DashboardSalesFeedRow(
                                item = sale,
                                amountFormatter = amountFormatter,
                                locale = locale,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (uiState.isSummaryUpdating) 0.55f else 1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = stringResource(R.string.dashboard_error_icon_a11y),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardPeriodHeroCard(periodText: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_period_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = periodText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.dashboard_period_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseExposedDropdown(
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectAllWarehouses: () -> Unit,
    warehouses: List<DashboardWarehouse>,
    onSelectWarehouse: (Long) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.sales_warehouse_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dashboard_all_warehouses_option)) },
                onClick = onSelectAllWarehouses,
            )
            warehouses.forEach { warehouse ->
                DropdownMenuItem(
                    text = { Text(warehouse.name) },
                    onClick = { onSelectWarehouse(warehouse.id) },
                )
            }
        }
    }
}

@Composable
private fun DashboardKpiSkeletonCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(14.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {}
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(22.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
        }
    }
}

@Composable
private fun DashboardKpiCard(
    title: String,
    amount: Double,
    amountFormatter: NumberFormat,
    icon: ImageVector,
    emphasis: Boolean,
) {
    val formatted = amountFormatter.format(amount)
    val rowA11y = stringResource(R.string.dashboard_kpi_row_a11y, title, formatted)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = rowA11y },
        shape = MaterialTheme.shapes.large,
        colors = if (emphasis) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
            )
        },
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (emphasis) 3.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasis) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(26.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (emphasis) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DashboardFeedErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardSalesFeedSkeletonRow() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {}
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {}
            }
            Surface(
                modifier = Modifier
                    .width(72.dp)
                    .height(22.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {}
        }
    }
}

@Composable
private fun DashboardSalesFeedRow(
    item: DashboardSalesFeedItem,
    amountFormatter: NumberFormat,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val dateText = remember(item.soldAt, locale) {
        formatSoldAtForDisplay(item.soldAt, locale)
    }
    val paymentLabel = paymentMethodLabel(item.paymentMethod)
    val formattedAmount = amountFormatter.format(item.total)
    val rowA11y = stringResource(
        R.string.dashboard_sales_feed_row_a11y,
        dateText,
        formattedAmount,
        paymentLabel,
    )
    ElevatedCard(
        modifier = modifier.semantics { contentDescription = rowA11y },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val customer = item.customerName?.takeIf { it.isNotBlank() }
                    if (customer != null) {
                        Text(
                            text = customer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = paymentLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatSoldAtForDisplay(soldAt: String, locale: Locale): String {
    return try {
        val odt = OffsetDateTime.parse(soldAt)
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(odt)
    } catch (_: Exception) {
        soldAt
    }
}

@Composable
private fun paymentMethodLabel(method: String?): String {
    if (method.isNullOrBlank()) {
        return stringResource(R.string.dashboard_sales_feed_payment_unknown)
    }
    return when (method.lowercase(Locale.ROOT)) {
        "cash" -> stringResource(R.string.sales_payment_cash)
        "card" -> stringResource(R.string.sales_payment_card)
        "transfer" -> stringResource(R.string.sales_payment_transfer)
        "other" -> stringResource(R.string.sales_payment_other)
        else -> method
    }
}
