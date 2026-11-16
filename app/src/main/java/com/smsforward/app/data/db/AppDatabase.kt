package com.smsforward.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.ProcessedSmsEntity
import com.smsforward.app.data.entity.RuleEntity
import com.smsforward.app.data.entity.SmsEntity

@Database(
    entities = [
        SmsEntity::class,
        RuleEntity::class,
        ChannelEntity::class,
        ProcessedSmsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao

    abstract fun ruleDao(): RuleDao

    abstract fun channelDao(): ChannelDao

    abstract fun processedDao(): ProcessedDao

    companion object {
        private const val NAME = "sms_forward.db"

        /** v2：新增已处理短信台账，保证清空记录后重新导入不会重复推送。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `processed_sms` (" +
                        "`fingerprint` TEXT NOT NULL, " +
                        "`processedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`fingerprint`))",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                // 仅在未来漏写迁移时兜底；已声明的迁移优先
                .fallbackToDestructiveMigration()
                .build()
    }
}
