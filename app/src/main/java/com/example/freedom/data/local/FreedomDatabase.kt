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
    version = 2,
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
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCreationCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCreationCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        val dao = database.milkRecordDao()
                        val currentTime = System.currentTimeMillis()
                        val initialSeedRecords = listOf(
                            MilkRecordEntity(
                                orgId = "ORG001",
                                workerId = "WORKER001",
                                farmerName = "Ramesh",
                                quantity = 18.0,
                                fat = 4.2,
                                snf = 8.6,
                                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                                payableAmount = MilkRecordEntity.calculatePayableAmount(18.0, 4.2, 8.6),
                                createdAt = currentTime - (1000 * 60 * 60 * 3),
                                updatedAt = currentTime - (1000 * 60 * 60 * 3),
                                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                            ),
                            MilkRecordEntity(
                                orgId = "ORG001",
                                workerId = "WORKER001",
                                farmerName = "Suresh",
                                quantity = 12.0,
                                fat = 4.0,
                                snf = 8.5,
                                paymentStatus = MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                                paymentMethod = MilkRecordEntity.METHOD_CASH,
                                paymentReference = "CASH-REC-001",
                                paymentTimestamp = currentTime - (1000 * 60 * 60 * 2),
                                payableAmount = MilkRecordEntity.calculatePayableAmount(12.0, 4.0, 8.5),
                                amountPaid = MilkRecordEntity.calculatePayableAmount(12.0, 4.0, 8.5),
                                createdAt = currentTime - (1000 * 60 * 60 * 2),
                                updatedAt = currentTime - (1000 * 60 * 60 * 2),
                                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                            ),
                            MilkRecordEntity(
                                orgId = "ORG001",
                                workerId = "WORKER001",
                                farmerName = "Mahesh",
                                quantity = 20.0,
                                fat = 4.3,
                                snf = 8.7,
                                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                                payableAmount = MilkRecordEntity.calculatePayableAmount(20.0, 4.3, 8.7),
                                createdAt = currentTime - (1000 * 60 * 60 * 1),
                                updatedAt = currentTime - (1000 * 60 * 60 * 1),
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
