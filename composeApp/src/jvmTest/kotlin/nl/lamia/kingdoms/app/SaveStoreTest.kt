package nl.lamia.kingdoms.app

import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.rules.NewGame
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Keeps the save in memory, for tests. */
class MemorySaveStore(var text: String? = null) : SaveStore {
    override fun read() = text

    override fun write(text: String) {
        this.text = text
    }

    override fun clear() {
        text = null
    }
}

class SaveStoreTest {
    private val setup = Setup(seed = 5)
    private val game = SavedGame(setup, restarts = 2, NewGame.create(MapGenerator.generate(setup.config), setup.seats, seed = 7))

    @Test
    fun aFileSaveRoundTripsAndOverwrites() {
        val dir = Files.createTempDirectory("kingdoms").resolve("not yet there")
        val store = FileSaveStore(dir.toString())
        assertNull(store.load())
        store.save(game.copy(restarts = 1))
        store.save(game)
        assertEquals(game, FileSaveStore(dir.toString()).load())
        assertEquals(listOf("save.json"), dir.toFile().list()!!.toList(), "no temporary file left behind")
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun anUnreadableSaveIsNoSave() {
        assertNull(MemorySaveStore("not json").load())
        assertNull(MemorySaveStore("""{"setup":{},"restarts":0}""").load())
        // Well-formed but invalid: MapConfig refuses a map too small to play.
        val text = MemorySaveStore().apply { save(game) }.text!!
        val tooSmall = text.replace("\"landProvinces\":28", "\"landProvinces\":2")
        assertNotEquals(text, tooSmall)
        assertNull(MemorySaveStore(tooSmall).load())
    }
}
