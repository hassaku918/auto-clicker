package com.example.autoclickerblocker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper

object ScreenGrab {
    @Volatile private var bitmap: Bitmap? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var projection: MediaProjection? = null
    private val handler = Handler(Looper.getMainLooper())

    val ready: Boolean get() = projection != null && bitmap != null

    fun start(context: Context, mp: MediaProjection) {
        stop()
        projection = mp
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1)
        val h = dm.heightPixels.coerceAtLeast(1)
        val dpi = dm.densityDpi
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            runCatching {
                val plane = img.planes[0]
                val buf = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * img.width
                val bmp = Bitmap.createBitmap(
                    img.width + rowPadding / pixelStride,
                    img.height,
                    Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buf)
                val cropped = if (bmp.width != img.width || bmp.height != img.height) {
                    Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
                } else bmp
                val old = bitmap
                bitmap = cropped
                if (old != null && old !== cropped) old.recycle()
                if (cropped !== bmp) bmp.recycle()
            }
            img.close()
        }, handler)
        reader = r
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, handler)
        display = mp.createVirtualDisplay(
            "color-grab",
            w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )
    }

    fun pixel(x: Int, y: Int): Int? {
        val b = bitmap ?: return null
        val px = x.coerceIn(0, (b.width - 1).coerceAtLeast(0))
        val py = y.coerceIn(0, (b.height - 1).coerceAtLeast(0))
        return runCatching { b.getPixel(px, py) }.getOrNull()
    }

    fun stop() {
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
        bitmap?.recycle()
        bitmap = null
    }
}
