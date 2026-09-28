package com.example.freedom.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freedom.data.local.dao.MilkRecordDao
import com.example.freedom.data.local.entity.MilkRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [MilkRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FreedomDatabase : RoomDatabase() {

    abstract fun milkRecordDao(): MilkRecordDao

    companion object {
        @Volatile
        private var INSTANCE: FreedomDatabase? = null

        fun getInstance(context: Context): FreedomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FreedomDatabase::class.java,
                    "freedom_local.db"
                )
                    .addCallback(DatabaseCreationCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCreationCallback : RoomDatabase.Callback() {
            /**
             * Called ONLY ONCE when the database file is first created on device storage.
             * It does NOT run on subsequent app launches or database opens.
             */
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed initial benchmark records once upon initial database creation
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        val dao = database.milkRecordDao()
                        val currentTime = System.currentTimeMillis()
                        val initialSeedRecords = listOf(
                            MilkRecordEntity(
                                farmerName = "Ramesh",
                                quantity = 18.0,
                                fat = 4.2,
                                snf = 8.6,
                                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                                createdAt = currentTime - (1000 * 60 * 60 * 3), // 3 hours ago today
                                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                            ),
                            MilkRecordEntity(
                                farmerName = "Suresh",
                                quantity = 12.0,
                                fat = 4.0,
                                snf = 8.5,
                                paymentStatus = MilkRecordEntity.PAYMENT_PAID,
                                createdAt = currentTime - (1000 * 60 * 60 * 2), // 2 hours ago today
                                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                            ),
                            MilkRecordEntity(
                                farmerName = "Mahesh",
                                quantity = 20.0,
                                fat = 4.3,
                                snf = 8.7,
                                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                                createdAt = currentTime - (1000 * 60 * 60 * 1), // 1 hour ago today
                                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                            )
                        )
                        dao.insertRecords(initialSeedRecords)
                    }
                }
            }
        }
    }
}
