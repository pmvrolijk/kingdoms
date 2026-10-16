package nl.lamia.kingdoms.app

import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.Save

/** What the app keeps between launches (Design §7): the game in play, and its setup and restarts for Restart. */
@Serializable
data class SavedGame(val setup: Setup, val restarts: Int, val state: GameState)

/** The one save slot. */
interface SaveStore {
    fun read(): String?

    fun write(text: String)

    fun clear()

    /** Keeps nothing: tests and previews. */
    object None : SaveStore {
        override fun read(): String? = null

        override fun write(text: String) {}

        override fun clear() {}
    }
}

/** The saved sound settings, or the defaults when there are none or they cannot be read. */
fun SaveStore.loadSettings(): SoundSettings = try {
    read()?.let { Save.json.decodeFromString(SoundSettings.serializer(), it) } ?: SoundSettings()
} catch (e: IllegalArgumentException) {
    SoundSettings()
} catch (e: IOException) {
    SoundSettings()
}

/** Saves the sound settings; a failed write only loses them. */
fun SaveStore.saveSettings(settings: SoundSettings) {
    try {
        write(Save.json.encodeToString(SoundSettings.serializer(), settings))
    } catch (e: IOException) {
        println("Kingdoms: could not save settings: ${e.message}")
    }
}

/** The saved game, or null when there is none or it cannot be read (a save from an incompatible version). */
fun SaveStore.load(): SavedGame? = try {
    read()?.let { Save.json.decodeFromString(SavedGame.serializer(), it) }
} catch (e: IllegalArgumentException) { // includes SerializationException and MapConfig's checks
    null
} catch (e: IOException) {
    null
}

/** Saves [game]. A failed write must not stop play, so it only loses the save. */
fun SaveStore.save(game: SavedGame) {
    try {
        write(Save.json.encodeToString(SavedGame.serializer(), game))
    } catch (e: IOException) {
        println("Kingdoms: could not save: ${e.message}")
    }
}

/**
 * The file [name] in [directory], created when needed: `save.json` for the game, `settings.json` for
 * the sound settings. Writes go to a temporary file first, so a crash mid-write keeps the old one.
 */
class FileSaveStore(directory: String, name: String = "save.json") : SaveStore {
    private val dir = Path(directory)
    private val file = Path(dir, name)
    private val temp = Path(dir, "$name.tmp")

    override fun read(): String? =
        if (SystemFileSystem.exists(file)) SystemFileSystem.source(file).buffered().use { it.readString() } else null

    override fun write(text: String) {
        SystemFileSystem.createDirectories(dir)
        SystemFileSystem.sink(temp).buffered().use { it.writeString(text) }
        SystemFileSystem.atomicMove(temp, file)
    }

    override fun clear() = SystemFileSystem.delete(file, mustExist = false)
}
