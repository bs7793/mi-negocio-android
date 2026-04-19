package superapps.minegocio.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WorkspaceScopeInvalidationBus {
    private val _workspaceChanges = MutableSharedFlow<String?>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val workspaceChanges = _workspaceChanges.asSharedFlow()

    fun invalidate(workspaceId: String?) {
        _workspaceChanges.tryEmit(workspaceId)
    }
}
