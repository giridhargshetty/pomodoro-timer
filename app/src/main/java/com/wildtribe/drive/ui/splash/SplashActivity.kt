package com.wildtribe.drive.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.wildtribe.drive.R
import com.wildtribe.drive.databinding.ActivitySplashBinding
import com.wildtribe.drive.ui.bluetooth.BluetoothScanActivity
import com.wildtribe.drive.utils.DebugLogger

/**
 * Boot animation screen. Plays wildtribe_intro.gif once, then navigates to scan screen.
 *
 * FIX 1: If GIF is missing or fails, shows static Garmin-style fallback logo
 * ("WILD TRIBE DRIVE" in garmin_green) and navigates after 2.5s.
 *
 * No skip button — it's a branding moment.
 *
 * TEST: GIF fallback logo shows if raw/wildtribe_intro.gif missing
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadBootAnimation()
    }

    /**
     * FIX 1: Try to load the GIF. If the resource doesn't exist or Glide fails,
     * show the static Garmin-style fallback logo.
     */
    private fun loadBootAnimation() {
        try {
            // Probe if the raw resource exists before delegating to Glide
            resources.openRawResourceFd(R.raw.wildtribe_intro)?.close()

            Glide.with(this)
                .asGif()
                .load(R.raw.wildtribe_intro)
                .listener(object : RequestListener<GifDrawable> {
                    override fun onResourceReady(
                        resource: GifDrawable,
                        model: Any?,
                        target: Target<GifDrawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.gifImageView.visibility = View.VISIBLE
                        binding.fallbackLayout.visibility = View.GONE
                        resource.setLoopCount(1)
                        val fc = resource.frameCount
                        val fd = if (fc > 0) resource.getFrameDuration(0).toLong() else 100L
                        val duration = (fc * fd).coerceIn(2_000L, 5_000L)
                        binding.gifImageView.postDelayed({ goToScan() }, duration)
                        return false
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<GifDrawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        DebugLogger.log("SPLASH", "GIF load failed — showing fallback logo")
                        showFallbackLogo()
                        return true
                    }
                })
                .into(binding.gifImageView)

        } catch (e: Exception) {
            // FIX 1: GIF not present in res/raw/ — show fallback
            DebugLogger.log("SPLASH", "wildtribe_intro.gif missing — showing Garmin fallback logo")
            showFallbackLogo()
        }
    }

    private fun showFallbackLogo() {
        binding.gifImageView.visibility = View.GONE
        binding.fallbackLayout.visibility = View.VISIBLE
        binding.fallbackLayout.postDelayed({ goToScan() }, 2_500L)
    }

    private fun goToScan() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, BluetoothScanActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
