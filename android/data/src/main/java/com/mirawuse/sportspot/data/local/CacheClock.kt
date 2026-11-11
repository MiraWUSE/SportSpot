package com.mirawuse.sportspot.data.local

fun interface CacheClock {
    fun nowMillis(): Long
}
