package com.example.lyricsify

class Song(
    val id: String,
    val artist: String,
    val album: String,
    val track: String,
    val length: Int,
    internal var playbackPosition: Int,
    private var playing: Boolean,
    val timeSent: Long,
    private val registeredTime: Long
) {
    // Additional constructors if needed
    constructor(
        id: String,
        artist: String,
        album: String,
        track: String,
        length: Int,
        timeSent: Long,
        registeredTime: Long,
        playbackPosition: Int
    ) : this(id, artist, album, track, length, playbackPosition, false, timeSent, registeredTime)

    constructor(
        id: String,
        artist: String,
        album: String,
        track: String,
        length: Int,
        timeSent: Long,
        playbackPosition: Int
    ) : this(id, artist, album, track, length, playbackPosition, false, timeSent, System.currentTimeMillis())

    fun timeRemaining(): Long {
        return (length - playbackPosition).toLong()
    }

    fun getTimeFinish(): Long {
        return timeSent + timeRemaining()
    }

    fun getElapsedTime(): Long {
        return System.currentTimeMillis() - timeSent
    }

    fun getPlaybackPosition(): Int {
        return playbackPosition
    }

    fun setPlaying(playing: Boolean) {
        this.playing = playing
    }

    fun isPlaying(): Boolean {
        return playing
    }
}