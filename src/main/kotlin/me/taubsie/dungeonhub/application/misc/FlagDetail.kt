package me.taubsie.dungeonhub.application.misc

data class FlagDetail(val flagged: Boolean, val reason: String?, val staff: Long?, val evidence: String?) {
    private fun reason() = reason?.let { "`$reason`" } ?: ""

    private fun staff() = staff?.let { "<@$staff>" } ?: ""

    private fun evidence() = evidence?.let { "||$evidence||" } ?: ""

    fun format(asList: Boolean): String {
        return "${if (asList) "-# - " else ""}Reason: ${reason()}\n${if (asList) "-# - " else ""}Added by: ${staff()}\n${if (asList) "-# - " else ""}${evidence()}".trim()
    }

    override fun toString(): String {
        return format(false)
    }

    class Builder {
        private var flagged: Boolean = false
        private var reason: String? = null
        private var staff: Long? = null
        private var evidence: String? = null

        fun flagged(flagged: Boolean): Builder {
            this.flagged = flagged
            return this
        }

        fun reason(reason: String?): Builder {
            this.reason = reason
            return this
        }

        fun staff(staff: Long?): Builder {
            this.staff = staff
            return this
        }

        fun evidence(evidence: String?): Builder {
            this.evidence = evidence
            return this
        }

        fun build(): FlagDetail {
            return FlagDetail(flagged, reason, staff, evidence)
        }
    }

    companion object FlagDetailBuilder {
        fun builder(): Builder {
            return Builder()
        }
    }
}