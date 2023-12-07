package com.example.lyricsify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object Utils {

    const val SPOTIFY_PACKAGE = "com.spotify.music";

    fun openApp(context: Context, packageName: String): Boolean {
        val manager: PackageManager = context.packageManager
        return try {
            val i: Intent? = manager.getLaunchIntentForPackage(packageName)
            if (i == null) {
                false
            } else {
                i.addCategory(Intent.CATEGORY_LAUNCHER)
                context.startActivity(i)
                true
            }
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun getTimeStamp(millis: Long): String {
        val minutes: Long = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds: Long = (millis / 1000) % 60
        return String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    }

    fun getTimeStampFromDate(timeStamp: Long): String {
        val date = Date(timeStamp)
        val formatter: DateFormat = SimpleDateFormat("HH:mm:ss")
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(date)
    }
}