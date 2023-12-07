@file:Suppress("DEPRECATION")

package com.example.lyricsify

import SpotifyBroadcastReceiver
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
import androidx.lifecycle.lifecycleScope
import com.example.lyricsify.Utils.SPOTIFY_PACKAGE
import com.example.lyricsify.Utils.openApp
import com.example.lyricsify.databinding.ActivityMainBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine




@Suppress("DEPRECATION", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNUSED_EXPRESSION")
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
    private val times = ArrayList<Double>()
    private val lyrics = ArrayList<String>()
    private val timesNew = ArrayList<Double>()
    private lateinit var outputTextLyrics: TextView
    private val baseUrl =
        "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?format=json&namespace=lyrics_synched&part=lyrics_crowd%2Cuser%2Clyrics_verified_by&tags=nowplaying&user_language=en&f_subtitle_length_max_deviation=1&subtitle_format=mxm&app_id=web-desktop-app-v1.0"
    private var qTrack: String? = null
    private var qArtist: String? = null
    private var qAlbum: String? = null
    private var showLyricsThread: Thread? = null
    private lateinit var gSong: Song
    private lateinit var albumArt: ImageView
    private val apiKeyDefault = "21062742510293ae06df230e7cd334d26ae4cf17646b37514af666"
    private var apiKey: String? = null
    private val times_new = mutableListOf<Double>()
    var album_art_url: String? = null

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
        togglePlayPause = findViewById(R.id.togglePlayPause)
        mediaButtons = findViewById(R.id.mediaButtons)
        mediaButtons.visibility = View.GONE
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        spotifyBroadcastReceiver = SpotifyBroadcastReceiver(this)
        prefs = getPreferences(Context.MODE_PRIVATE)

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

        coroutineScope {
            try {
                val lyricsJson = getLyrics()

                Log.d("Lyrics JSON", lyricsJson.toString())

                val job = async { parseLyrics(lyricsJson) }
                // Wait for the job to complete
                job.await()

                Log.d("Parsed Times", times.toString())
                Log.d("Parsed Lyrics", lyrics.toString())

                for (i in times.indices) {
                    if (i == 0) {
                        times_new.add(times[i])
                    } else {
                        times_new.add(times[i] - times[i - 1])
                    }
                }

                Log.d("New times list", times_new.toString())

                // Call updateUi with the lyrics text
                updateUi(lyrics.joinToString("\n"))

            } catch (e: Exception) {
                Log.e("showLyrics", "An error occurred: ${e.message}")
                // Handle the error, show a message, etc.
            }
        }
    }


    suspend fun getLyrics(): JSONObject? = withContext(Dispatchers.IO) {
        return@withContext suspendCoroutine { continuation ->
            val finalUrl = "$baseUrl&$apiKey&$qTrack&$qArtist&$qAlbum"

            val client = OkHttpClient.Builder().build()
            val request = Request.Builder()
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

            val call = client.newCall(request)

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(IOException("Unexpected code ${response.code}"))
                    } else {
                        try {
                            Thread.sleep(5000)
                            val myResponse = response.body?.string()
                            val jsonObject = JSONObject(myResponse)
                            continuation.resume(jsonObject)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }


    fun parseLyrics(jsonObject: JSONObject?) {
        if (jsonObject == null) {
            Log.e("Lyrics response", "Null JSON response")
            return
        }
        val statusCode = jsonObject.optJSONObject("message")
            ?.optJSONObject("header")
            ?.optInt("status_code", -1)

        if (statusCode == 404) {
            // Handle 404 response (lyrics not found)
            Log.e("Lyrics Response", "Lyrics not found (404)")
            return
        }

        try {
            if (jsonObject.has("message") && !jsonObject.isNull("message")) {
                val messageObject = jsonObject.getJSONObject("message")

                if (messageObject.has("body") && !messageObject.isNull("body")) {
                    val bodyObject = messageObject.getJSONObject("body")

                    if (bodyObject.has("macro_calls") && !bodyObject.isNull("macro_calls")) {
                        val macroCallsObject = bodyObject.getJSONObject("macro_calls")

                        if (macroCallsObject.has("track.subtitles.get") && !macroCallsObject.isNull("track.subtitles.get")) {
                            val trackSubtitlesObject = macroCallsObject.getJSONObject("track.subtitles.get")

                            if (trackSubtitlesObject.has("message") && !trackSubtitlesObject.isNull("message")) {
                                val messageObjectInSubtitles = trackSubtitlesObject.getJSONObject("message")

                                if (messageObjectInSubtitles.has("body") && !messageObjectInSubtitles.isNull("body")) {
                                    val bodyObjectInSubtitles = messageObjectInSubtitles.getJSONObject("body")

                                    if (bodyObjectInSubtitles.has("subtitle_list") && !bodyObjectInSubtitles.isNull("subtitle_list")) {
                                        val subtitleListArray = bodyObjectInSubtitles.getJSONArray("subtitle_list")



                                        album_art_url = get_album_art_url(jsonObject)
                                        Log.d("Album art URL", album_art_url!!)

                                        if (album_art_url != null && album_art_url!!.isNotBlank()) {
                                            val b: Bitmap? = getBitmapFromURL(album_art_url)
                                            val col = getDominantColor(b)
                                            changeNavBarColour(col)
                                            Log.d("colour", col.toString())
                                            if (b != null) {
                                                Blurry.with(applicationContext).from(b).into(albumArt)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    suspend fun work() {
        try {
            Log.d("Inside", "work()")
            val lyricsJson = getLyrics()
            if (lyricsJson != null) {
                Log.d("Lyrics JSON", lyricsJson.toString())
                parseLyrics(lyricsJson)
                Log.d("Parsed Times", times.toString())
                Log.d("Parsed Lyrics", lyrics.toString())
                times_new.clear() // Clear the list before adding new elements
                for (i in times.indices) {
                    if (i == 0) {
                        times_new.add(times[i])
                    } else {
                        times_new.add(times[i] - times[i - 1])
                    }
                }
                Log.d("New times list", times_new.toString())

                // Call updateUi with the lyrics text
                updateUi(lyrics.joinToString("\n"))
            } else {
                Log.e("work", "Failed to retrieve lyrics JSON")
                // Handle the case where lyricsJson is null
            }
        } catch (e: Exception) {
            Log.e("work", "An error occurred: ${e.message}")
        }
    }

    fun updateUi(text: String) {
        val formattedText = "<b>$text</b>"

        runOnUiThread {
            val finalText = formattedText
            outputTextLyrics.text = Html.fromHtml(finalText, Html.FROM_HTML_MODE_COMPACT)
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
        var qtrack: String?
        var qartist: String? = null
        var qalbum: String? = null
        var api_key: String? = null
        var trackold: String? = null

        gSong = song
        track.text = "${song.track} (${Utils.getTimeStamp(song.length.toLong())})"
        songInfoTextView.text = "By ${song.artist}\nFrom ${song.album}"
        lastUpdated.text = "(info updated @${Utils.getTimeStampFromDate(song.timeSent)})"
        Log.d("Playback", song.playbackPosition.toString())
        trackold = song.track
        qtrack = song.track
        qartist = song.artist
        qalbum = song.album

        val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        api_key = sp.getString("API_key", apiKeyDefault)
        song.playbackPosition = 0

        try {
            qtrack = URLEncoder.encode(qtrack, Charsets.UTF_8.name())
            qartist = URLEncoder.encode(qartist, Charsets.UTF_8.name())
            qalbum = URLEncoder.encode(qalbum, Charsets.UTF_8.name())
        } catch (e: Exception) {
            Log.d("UnsupportedEncoding", "Failed to encode")
        }

        qtrack = "q_track=$qtrack"
        qartist = "q_artist=$qartist"
        qalbum = "q_album=$qalbum"

        Log.d("q_track", qtrack)
        Log.d("q_artist", qartist)
        Log.d("q_album", qalbum)
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
        // Check if the SpotifyBroadcastReceiver is not null
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

    private fun changeNavBarColour(col: Int) {
        window.navigationBarColor = col
        window.statusBarColor = col
    }
    override fun onPause() {
        super.onPause()
        unregisterReceiver(spotifyBroadcastReceiver)
    }

    override fun onResume() {
        super.onResume()
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
                    times.clear()
                    lyrics.clear()
                    times_new.clear()
                    showLyrics()
                    audioManager.dispatchMediaKeyEvent(play)
                    updatePlayPauseButton(true)
                }
            } else if (id == R.id.next) {
                if (audioManager.isMusicActive) {
                    val next = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                    audioManager.dispatchMediaKeyEvent(next)
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
            //Toast.makeText(getApplicationContext(), "Lyrics won't work without an API key", Toast.LENGTH_LONG).show();
        }
        alert.show()
    }

    private fun isSpotifyInstalled(): Boolean {
        val packageManager = this.packageManager
        val intent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE) ?: return false
        val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return !list.isEmpty()
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
        r = r shl 16 and 0x00FF0000
        g = g shl 8 and 0x0000FF00
        b = b and 0x000000FF
        color = -0x1000000 or r or g or b
        return color
    }

    fun get_album_art_url(jobj: JSONObject): String? {
        var ab_url: String? = null
        try {
            ab_url =
                jobj.getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls")
                    .getJSONObject("matcher.track.get").getJSONObject("message")
                    .getJSONObject("body").getJSONObject("track")
                    .getString("album_coverart_800x800")
            Log.d("ab_url 800", " " + ab_url.length)
            if (ab_url === "" || ab_url === " " || ab_url == null || ab_url.length == 0) {
                throw JSONException("")
            }
        } catch (e: JSONException) {
            try {
                ab_url =
                    jobj.getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls")
                        .getJSONObject("matcher.track.get").getJSONObject("message")
                        .getJSONObject("body").getJSONObject("track")
                        .getString("album_coverart_500x500")
                Log.d("ab_url 500", " " + ab_url.length)
                if (ab_url === "" || ab_url === " " || ab_url == null || ab_url.length == 0) {
                    throw JSONException("")
                }
            } catch (e1: JSONException) {
                try {
                    ab_url = jobj.getJSONObject("message").getJSONObject("body")
                        .getJSONObject("macro_calls").getJSONObject("matcher.track.get")
                        .getJSONObject("message").getJSONObject("body").getJSONObject("track")
                        .getString("album_coverart_350x350")
                    Log.d("ab_url 350", " " + ab_url.length)
                    if (ab_url === "" || ab_url === " " || ab_url == null || ab_url.length == 0) {
                        throw JSONException("")
                    }
                } catch (e2: JSONException) {
                    try {
                        ab_url = jobj.getJSONObject("message").getJSONObject("body")
                            .getJSONObject("macro_calls").getJSONObject("matcher.track.get")
                            .getJSONObject("message").getJSONObject("body").getJSONObject("track")
                            .getString("album_coverart_100x100")
                        Log.d("ab_url 100", " " + ab_url.length)
                        if (ab_url === "" || ab_url === " " || ab_url == null || ab_url.length == 0) {
                            throw JSONException("")
                        }
                    } catch (e3: JSONException) {
                    }
                }
            }
            e.printStackTrace()
        }
        return ab_url
    }
    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(spotifyBroadcastReceiver)
    }
}

private fun OkHttpClient.setFollowRedirects(b: Boolean) {

}
