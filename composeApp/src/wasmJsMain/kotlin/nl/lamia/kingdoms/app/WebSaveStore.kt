package nl.lamia.kingdoms.app

import kotlinx.io.IOException

/**
 * The browser's save slot: one `localStorage` entry per [key], kept per site and browser (Design §7).
 * A browser that blocks storage (some private modes) reads as no save; a failed write, such as a full
 * quota, is an [IOException], so it only loses the save.
 */
class WebSaveStore(private val key: String) : SaveStore {
    override fun read(): String? = try {
        storageGet(key)
    } catch (e: Throwable) {
        null
    }

    override fun write(text: String) = try {
        storageSet(key, text)
    } catch (e: Throwable) {
        throw IOException(e.message)
    }

    override fun clear() = try {
        storageRemove(key)
    } catch (e: Throwable) {
        throw IOException(e.message)
    }
}

private fun storageGet(key: String): String? = js("window.localStorage.getItem(key)")

private fun storageSet(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")

private fun storageRemove(key: String): Unit = js("window.localStorage.removeItem(key)")
