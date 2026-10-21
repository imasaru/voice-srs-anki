package com.evan.voicesrs

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** One saved base explanation. Primary key matches the design doc §6. */
@Entity(tableName = "explanation", primaryKeys = ["noteId", "contentHash", "model", "promptVersion"])
data class ExplanationRow(
    val noteId: Long,
    val contentHash: String,
    val model: String,
    val promptVersion: Int,
    val text: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val source: String
)

data class ExplanationPk(
    val noteId: Long,
    val contentHash: String,
    val model: String,
    val promptVersion: Int
)

@Dao
interface ExplanationDao {
    @Query(
        """
        SELECT * FROM explanation
        WHERE noteId = :noteId AND contentHash = :hash AND promptVersion = :promptVersion
        ORDER BY CASE WHEN model = :model THEN 0 ELSE 1 END, lastUsedAt DESC
        LIMIT 1
        """
    )
    fun find(noteId: Long, hash: String, promptVersion: Int, model: String): ExplanationRow?

    @Query(
        """
        SELECT COUNT(*) FROM explanation
        WHERE noteId = :noteId AND contentHash = :hash AND promptVersion = :promptVersion
        """
    )
    fun hits(noteId: Long, hash: String, promptVersion: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(row: ExplanationRow)

    @Query(
        """
        UPDATE explanation SET lastUsedAt = :now
        WHERE noteId = :noteId AND contentHash = :hash AND model = :model AND promptVersion = :promptVersion
        """
    )
    fun touch(noteId: Long, hash: String, model: String, promptVersion: Int, now: Long)

    @Query("SELECT COUNT(*) FROM explanation")
    fun count(): Int

    @Query(
        """
        SELECT noteId, contentHash, model, promptVersion FROM explanation
        ORDER BY lastUsedAt ASC, createdAt ASC
        LIMIT :n
        """
    )
    fun oldest(n: Int): List<ExplanationPk>

    @Query(
        """
        DELETE FROM explanation
        WHERE noteId = :noteId AND contentHash = :hash AND model = :model AND promptVersion = :promptVersion
        """
    )
    fun delete(noteId: Long, hash: String, model: String, promptVersion: Int)

    @Query("DELETE FROM explanation")
    fun clear()
}

@Database(entities = [ExplanationRow::class], version = 1, exportSchema = false)
abstract class AiCacheDatabase : RoomDatabase() {
    abstract fun explanations(): ExplanationDao
}

/** Room cache `ai_cache.db`. Any model is a hit. Trim to [MAX_ROWS] after insert. */
object AiCache {
    const val MAX_ROWS = 5_000
    const val SOURCE_LIVE = "live"
    const val SOURCE_BATCH = "batch"

    @Volatile private var db: AiCacheDatabase? = null

    private fun open(context: Context): AiCacheDatabase {
        val existing = db
        if (existing != null) return existing
        return synchronized(this) {
            val again = db
            if (again != null) again
            else Room.databaseBuilder(
                context.applicationContext,
                AiCacheDatabase::class.java,
                "ai_cache.db"
            ).build().also { db = it }
        }
    }

    /** Preferred model first. Touches [ExplanationRow.lastUsedAt] on a hit. */
    fun lookup(context: Context, noteId: Long, hash: String, promptVersion: Int, model: String): ExplanationRow? {
        val dao = open(context).explanations()
        val row = dao.find(noteId, hash, promptVersion, model) ?: return null
        val now = System.currentTimeMillis()
        dao.touch(row.noteId, row.contentHash, row.model, row.promptVersion, now)
        return row.copy(lastUsedAt = now)
    }

    /** True when any model's row matches. Does not move the LRU clock. */
    fun has(context: Context, noteId: Long, hash: String, promptVersion: Int): Boolean {
        return open(context).explanations().hits(noteId, hash, promptVersion) > 0
    }

    fun put(context: Context, row: ExplanationRow) {
        val database = open(context)
        val dao = database.explanations()
        database.runInTransaction {
            dao.upsert(row)
            val extra = dao.count() - MAX_ROWS
            if (extra > 0) {
                for (old in dao.oldest(extra)) {
                    dao.delete(old.noteId, old.contentHash, old.model, old.promptVersion)
                }
            }
        }
    }

    fun clear(context: Context) {
        open(context).explanations().clear()
    }
}
