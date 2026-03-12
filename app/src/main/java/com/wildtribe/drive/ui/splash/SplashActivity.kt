package com.wildtribe.drive.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
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
import com.wildtribe.drive.util.LogHelper

/**
 * Splash screen that plays the wildtribe_intro.gif boot animation once,
 * then fades into the Bluetooth connection screen.
 *
 * If the GIF is missing or fails to load, a fallback logo + app name is shown.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen immersive mode for boot animation
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadBootAnimation()
    }

    private fun loadBootAnimation() {
        Glide.with(this)
            .asGif()
            .load(R.raw.wildtribe_intro)
            .listener(object : RequestListener<GifDrawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<GifDrawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    LogHelper.w("SplashActivity", "GIF failed to load – showing fallback")
                    showFallback()
                    return false
                }

                override fun onResourceReady(
                    resource: GifDrawable,
                    model: Any?,
                    target: Target<GifDrawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    // GIF loaded successfully
                    binding.gifImageView.visibility = View.VISIBLE
                    binding.fallbackLayout.visibility = View.GONE

                    // Play once, then navigate after the animation
                    resource.setLoopCount(1)
                    val duration = resource.frameCount * resource.getFrameDuration(0) // approx
                    val clampedDuration = duration.toLong().coerceIn(2_000L, 4_000L)

                    binding.gifImageView.postDelayed({ navigateToBluetooth() }, clampedDuration)
                    return false
                }
            })
            .into(binding.gifImageView)
    }

    private fun showFallback() {
        binding.gifImageView.visibility = View.GONE
        binding.fallbackLayout.visibility = View.VISIBLE

        // Navigate after 2.5 seconds minimum display for fallback
        binding.fallbackLayout.postDelayed({ navigateToBluetooth() }, 2_500L)
    }

    private fun navigateToBluetooth() {
        if (isFinishing || isDestroyed) return

        // Smooth fade-out transition
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 400
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    startActivity(Intent(this@SplashActivity, BluetoothScanActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            })
        }
        binding.root.startAnimation(fadeOut)
    }
}
