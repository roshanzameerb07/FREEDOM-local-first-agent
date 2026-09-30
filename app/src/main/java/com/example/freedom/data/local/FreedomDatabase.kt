package com.example.freedom.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freedom.data.local.dao.FarmerDao
import com.example.freedom.data.local.dao.MilkRecordDao
import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.domain.query.NameNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [MilkRecordEntity::class, FarmerEntity::class],
    version = 3,
    exportSchema = false
)
abstract class FreedomDatabase : RoomDatabase() {

    abstract fun milkRecordDao(): MilkRecordDao
    abstract fun farmerDao(): FarmerDao

    companion object {
        @Volatile
        private var INSTANCE: FreedomDatabase? = null

        /**
         * Migration from version 2 to 3:
         * 1. Creates the 'farmers' table
         * 2. Populates it from distinct farmerName values in milk_records
         * 3. Links milk_records.farmerId to the new farmers table
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create farmers table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `farmers` (
                        `farmerId` TEXT NOT NULL,
                        `farmerName` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(`farmerId`)
                    )
                """.trimIndent())

                // 2. Create unique index on normalizedName
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_farmers_normalizedName`
                    ON `farmers` (`normalizedName`)
                """.trimIndent())

                // 3. Populate farmers ONLY for unambiguous distinct names.
                //    COLLISION SAFETY RULE: If multiple distinct farmerName strings map to the same
                //    normalized name (HAVING COUNT(DISTINCT farmerName) > 1), they represent a collision.
                //    DO NOT silently merge them. They are excluded from automatic migration and
                //    their records retain farmerId = NULL for explicit manual review.
                db.execSQL("""
                    INSERT INTO `farmers` (`farmerId`, `farmerName`, `normalizedName`, `createdAt`, `isActive`)
                    SELECT
                        hex(randomblob(16)),
                        MIN(farmerName),
                        LOWER(TRIM(farmerName)),
                        strftime('%s', 'now') * 1000,
                        1
                    FROM milk_records
                    WHERE farmerName IS NOT NULL AND TRIM(farmerName) != ''
                    GROUP BY LOWER(TRIM(farmerName))
                    HAVING COUNT(DISTINCT farmerName) = 1
                """.trimIndent())

                // 4. Link milk_records.farmerId ONLY to unambiguous registered farmers
                db.execSQL("""
                    UPDATE milk_records
                    SET farmerId = (
                        SELECT farmerId FROM farmers
                        WHERE farmers.normalizedName = LOWER(TRIM(milk_records.farmerName))
                        LIMIT 1
                    )
                    WHERE farmerId IS NULL
                      AND LOWER(TRIM(farmerName)) IN (
                          SELECT normalizedName FROM farmers
                      )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): FreedomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FreedomDatabase::class.java,
                    "freedom_local.db"
                )
                    .addMigrations(MIGRATION_2_3)
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
                        val milkDao = database.milkRecordDao()
                        val farmerDao = database.farmerDao()
                        val currentTime = System.currentTimeMillis()

                        // Create seed farmers
                        val seedFarmers = listOf(
                            FarmerEntity(
                                farmerName = "Ramesh",
                                normalizedName = NameNormalizer.normalize("Ramesh"),
                                createdAt = currentTime
                            ),
                            FarmerEntity(
                                farmerName = "Suresh",
                                normalizedName = NameNormalizer.normalize("Suresh"),
                                createdAt = currentTime
                            ),
                            FarmerEntity(
                                farmerName = "Mahesh",
                                normalizedName = NameNormalizer.normalize("Mahesh"),
                                createdAt = currentTime
                            )
                        )
                        farmerDao.insertFarmers(seedFarmers)

                        // Retrieve inserted farmers to get their IDs
                        val ramesh = farmerDao.getFarmerByNormalizedName(NameNormalizer.normalize("Ramesh"))
                        val suresh = farmerDao.getFarmerByNormalizedName(NameNormalizer.normalize("Suresh"))
                        val mahesh = farmerDao.getFarmerByNormalizedName(NameNormalizer.normalize("Mahesh"))

                        // Create seed milk records with farmerId linked
                        val initialSeedRecords = listOf(
                            MilkRecordEntity(
                                orgId = "ORG001",
                                workerId = "WORKER001",
                                farmerId = ramesh?.farmerId,
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
                                farmerId = suresh?.farmerId,
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
                                farmerId = mahesh?.farmerId,
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
                        milkDao.insertRecords(initialSeedRecords)
                    }
                }
            }
        }
    }
}
