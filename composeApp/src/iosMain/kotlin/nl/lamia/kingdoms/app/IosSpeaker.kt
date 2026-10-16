package nl.lamia.kingdoms.app

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * iOS sound through `AVAudioPlayer`, two players per sound so a quick repeat overlaps. The session is
 * Ambient: the ring/silent switch mutes the game, and music the player has on keeps playing.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSpeaker : Speaker {
    private val players: Map<Pair<Sound, Int>, List<AVAudioPlayer>>

    init {
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
        AVAudioSession.sharedInstance().setActive(true, null)
        players = Sound.entries.flatMap { s -> List(s.variants) { s to it } }.associateWith { (s, v) ->
            val data = Synth.wav(Synth.render(s, v)).toNSData()
            List(2) { AVAudioPlayer(data = data, error = null).apply { prepareToPlay() } }
        }
    }

    override fun play(sound: Sound, variant: Int, volume: Float) {
        val pool = players[sound to variant] ?: return
        val player = pool.firstOrNull { !it.playing } ?: pool.first().apply { stop() }
        player.currentTime = 0.0
        player.volume = volume
        player.play()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
