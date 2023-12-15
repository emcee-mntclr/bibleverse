@file:Suppress("DEPRECATION")

package com.example.lyricsify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.preference.PreferenceManager
import android.text.Html
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.example.lyricsify.Utils.SPOTIFY_PACKAGE
import com.example.lyricsify.Utils.openApp
import com.example.lyricsify.databinding.ActivityMainBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder




@Suppress("DEPRECATION", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class MainActivity: AppCompatActivity() , ReceiverCallback,
    SpotifyBroadcastReceiver.ReceiverCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var togglePlayPause: ImageButton
    private lateinit var mediaButtons: LinearLayout
    private lateinit var adSwitch: SwitchMaterial
    private lateinit var spotifyBroadcastReceiver: SpotifyBroadcastReceiver
    private lateinit var audioManager: AudioManager
    private lateinit var songInfoTextView: TextView
    private lateinit var track: TextView
    private val callback = this
    private lateinit var isPlaying: TextView
    private lateinit var lastUpdated: TextView
    private var playbackPosition: Int = 0
    private lateinit var prefs: SharedPreferences
//    private var lyrics: List<String> = emptyList()
//    private var times: List<Int> = emptyList()
    private val times_new = mutableListOf<Double>()
    private var times = mutableListOf<Double>()
    private var lyrics = mutableListOf<String>()

    private lateinit var outputTextLyrics: TextView
     val base_url =
        "https://api.musixmatch.com/ws/1.1/"
    private var q_track: String? = null
    private var q_artist: String? = null
    private var q_album: String? = null
    private var showLyricsThread: Thread? = null
    private lateinit var gSong: Song
    private lateinit var albumArt: ImageView
    private val apiKeyDefault = "a6bea9367054bb36d8b796dcda85ae9a"
    private var apikey = "a6bea9367054bb36d8b796dcda85ae9a"
    var album_art_url: String? = null
    private lateinit var finalUrl: String

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())

        outputTextLyrics = binding.outputTextLyrics
        albumArt = binding.albumartimage
        setSupportActionBar(toolbar)
        adSwitch = findViewById(R.id.adSwitch)
        adSwitch.setOnCheckedChangeListener { _, isChecked -> checkSwitch(isChecked) }
        track = findViewById(R.id.track)
        songInfoTextView = findViewById(R.id.songInfoTextView)
        isPlaying = findViewById(R.id.isPlaying)
        lastUpdated = findViewById(R.id.lastUpdated)
        mediaButtons = findViewById(R.id.mediaButtons)
        togglePlayPause = findViewById(R.id.togglePlayPause)
        mediaButtons.visibility = View.GONE
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        spotifyBroadcastReceiver = SpotifyBroadcastReceiver(this)
        prefs = getPreferences(Context.MODE_PRIVATE)
        finalUrl = "$base_url&apikey=$apikey&q_track=$q_track&q_artist=$q_artist&q_album=$q_album"

        val previousButton: ImageButton = findViewById(R.id.previous)
        val playPauseButton: ImageButton = findViewById(R.id.togglePlayPause)
        val nextButton: ImageButton = findViewById(R.id.next)

        previousButton.setOnClickListener { handleMedia(it) }
        playPauseButton.setOnClickListener { handleMedia(it) }
        nextButton.setOnClickListener { handleMedia(it) }

        if (!prefs.contains("Launched")) {
            showAlertDialog()
            setAPIKey()
        }
    }
    suspend fun showLyrics() {
        Log.d("Inside", "showLyrics()")
        work()
        showLyricsThread = Thread {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt()
                return@Thread
            } else {
                try {
                    for (i in 0 until lyrics.size) {
                        Log.d("Inside last loop", "I is at $i")
                        Thread.sleep((times_new[i].toLong() * 1000))
                        updateUi(lyrics[i].toString())
                        Log.d("Playback position", gSong.playbackPosition.toString())
                        if (i + 1 == lyrics.size) {
                            return@Thread
                        }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    showLyricsThread = null
                    runOnUiThread {
                        val ot = "Lyrics will appear here"
                        outputTextLyrics.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Html.fromHtml(ot, Html.FROM_HTML_MODE_COMPACT)
                        } else {
                            Html.fromHtml(ot)
                        }
                    }
                    return@Thread
                }
            }
        }
        showLyricsThread?.start()
    }


    suspend fun getLyrics(): JSONObject? = withContext(Dispatchers.IO) {
        val finalUrl = "$base_url&apikey=$apikey&q_track=$q_track&q_artist=$q_artist&q_album=$q_album"

        Log.d("Final URL", finalUrl)

        try {
            val client = OkHttpClient.Builder().build()

            val request = Request.Builder()
                .addHeader("apikey", apikey)
                .addHeader("authority", "apic-desktop.musixmatch.com")
                .addHeader("method", "GET")
                .addHeader("scheme", "https")
                .addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .addHeader("accept-language", "en-GB")
                .addHeader("cookie", "_ga=GA1.2.1690725417.1617372359; x-mxm-user-id=mxm%3A394566873cac28d30d2fdfff94965a8e; x-mxm-token-guid=fa9cc7fa-58a3-415e-9a7f-afa7d612520b; mxm-encrypted-token=; AWSELB=55578B011601B1EF8BC274C33F9043CA947F99DCFF8378C231564BC3E68894E08BD389E37D70BDE22DD3BE8B057337BA725B076A5437EFD5DCF9DA7B0658AA87EB7AE701D7; AWSELBCORS=55578B011601B1EF8BC274C33F9043CA947F99DCFF8378C231564BC3E68894E08BD389E37D70BDE22DD3BE8B057337BA725B076A5437EFD5DCF9DA7B0658AA87EB7AE701D7; _gid=GA1.2.398348776.1622458629; _gat=1")
                .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Musixmatch/3.14.4564-master.20200505002 Chrome/78.0.3904.130 Electron/7.1.5 Safari/537.36")
                .url(finalUrl)
                .method("GET", null)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("Response", "Unexpected code ${response.code}")
                return@withContext null
            }
            Log.d("Response", response.toString())
            // Log response body
            val myResponse = response.body?.string()
            Log.d("Response Body", myResponse ?: "Response body is null or empty")
            try {
                Thread.sleep(5000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            // Check for null or empty response body
            if (myResponse.isNullOrBlank()) {
                Log.e("Response", "Empty or null response body")
                return@withContext null
            }
            // Parse JSON
            try {
                val jsonObject = JSONObject(myResponse)
                Log.d("JSON DATA", jsonObject.toString())
                jsonObject
            } catch (e: JSONException) {
                Log.e("JSON Parsing Error", e.message ?: "Unknown error")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseLyrics(jsonObject: JSONObject?) {
        var newJObj: JSONArray? = null

        if (jsonObject == null) {
            return
        }

        try {
            val message = jsonObject.optJSONObject("message")
            val body = message?.optJSONObject("body")
            val macroCalls = body?.optJSONObject("macro_calls")
            val trackSubtitles = macroCalls?.optJSONObject("track.subtitles.get")
            val messageBody = trackSubtitles?.optJSONObject("message")?.optJSONObject("body")
            val subtitleList = messageBody?.optJSONArray("subtitle_list")

            if (subtitleList == null) {
                // Handle the case where "subtitle_list" is not found
                return
            }
            album_art_url = get_album_art_url(jsonObject)
            Log.d("Album art URL", album_art_url!!)
            val subtitleObject = subtitleList.getJSONObject(0)
            val subtitleBody = subtitleObject.getJSONObject("subtitle").getString("subtitle_body")
            newJObj = JSONArray(subtitleBody)

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        if (newJObj == null) {
            // Handle the case where newJObj is null
        } else {
            for (i in 0 until newJObj.length()) {
                try {
                    val oneObject = newJObj.getJSONObject(i)
                    times.add(oneObject.getJSONObject("time").getDouble("total"))
                    lyrics.add(oneObject.getString("text"))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }

        if (album_art_url != null && album_art_url != "" && album_art_url != " ") {
            var b: Bitmap? = null
            b = getBitmapFromURL(album_art_url)
            val col = getDominantColor(b)
            changeNavBarColor(col)
            Log.d("colour", col.toString())
            if (b != null) {
                Blurry.with(applicationContext).from(b).into(albumArt)
            }
        }
    }
    fun getDominantColor(bitmap: Bitmap?): Int {
        if (bitmap == null) {
            return Color.TRANSPARENT
        }

        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var color: Int
        var r = 0
        var g = 0
        var b = 0
        var a: Int
        var count = 0

        for (i in pixels.indices) {
            color = pixels[i]
            a = Color.alpha(color)
            if (a > 0) {
                r += Color.red(color)
                g += Color.green(color)
                b += Color.blue(color)
                count++
            }
        }

        r /= count
        g /= count
        b /= count

        r = ((r shl 16) and 0x00FF0000).toInt()
        g = ((g shl 8) and 0x0000FF00).toInt()
        b = (b and 0x000000FF).toInt()

        color = 0xFF000000.toInt() or r or g or b
        return color
    }
     fun changeNavBarColor(col: Int) {
        window.navigationBarColor = col
        window.statusBarColor = col
    }

    fun get_album_art_url(jobj: JSONObject): String {
        var abUrl: String? = null
        try {
            abUrl = jobj.getJSONObject("message")
                .getJSONObject("body")
                .getJSONObject("macro_calls")
                .getJSONObject("matcher.track.get")
                .getJSONObject("message")
                .getJSONObject("body")
                .getJSONObject("track")
                .getString("album_coverart_800x800")
            Log.d("ab_url 800", " " + abUrl.length)
            if (abUrl == "" || abUrl == " " || abUrl == null || abUrl.isEmpty()) {
                throw JSONException("")
            }
        } catch (e: JSONException) {
            try {
                abUrl = jobj.getJSONObject("message")
                    .getJSONObject("body")
                    .getJSONObject("macro_calls")
                    .getJSONObject("matcher.track.get")
                    .getJSONObject("message")
                    .getJSONObject("body")
                    .getJSONObject("track")
                    .getString("album_coverart_500x500")
                Log.d("ab_url 500", " " + abUrl.length)
                if (abUrl == "" || abUrl == " " || abUrl == null || abUrl.isEmpty()) {
                    throw JSONException("")
                }
            } catch (e1: JSONException) {
                try {
                    abUrl = jobj.getJSONObject("message")
                        .getJSONObject("body")
                        .getJSONObject("macro_calls")
                        .getJSONObject("matcher.track.get")
                        .getJSONObject("message")
                        .getJSONObject("body")
                        .getJSONObject("track")
                        .getString("album_coverart_350x350")
                    Log.d("ab_url 350", " " + abUrl.length)
                    if (abUrl == "" || abUrl == " " || abUrl == null || abUrl.isEmpty()) {
                        throw JSONException("")
                    }
                } catch (e2: JSONException) {
                    try {
                        abUrl = jobj.getJSONObject("message")
                            .getJSONObject("body")
                            .getJSONObject("macro_calls")
                            .getJSONObject("matcher.track.get")
                            .getJSONObject("message")
                            .getJSONObject("body")
                            .getJSONObject("track")
                            .getString("album_coverart_100x100")
                        Log.d("ab_url 100", " " + abUrl.length)
                        if (abUrl == "" || abUrl == " " || abUrl == null || abUrl.isEmpty()) {
                            throw JSONException("")
                        }
                    } catch (e3: JSONException) {
                        // Handle the case where all attempts failed
                    }
                }
            }
            e.printStackTrace()
        }
        return abUrl.orEmpty()
    }


    suspend fun work() {
        try {
            Log.d("Inside", "work()")
            val lyricsJson = getLyrics()
            if (lyricsJson != null) {
                Log.d("Lyrics JSON", lyricsJson.toString())
                parseLyrics(getLyrics())
                Log.d("Parsed Times", times.toString())
                Log.d("Parsed Lyrics", lyrics.toString())
                times_new.clear() // Clear the list before adding new elements
                for (i in times.indices) {
                    if (i == 0) {
                        times_new.add(times[i].toDouble())
                    } else {
                        times_new.add((times[i] - times[i - 1]).toDouble())
                    }
                }
                Log.d("New times list", times_new.toString())
            } else {
                Log.e("work", "Failed to retrieve lyrics JSON")
            }
        } catch (e: Exception) {
            Log.e("work", "An error occurred: ${e.message}")
        }
    }

    fun updateUi(text: String) {
        val formattedText = "<b>$text</b>"

        runOnUiThread {
            val finalText = formattedText
            outputTextLyrics.text = text
            Log.d("updateUi", "Updating UI with text: $finalText")
            outputTextLyrics.text = HtmlCompat.fromHtml(finalText, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    }
    private fun getBitmapFromURL(imageUrl: String?): Bitmap? {
        return try {
            Log.d("imageurl", imageUrl!!)
            val url = URL(imageUrl)
            val connection =
                url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("SetTextI18n")
    override  fun metadataChanged(song: Song) {
        var q_track: String?
        var q_artist: String? = null
        var q_album: String? = null
        var api_key: String? = null
        var trackold: String? = null

        gSong = song
        track.text = "${song.track} (${Utils.getTimeStamp(song.length.toLong())})"
        songInfoTextView.text = "By ${song.artist}\nFrom ${song.album}"
        lastUpdated.text = "(info updated @${Utils.getTimeStampFromDate(song.timeSent)})"
        Log.d("Playback", song.playbackPosition.toString())
        trackold = song.track
        q_track = song.track
        q_artist = song.artist
        q_album = song.album

        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        api_key = sp.getString("API_key", apiKeyDefault)
        song.playbackPosition = 0

        try {
            q_track = URLEncoder.encode(q_track, Charsets.UTF_8.name())
            q_artist = URLEncoder.encode(q_artist, Charsets.UTF_8.name())
            q_album = URLEncoder.encode(q_album, Charsets.UTF_8.name())
        } catch (e: Exception) {
            Log.d("UnsupportedEncoding", "Failed to encode")
        }

        q_track = "q_track=$q_track"
        q_artist = "q_artist=$q_artist"
        q_album = "q_album=$q_album"
        api_key = "usertoken=$api_key"

        Log.d("q_track", q_track)
        Log.d("q_artist", q_artist)
        Log.d("q_album", q_album)
        Log.i("New Song", song.track)

        val lyricsJson = runBlocking {
            getLyrics()
        }

        if (lyricsJson != null) {
            parseLyrics(lyricsJson)
            val lyricsText = lyrics.joinToString("\n")
            updateUi(lyricsText)
        } else {
            Log.e("Lyrics", "Failed to retrieve lyrics JSON")
        }
    }

    override fun playbackStateChanged(playState: Boolean, playbackPos: Int, song: Song) {
        playbackPosition = playbackPos
        updatePlayPauseButton(playState)
        if (playState) {
            isPlaying.setText(R.string.play_state_text)
        } else {
            isPlaying.setText(R.string.last_detected_song)
        }
        handleNewSongIntent(song)
    }

    private fun handleNewSongIntent(song: Song) {}

    private fun calculateTime(songFromIntent: Song): Long {
        return songFromIntent.timeRemaining()
    }


    private fun mute() {
        if (getMusicVolume() != 0) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                0
            )
        }
    }

    private fun unmute() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_UNMUTE,
            0
        )
    }

    private fun checkSwitch(isChecked: Boolean) {

        if (isChecked) {
            if (isSpotifyInstalled()) {
                startService()
            } else {
                adSwitch.isChecked = !adSwitch.isChecked
                Toast.makeText(this, "Spotify is not installed", Toast.LENGTH_LONG).show()
            }
            mediaButtons.visibility = View.VISIBLE
        } else {
            stopService()
            mediaButtons.visibility = View.GONE
        }
    }


    private fun startService() {
        // Check if the com.example.lyricsify.SpotifyBroadcastReceiver is not null
        if (spotifyBroadcastReceiver != null) {
            val filter = IntentFilter()

            // Check if BroadcastTypes constants are not null and add actions
            if (SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED != null) {
                filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED)
            }

            if (SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED != null) {
                filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED)
            }

            // Check if the ReceiverCallback is not null
            if (callback != null) {
                ContextCompat.registerReceiver(
                    application,
                    spotifyBroadcastReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED)

            } else {

            }
        } else {

        }
    }

    private fun stopService() {
        try {
            unregisterReceiver(spotifyBroadcastReceiver)
        } catch (e: Exception) {
            Log.wtf("error while stopping", e.message)
        }
        songInfoTextView.text = ""
        isPlaying.text = ""
        track.text = ""
        lastUpdated.text = ""
    }

    private fun getMusicVolume(): Int {
        return this.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun handleOpenSpotify(view: View?) {
        openSpotify()
    }

    private fun openSpotify() {
        if (isSpotifyInstalled()) {
            openApp(applicationContext, SPOTIFY_PACKAGE)
        } else {
            Toast.makeText(this, "Could not find Spotify!", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onPause() {
        super.onPause()
        unregisterReceiver(spotifyBroadcastReceiver)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            showLyrics()
        }
        updatePlayPauseButton(audioManager.isMusicActive)
        val filter = IntentFilter().apply {}
        val flags = Context.RECEIVER_VISIBLE_TO_INSTANT_APPS
        registerReceiver(spotifyBroadcastReceiver, filter, flags.toString(), null)
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED)
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED)
    }
    fun closeSwitch() {
        adSwitch.isChecked = false
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        moveTaskToBack(true)
    }
    fun handleMuteUnmute(view: View) {
        val id = view.id
        if (id == R.id.unMute) {
            unmute()
        } else if (id == R.id.mute) {
            mute()
        }
    }

    fun handleMedia(view: View) {
        val id = view.id
        lifecycleScope.launch {
            if (id == R.id.previous) {
                if (audioManager.isMusicActive) {
                    val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    audioManager.dispatchMediaKeyEvent(event)
                }
            } else if (id == R.id.togglePlayPause) { // get music playing info
                val isMusicActive = audioManager.isMusicActive
                if (isMusicActive) {
                    val pause = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                    showLyricsThread?.interrupt()
                    audioManager.dispatchMediaKeyEvent(pause)
                    updatePlayPauseButton(false)
                } else {
                    val play = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)

                    // Use clear on mutable lists
                    times.clear()
                    lyrics.clear()
                    times_new.clear()

                    showLyrics()
                    audioManager.dispatchMediaKeyEvent(play)
                    updatePlayPauseButton(true)
                }
            }
        }
    }

    private fun updatePlayPauseButton(isMusicActive: Boolean) {
        if (isMusicActive) {
            togglePlayPause.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_baseline_pause
                )
            )
        } else{
        togglePlayPause.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_baseline_play_arrow
                )
            )
        }
    }

    private fun showAlertDialog() {
        val titleView = TextView(this)
        titleView.text = getString(R.string.dialog_title)
        titleView.textSize = 20.0f
        titleView.setPadding(15, 20, 15, 20)
        titleView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        val messageView = TextView(this)
        messageView.text = getString(R.string.dialog_message)
        messageView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        messageView.textSize = 16.0f
        val builder = AlertDialog.Builder(this)
        builder.setCustomTitle(titleView)
        builder.setView(messageView)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.dialog_positive_btn) { dialog, which -> }
        builder.setNegativeButton(R.string.dialog_negative_btn) { dialog, which -> }
        val dialog = builder.create()
        dialog.show()
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        positive.setOnClickListener { v: View? ->
            if (isSpotifyInstalled()) {
                val spotifySettings =
                    Intent("android.intent.action.APPLICATION_PREFERENCES").setPackage(
                        SPOTIFY_PACKAGE
                    )
                startActivity(spotifySettings)
                Toast.makeText(
                    this,
                    "Scroll down and Enable Device Broadcast Status…",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Couldn't find Spotify installed!", Toast.LENGTH_SHORT).show()
            }
        }
        negative.setOnClickListener { v: View? -> dialog.dismiss() }
        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sp.edit().putBoolean("Launched", true).apply()
    }

    private fun setAPIKey() {
        val alert = AlertDialog.Builder(this)
        val edittext = EditText(this)
        alert.setView(edittext)
        alert.setTitle("Enter your API key.")
        alert.setMessage("Please enter your API key below. If you don't have one, skip this section")
        alert.setPositiveButton(
            "Done"
        ) { dialog, whichButton ->
            val api_key = edittext.text.toString()
            val sp = PreferenceManager.getDefaultSharedPreferences(
                applicationContext
            )
            sp.edit().putString("API_key", api_key).apply()
        }
        alert.setNegativeButton(
            "I don't have a key"
        ) { dialog, whichButton ->
            //TODO Show an alert with steps to get an API key
        }
        alert.show()
    }

    private fun isSpotifyInstalled(): Boolean {
        val packageManager = this.packageManager
        val intent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE) ?: return false
        val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return !list.isEmpty()
    }


    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(spotifyBroadcastReceiver)
    }
}

private fun OkHttpClient.setFollowRedirects(b: Boolean) {

}
