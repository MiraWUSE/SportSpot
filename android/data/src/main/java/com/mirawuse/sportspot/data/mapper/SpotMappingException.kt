package com.mirawuse.sportspot.data.mapper

/** A malformed server object; the message deliberately excludes raw response values. */
class SpotMappingException : RuntimeException("Invalid spot response")
