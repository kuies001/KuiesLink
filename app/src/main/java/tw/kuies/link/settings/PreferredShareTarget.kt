package tw.kuies.link.settings

enum class PreferredShareTarget(val storedValue: String) {
    CLEAN_URL("clean"),
    KUIES_SHORT_URL("short"),
    ;

    companion object {
        val DEFAULT = KUIES_SHORT_URL

        fun fromStoredValue(value: String?): PreferredShareTarget =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}
