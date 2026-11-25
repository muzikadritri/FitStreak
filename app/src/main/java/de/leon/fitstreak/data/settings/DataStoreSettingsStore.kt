package de.leon.fitstreak.data.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal val SettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(input: InputStream): Settings = try {
        SettingsJson.decodeFromString(Settings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Einstellungen nicht lesbar", e)
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(SettingsJson.encodeToString(Settings.serializer(), t).encodeToByteArray())
    }
}

/** Einstellungen als typisierter DataStore (JSON-Datei im App-Speicher). */
class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val appContext = context.applicationContext

    private val store: DataStore<Settings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { Settings() },
        produceFile = { File(appContext.filesDir, "datastore/settings.json") },
    )

    override val data: Flow<Settings> = store.data

    override suspend fun update(transform: (Settings) -> Settings): Settings = store.updateData { transform(it) }
}
