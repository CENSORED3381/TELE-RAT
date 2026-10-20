package com.lab.rat.modules

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleService
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

object CameraModule {

    fun capture(ctx: Context, front: Boolean): File? {
        // bug #12: exige LifecycleService real, nunca cast de Context
        val service = ctx as? LifecycleService ?: return null
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val out = File(service.cacheDir,
            "cam_${if (front) "front" else "back"}_${System.currentTimeMillis()}.jpg")
        var ok = false

        try {
            val provider = ProcessCameraProvider.getInstance(service).get()
            val take = ImageCapture.Builder().build()

            provider.unbindAll() // limpa bindings anteriores antes de rebind
            provider.bindToLifecycle(
                service,
                if (front) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA,
                take
            )

            take.takePicture(
                ImageCapture.OutputFileOptions.Builder(out).build(),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                        ok = true; latch.countDown()
                    }
                    override fun onError(e: ImageCaptureException) {
                        Log.e("LabRAT", "camera", e); latch.countDown()
                    }
                })

            latch.await()
            provider.unbindAll() // bug #13: libera câmera para a próxima captura
        } catch (e: Exception) {
            Log.e("LabRAT", "camera", e)
        } finally {
            executor.shutdown()
        }
        return if (ok) out else null
    }
}
