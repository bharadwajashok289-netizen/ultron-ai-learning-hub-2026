package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class CachedStudyItemEntity(
    val itemKey: String,
    val category: String, // "CHAT", "FLASHCARD", "QUIZ", "TASK", "HOMEWORK", "FOCUS", "NOTEBOOK"
    val title: String,
    val subtitle: String,
    val contentBody: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

interface CachedStudyDao {
    fun observeAllCachedItems(): Flow<List<CachedStudyItemEntity>>

    fun observeCachedByCategory(category: String): Flow<List<CachedStudyItemEntity>>

    suspend fun upsertItem(item: CachedStudyItemEntity)

    suspend fun upsertAll(items: List<CachedStudyItemEntity>)

    suspend fun deleteByKey(key: String)
}

class UltronLocalDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "ultron_offline_cache.db", null, 1) {

    private val invalidationVersion = MutableStateFlow(0L)

    private val daoImpl = object : CachedStudyDao {
        override fun observeAllCachedItems(): Flow<List<CachedStudyItemEntity>> {
            return invalidationVersion.map {
                withContext(Dispatchers.IO) {
                    queryItems(selection = null, selectionArgs = null)
                }
            }
        }

        override fun observeCachedByCategory(category: String): Flow<List<CachedStudyItemEntity>> {
            return invalidationVersion.map {
                withContext(Dispatchers.IO) {
                    queryItems(selection = "category = ?", selectionArgs = arrayOf(category))
                }
            }
        }

        override suspend fun upsertItem(item: CachedStudyItemEntity) {
            withContext(Dispatchers.IO) {
                synchronized(this@UltronLocalDatabase) {
                    writableDatabase.insertWithOnConflict(
                        TABLE_NAME,
                        null,
                        item.toContentValues(),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                invalidationVersion.value = System.nanoTime()
            }
        }

        override suspend fun upsertAll(items: List<CachedStudyItemEntity>) {
            if (items.isEmpty()) return
            withContext(Dispatchers.IO) {
                synchronized(this@UltronLocalDatabase) {
                    val db = writableDatabase
                    db.beginTransaction()
                    try {
                        for (item in items) {
                            db.insertWithOnConflict(
                                TABLE_NAME,
                                null,
                                item.toContentValues(),
                                SQLiteDatabase.CONFLICT_REPLACE
                            )
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
                invalidationVersion.value = System.nanoTime()
            }
        }

        override suspend fun deleteByKey(key: String) {
            withContext(Dispatchers.IO) {
                synchronized(this@UltronLocalDatabase) {
                    writableDatabase.delete(TABLE_NAME, "itemKey = ?", arrayOf(key))
                }
                invalidationVersion.value = System.nanoTime()
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                itemKey TEXT PRIMARY KEY NOT NULL,
                category TEXT NOT NULL,
                title TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                contentBody TEXT NOT NULL,
                updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun cachedStudyDao(): CachedStudyDao = daoImpl

    private fun queryItems(
        selection: String?,
        selectionArgs: Array<String>?
    ): List<CachedStudyItemEntity> {
        synchronized(this) {
            val result = mutableListOf<CachedStudyItemEntity>()
            readableDatabase.query(
                TABLE_NAME,
                arrayOf("itemKey", "category", "title", "subtitle", "contentBody", "updatedAtMs"),
                selection,
                selectionArgs,
                null,
                null,
                "updatedAtMs DESC"
            ).use { cursor ->
                val keyIdx = cursor.getColumnIndexOrThrow("itemKey")
                val catIdx = cursor.getColumnIndexOrThrow("category")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                val subIdx = cursor.getColumnIndexOrThrow("subtitle")
                val bodyIdx = cursor.getColumnIndexOrThrow("contentBody")
                val updatedIdx = cursor.getColumnIndexOrThrow("updatedAtMs")
                while (cursor.moveToNext()) {
                    result.add(
                        CachedStudyItemEntity(
                            itemKey = cursor.getString(keyIdx),
                            category = cursor.getString(catIdx),
                            title = cursor.getString(titleIdx),
                            subtitle = cursor.getString(subIdx),
                            contentBody = cursor.getString(bodyIdx),
                            updatedAtMs = cursor.getLong(updatedIdx)
                        )
                    )
                }
            }
            return result
        }
    }

    private fun CachedStudyItemEntity.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("itemKey", itemKey)
            put("category", category)
            put("title", title)
            put("subtitle", subtitle)
            put("contentBody", contentBody)
            put("updatedAtMs", updatedAtMs)
        }
    }

    companion object {
        private const val TABLE_NAME = "cached_study_items"

        @Volatile
        private var INSTANCE: UltronLocalDatabase? = null

        fun getInstance(context: Context): UltronLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UltronLocalDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

object NetworkConnectivityObserver {
    fun observeIsOnline(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun checkCurrent(): Boolean {
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        trySend(checkCurrent())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(checkCurrent())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            trySend(true)
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()
}
