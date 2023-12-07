package com.example.lyricsify

interface ReceiverCallback {

    fun metadataChanged(song: Song)

    fun playbackStateChanged(isPlaying: Boolean, playbackPos: Int, song: Song)
}