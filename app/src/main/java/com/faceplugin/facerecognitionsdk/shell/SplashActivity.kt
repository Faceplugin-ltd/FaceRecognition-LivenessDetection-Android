package com.faceplugin.facerecognitionsdk.shell

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.faceplugin.facerecognitionsdk.R

/** Logo splash. The engine is already activating on a background thread. */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val open = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(open, 900)
    }

    override fun onDestroy() {
        handler.removeCallbacks(open)
        super.onDestroy()
    }
}
