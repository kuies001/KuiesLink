package tw.kuies.link.ui

/**
 * Remembers which share payload has already been turned into a cleaning session.
 *
 * The same payload reaches the Activity again after a configuration change or when the system
 * redelivers the intent; it must not silently restart cleaning or reopen the share sheet.
 */
class ShareIntake {
    private var lastSignature: String? = null

    fun accept(text: String?): Decision {
        val normalized = text?.trim()
        if (normalized.isNullOrEmpty()) return Decision.Empty
        val signature = "${normalized.length}:${normalized.hashCode()}"
        if (signature == lastSignature) return Decision.Duplicate
        lastSignature = signature
        return Decision.Accepted
    }

    enum class Decision { Accepted, Duplicate, Empty }
}
