package com.mediagrab.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mediagrab.app.data.local.dao.DownloadDao
import com.mediagrab.app.data.local.entity.DownloadEntity

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MediaGrabDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
