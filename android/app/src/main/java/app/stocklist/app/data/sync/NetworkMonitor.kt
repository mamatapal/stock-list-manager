package app.stocklist.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the internet connection while the app is running. When it comes back, a sync is requested straight away
 * (WorkManager's own retry would otherwise wait for its back-off delay). [online] feeds the sync status.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: SyncScheduler,
) {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        val cm = context.getSystemService(ConnectivityManager::class.java)
        _online.value = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val now = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val wasOffline = !_online.value
                _online.value = now
                if (now && wasOffline) scheduler.requestNow()
            }

            override fun onLost(network: Network) {
                _online.value = false
            }
        })
    }
}
