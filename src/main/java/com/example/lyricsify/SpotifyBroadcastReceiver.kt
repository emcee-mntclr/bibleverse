package com.example.lyricsify
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SpotifyBroadcastReceiver(private val callback: ReceiverCallback) : BroadcastReceiver() {

    interface ReceiverCallback {
        fun metadataChanged(song: Song)
        fun playbackStateChanged(playState: Boolean, playbackPos: Int, song: Song)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            when (intent.action) {
                BroadcastTypes.PLAYBACK_STATE_CHANGED -> {
                    val playState = intent.getBooleanExtra("playbackState", false)
                    val playbackPos = intent.getIntExtra("playbackPosition", 0)
                    val song = extractSongFromIntent(intent)
                    callback.playbackStateChanged(playState, playbackPos, song)
                }
                BroadcastTypes.METADATA_CHANGED -> {
                    val song = extractSongFromIntent(intent)
                    callback.metadataChanged(song)
                }
                // Add other cases if needed
            }
        }
    }

    private fun extractSongFromIntent(intent: Intent): Song {
        // Extract relevant information from the intent and create a Song object
        val id = intent.getStringExtra("id") ?: ""
        val track = intent.getStringExtra("track") ?: ""
        val artist = intent.getStringExtra("artist") ?: ""
        val album = intent.getStringExtra("album") ?: ""
        val length = intent.getIntExtra("length", 0)
        val playbackPosition = intent.getIntExtra("playbackPosition", 0)
        val timeSent = intent.getLongExtra("timeSent", 0)
        val registeredTime = intent.getLongExtra("registeredTime", 0)
        val playing = intent.getBooleanExtra("playing", false)

        return Song(id, artist, album, track, length, playbackPosition, playing, timeSent, registeredTime)
    }

    object BroadcastTypes {
        const val PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val METADATA_CHANGED = "com.spotify.music.metadatachanged"
        // Add other broadcast types if needed
    }
}