package com.mirawuse.sportspot.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saved_spots ADD COLUMN fetched_at_epoch_millis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE saved_spot_snapshot ADD COLUMN fetched_at_epoch_millis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE search_snapshots ADD COLUMN fetched_at_epoch_millis INTEGER NOT NULL DEFAULT 0")
        }
    }
