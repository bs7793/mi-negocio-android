package superapps.minegocio.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emits when sales data changes so consumers (e.g. dashboard summaries) can refresh.
 * Burst-safe: extra buffer coalesces rapid events; collectors should use [collectLatest].
 */
object SalesSummaryInvalidationBus {
    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val salesSummaryInvalidations: SharedFlow<Unit> = _events.asSharedFlow()

    fun invalidateSalesSummary() {
        _events.tryEmit(Unit)
    }
}
