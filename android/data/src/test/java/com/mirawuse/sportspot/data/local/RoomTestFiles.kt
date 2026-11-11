package com.mirawuse.sportspot.data.local

import android.content.Context
import android.content.ContextWrapper
import java.io.File

// Robolectric includes test method names in its default paths, which can exceed SQLite's Windows path limit.
internal fun Context.withDatabaseDirectory(directory: File): Context =
    object : ContextWrapper(this) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File = File(directory, name)
    }
