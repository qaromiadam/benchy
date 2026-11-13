package com.benchy.X.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "benchmark_records")
data class BenchmarkRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val scanType: String, // "QUICK" or "LONG"
    val overallScore: Int,
    val gaming2dRating: Int,
    val gaming3dRating: Int,
    val batteryTempRating: Int,
    val multitaskingRating: Int,
    val cpuSingleScore: Double,
    val cpuMultiScore: Double,
    val ramBandwidthMbPerSec: Double,
    val storageSeqReadMbPerSec: Double,
    val storageSeqWriteMbPerSec: Double,
    val threatsDetected: Int
)

@Entity(tableName = "battery_samples")
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val voltageMv: Int,
    val currentMa: Int,
    val tempCelsius: Float,
    val levelPercent: Int,
    val elapsedDays: Float,
    val isCharging: Boolean = false,
    val chargeCounterUah: Int = 0,
    val powerMw: Float = 0f
)

@Entity(tableName = "allowlisted_apps")
data class AllowlistedAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val allowlistedAtMillis: Long
)

@Entity(
    tableName = "translation_cache",
    primaryKeys = ["targetLang", "sourceText"]
)
data class TranslationCacheEntity(
    val targetLang: String,
    val sourceText: String,
    val translatedText: String,
    val updatedAtMillis: Long
)

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmark_records ORDER BY timestampMillis DESC LIMIT 25")
    fun observeHistory(): Flow<List<BenchmarkRecordEntity>>

    @Query("SELECT * FROM benchmark_records ORDER BY timestampMillis DESC LIMIT 1")
    fun observeLatest(): Flow<BenchmarkRecordEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BenchmarkRecordEntity)
}

@Dao
interface BatterySampleDao {
    @Query("SELECT * FROM battery_samples ORDER BY timestampMillis DESC LIMIT 500")
    fun observeRecentSamples(): Flow<List<BatterySampleEntity>>

    @Query("SELECT * FROM battery_samples ORDER BY timestampMillis ASC")
    suspend fun getAllSamplesOrdered(): List<BatterySampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: BatterySampleEntity)

    @Query("DELETE FROM battery_samples")
    suspend fun clearAll()
}

@Dao
interface AllowlistedAppDao {
    @Query("SELECT * FROM allowlisted_apps ORDER BY allowlistedAtMillis DESC")
    fun observeAllowlisted(): Flow<List<AllowlistedAppEntity>>

    @Query("SELECT packageName FROM allowlisted_apps")
    suspend fun getAllowlistedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAllowlisted(entity: AllowlistedAppEntity)

    @Query("DELETE FROM allowlisted_apps WHERE packageName = :packageName")
    suspend fun removeAllowlisted(packageName: String)
}

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE targetLang = :targetLang AND sourceText = :sourceText LIMIT 1")
    suspend fun getTranslation(targetLang: String, sourceText: String): TranslationCacheEntity?

    @Query("SELECT * FROM translation_cache WHERE targetLang = :targetLang")
    suspend fun getAllForLanguage(targetLang: String): List<TranslationCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TranslationCacheEntity)
}

@Database(
    entities = [
        BenchmarkRecordEntity::class,
        BatterySampleEntity::class,
        AllowlistedAppEntity::class,
        TranslationCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BenchyDatabase : RoomDatabase() {
    abstract fun benchmarkDao(): BenchmarkDao
    abstract fun batterySampleDao(): BatterySampleDao
    abstract fun allowlistedAppDao(): AllowlistedAppDao
    abstract fun translationCacheDao(): TranslationCacheDao

    companion object {
        @Volatile
        private var INSTANCE: BenchyDatabase? = null

        fun getInstance(context: Context): BenchyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BenchyDatabase::class.java,
                    "benchy_database.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
