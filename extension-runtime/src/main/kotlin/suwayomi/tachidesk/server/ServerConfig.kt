package suwayomi.tachidesk.server

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dareader-owned server config shim. Only the keys touched by vendored
 * network code are modeled. FlareSolverr comes from the environment
 * (`DAREADER_FLARE_URL` enables it) or [configure]; disabled by default.
 */
object serverConfig {
    val flareSolverrEnabled = MutableStateFlow(false)
    val flareSolverrAsResponseFallback = MutableStateFlow(false)
    val flareSolverrTimeout = MutableStateFlow(60)
    val flareSolverrUrl = MutableStateFlow("")
    val flareSolverrSessionName = MutableStateFlow("dareader")
    val flareSolverrSessionTtl = MutableStateFlow(10)

    init {
        val url = System.getenv("DAREADER_FLARE_URL").orEmpty()
        if (url.isNotBlank()) {
            configure(url = url)
        }
    }

    fun configure(
        url: String,
        timeoutSeconds: Int = flareSolverrTimeout.value,
        sessionName: String = flareSolverrSessionName.value,
        sessionTtlMinutes: Int = flareSolverrSessionTtl.value,
        asResponseFallback: Boolean = flareSolverrAsResponseFallback.value,
    ) {
        flareSolverrUrl.value = url.removeSuffix("/")
        flareSolverrTimeout.value = timeoutSeconds
        flareSolverrSessionName.value = sessionName
        flareSolverrSessionTtl.value = sessionTtlMinutes
        flareSolverrAsResponseFallback.value = asResponseFallback
        flareSolverrEnabled.value = true
    }
}
