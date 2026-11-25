// Minimaler Stellvertreter für androidx.lifecycle.ViewModel im Desktop-Prüfwerkzeug.
package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class ViewModel {
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    protected open fun onCleared() {}
    fun clear() {
        scope.cancel()
        onCleared()
    }
}

val ViewModel.viewModelScope: CoroutineScope get() = scope
