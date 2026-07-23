package com.example.unpawse.data.capture

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.unpawse.data.usage.DailyUsageEntity
import com.example.unpawse.data.usage.MonitoredAppEntity
import com.example.unpawse.data.usage.UsageDao

/**
 * The app's single Room database (named for its first entity, but now app-wide: captures + the
 * usage-tracking tables). The manual-DI [Companion.getInstance] singleton is owned by the
 * [com.example.unpawse.data.AppContainer] and is the seam a DI framework (Hilt) would later replace.
 *
 * Pre-release migration policy: schema changes bump [version] and rely on
 * `fallbackToDestructiveMigration` — acceptable while there are no real users. Add real
 * [androidx.room.migration.Migration]s (and `exportSchema = true`) before shipping.
 */
@Database(
    entities = [CaptureEntity::class, MonitoredAppEntity::class, DailyUsageEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var instance: CaptureDatabase? = null

        fun getInstance(context: Context): CaptureDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CaptureDatabase::class.java,
                    "unpawse.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
