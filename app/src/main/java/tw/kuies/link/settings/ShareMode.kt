package tw.kuies.link.settings

enum class ShareMode(val storedValue: String) {
    /** Show the cleaning result and let the user decide what to do. */
    CONFIRM("confirm"),

    /** Clean and open the system share sheet immediately. */
    QUICK("quick"),
    ;

    companion object {
        val DEFAULT = CONFIRM

        fun fromStoredValue(value: String?): ShareMode =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}
