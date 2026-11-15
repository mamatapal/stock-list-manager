package app.stocklist.app.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

data class ReauthUi(val visible: Boolean = false, val loading: Boolean = false, val error: String? = null)

/**
 * Base for screens with sensitive actions. When the server (or the local cache) says a step-up is needed,
 * the action is parked, the password dialog is shown, and the action is retried after a correct password.
 */
abstract class StepUpViewModel(private val auth: AuthRepository) : ViewModel() {
    var reauth by mutableStateOf(ReauthUi())
        private set
    private var pending: (() -> Unit)? = null

    /** Returns true if the result asked for a step-up (the action will be retried automatically). */
    protected fun needsStepUp(result: ApiResult<*>, retry: () -> Unit): Boolean {
        if (result is ApiResult.Failure && result.error.needsStepUp) {
            pending = retry
            reauth = ReauthUi(visible = true)
            return true
        }
        return false
    }

    fun submitReauth(password: String) {
        reauth = reauth.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = auth.reauthenticate(password)) {
                is ApiResult.Ok -> {
                    reauth = ReauthUi()
                    pending?.invoke()
                    pending = null
                }
                is ApiResult.Failure -> reauth = reauth.copy(loading = false, error = r.error.message)
            }
        }
    }

    fun dismissReauth() {
        pending = null
        reauth = ReauthUi()
    }
}
