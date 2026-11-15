package app.stocklist.app.core.session

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The 5-minute step-up token after re-entering the password. Memory only. */
@Singleton
class StepUpCache @Inject constructor() {
    private var token: String? = null
    private var expiresAt: Instant = Instant.EPOCH

    @Synchronized
    fun valid(): String? = token?.takeIf { Instant.now().isBefore(expiresAt.minusSeconds(15)) }

    @Synchronized
    fun store(token: String, expiresAtIso: String) {
        this.token = token
        this.expiresAt = runCatching { Instant.parse(expiresAtIso) }.getOrDefault(Instant.now().plusSeconds(240))
    }

    @Synchronized
    fun clear() {
        token = null
        expiresAt = Instant.EPOCH
    }
}
