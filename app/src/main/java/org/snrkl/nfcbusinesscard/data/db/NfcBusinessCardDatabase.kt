package org.snrkl.nfcbusinesscard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShareableItemEntity::class,
        PhoneNumberEntity::class,
        EmailAddressEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class NfcBusinessCardDatabase : RoomDatabase() {

    abstract fun shareableItemDao(): ShareableItemDao

    companion object {
        private const val DATABASE_NAME = "nfc_business_card.db"

        /**
         * Adds the display-only portrait column.
         *
         * A real migration rather than `fallbackToDestructiveMigration()`: by
         * the time this shipped the operator already had their own items on the
         * device, and silently wiping someone's contact library to add a column
         * is not an acceptable upgrade path.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shareable_items ADD COLUMN vcard_photo_uri TEXT")
            }
        }

        @Volatile
        private var instance: NfcBusinessCardDatabase? = null

        fun get(context: Context): NfcBusinessCardDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): NfcBusinessCardDatabase =
            Room.databaseBuilder(context, NfcBusinessCardDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Room declares the foreign keys but SQLite does not
                        // enforce them unless asked, per connection. Without
                        // this, deleting an item leaves its phone numbers behind
                        // as orphans that no query will ever reach.
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}
