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

val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("saved_spots", "search_spots")) {
                for (column in listOf("photo_url", "photo_source_url", "photo_author", "photo_license", "photo_license_url")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN $column TEXT")
                }
            }
        }
    }
