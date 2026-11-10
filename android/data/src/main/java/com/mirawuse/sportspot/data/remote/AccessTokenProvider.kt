package com.mirawuse.sportspot.data.remote

/** Supplies the current user session token; never the project's API key. */
fun interface AccessTokenProvider {
    fun getAccessToken(): String?
}
