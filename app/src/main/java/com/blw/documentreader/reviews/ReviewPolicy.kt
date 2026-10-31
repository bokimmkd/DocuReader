package com.blw.documentreader.reviews

object ReviewPolicy {
    private const val DAY = 86_400_000L
    fun eligible(now: Long, first: Long, distinctDays: Int, last: Long): Boolean =
        first > 0 && now >= first && now - first >= 7 * DAY && distinctDays >= 3 &&
            (last == 0L || now >= last && now - last >= 30 * DAY)
}
