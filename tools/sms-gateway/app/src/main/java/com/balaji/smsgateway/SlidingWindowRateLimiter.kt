package com.balaji.smsgateway

class SlidingWindowRateLimiter(
    private val perMinute: Int,
    private val perHour: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val minuteWindow = ArrayDeque<Long>()
    private val hourWindow = ArrayDeque<Long>()

    init {
        require(perMinute > 0) { "perMinute must be positive" }
        require(perHour > 0) { "perHour must be positive" }
    }

    @Synchronized
    fun tryAcquire(): Boolean {
        val timestamp = now()
        evictBefore(minuteWindow, timestamp - MINUTE_MILLIS)
        evictBefore(hourWindow, timestamp - HOUR_MILLIS)

        if (minuteWindow.size >= perMinute || hourWindow.size >= perHour) {
            return false
        }

        minuteWindow.addLast(timestamp)
        hourWindow.addLast(timestamp)
        return true
    }

    private fun evictBefore(window: ArrayDeque<Long>, cutoff: Long) {
        while (window.isNotEmpty() && window.first() <= cutoff) {
            window.removeFirst()
        }
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 3_600_000L
    }
}
