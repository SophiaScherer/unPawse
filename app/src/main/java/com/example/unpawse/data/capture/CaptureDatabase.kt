package com.example.unpawse.data.capture

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's Room database. Single entity for now; the manual-DI [Companion.getInstance] singleton
 * is the seam a DI framework (Hilt) would later replace.
 */
@Database(entities = [CaptureEntity::class], version = 1, exportSchema = false)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    companion object {
        @Volatile
        private var instance: CaptureDatabase? = null

        fun getInstance(context: Context): CaptureDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CaptureDatabase::class.java,
                    "unpawse.db",
                ).build().also { instance = it }
            }
    }
}
