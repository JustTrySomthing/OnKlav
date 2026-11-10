package app.onklav.home

/** Resolve before staging a message. Never guess between multiple subscriptions. */
internal object SendingSubscription {
    fun resolve(explicit: Int?, saved: Int?, systemDefault: Int, active: List<Int>?): Int {
        val available = active?.filter { it >= 0 }?.distinct()
        fun usable(id: Int?) = id != null && id >= 0 && (available == null || id in available)
        if (explicit != null) {
            check(usable(explicit)) { "The message's sending SIM is no longer active. Choose a sending SIM in OnKlav Setup." }
            return explicit
        }
        if (usable(saved)) return saved!!
        // An inactive saved choice can recover on a single-SIM phone. On a dual-SIM
        // phone ask again rather than silently moving an explicit choice to another plan.
        if (saved != null && saved >= 0 && available != null && available.size > 1)
            error("The selected SIM is no longer active. Choose a sending SIM in OnKlav Setup.")
        if (usable(systemDefault)) return systemDefault
        if (available?.size == 1) return available.single()
        check(available?.isEmpty() != true) { "No active SIM is available for text messages." }
        error("Choose a sending SIM in OnKlav Setup.")
    }
}
