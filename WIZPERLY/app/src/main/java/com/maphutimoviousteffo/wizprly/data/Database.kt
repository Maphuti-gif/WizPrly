package com.maphutimoviousteffo.wizprly.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Chat::class, Message::class],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WizPrlyDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: WizPrlyDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN backgroundUri TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN fileUri TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN fileName TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN customContext TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN lastCheckInTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN isCheckInPending INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reaction TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN audioUri TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN audioDuration INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN audioTranscription TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isFromCall INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageType TEXT NOT NULL DEFAULT 'TEXT'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId_timestamp ON messages(chatId, timestamp)")
            }
        }

        fun getInstance(context: Context): WizPrlyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WizPrlyDatabase::class.java,
                    "wizprly_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromString(value: String): List<String> = value.split(",").filter { it.isNotEmpty() }

    @androidx.room.TypeConverter
    fun toString(list: List<String>): String = list.joinToString(",")
}

@androidx.room.Dao
interface ChatDao {
    @androidx.room.Query("SELECT * FROM chats ORDER BY isPinned DESC, lastMessageTime DESC")
    suspend fun getAllChats(): List<Chat>

    @androidx.room.Query("SELECT COUNT(*) FROM chats WHERE isPinned = 1")
    suspend fun getPinnedCount(): Int

    @androidx.room.Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): Chat?

    @androidx.room.Insert
    suspend fun insertChat(chat: Chat)

    @androidx.room.Update
    suspend fun updateChat(chat: Chat)

    @androidx.room.Delete
    suspend fun deleteChat(chat: Chat)
}

@androidx.room.Dao
interface MessageDao {
    @androidx.room.Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChat(chatId: String): List<Message>

    @androidx.room.Insert
    suspend fun insertMessage(message: Message)

    @androidx.room.Update
    suspend fun updateMessage(message: Message)

    @androidx.room.Delete
    suspend fun deleteMessage(message: Message)
}