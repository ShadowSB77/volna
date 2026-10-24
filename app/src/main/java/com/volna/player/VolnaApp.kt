package com.volna.player

import android.app.Application
import com.volna.player.data.Downloader
import com.volna.player.data.Library
import com.volna.player.data.SoundCloud
import com.volna.player.player.PlayerHolder
import com.volna.player.player.Streamer

class VolnaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SoundCloud.init(this)
        Library.init(this)
        Downloader.init(this)
        PlayerHolder.init(this)
        Streamer.init()
    }
}
