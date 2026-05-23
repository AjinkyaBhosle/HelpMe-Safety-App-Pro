package com.helpme.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File

@CapacitorPlugin(name = "SafetyCameraPlugin")
class SafetyCameraPlugin : Plugin() {

    companion object {
        const val REQUEST_VIDEO_CAPTURE = 1001
        const val REQUEST_IMAGE_CAPTURE = 1002
        const val EXTRA_VIDEO_URI = "video_uri"
    }

    private var videoUri: Uri? = null
    private var videoFile: File? = null
    
    private var photoUri: Uri? = null
    private var photoFile: File? = null

    @PluginMethod
    fun captureVideo(call: PluginCall) {
        // Create file for video
        videoFile = createVideoFile()
        videoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile!!
        )

        // Launch system camera
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1) // High quality
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 300) // 5 min max
        }

        startActivityForResult(call, intent, "handleMediaResult")
    }

    @PluginMethod
    fun capturePhoto(call: PluginCall) {
        // Create file for photo
        photoFile = createPhotoFile()
        photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile!!
        )

        // Launch system camera for image
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }

        startActivityForResult(call, intent, "handleMediaResult")
    }

    @ActivityCallback
    private fun handleMediaResult(call: PluginCall?, result: androidx.activity.result.ActivityResult?) {
        if (result != null && result.resultCode == Activity.RESULT_OK) {
            val ret = JSObject()
            ret.put("success", true)
            
            // value depends on what was called. We can infer or check which file exists/was recently created
            // But better: checks which RequestCode was used. But ActivityCallback doesn't expose RequestCode easily in Capacitor 3+ standard way without saveCall?
            // Actually, `startActivityForResult` saves the call for the callback.
            // But we need to know if it was video or photo.
            // Simplest way: Check which file isn't null and has content? 
            // Or rely on the fact that we set member variables.
            // Note: Parallel calls aren't supported by this simple plugin design, so member vars are okay.
            
            if (videoFile != null && videoFile!!.exists() && videoFile!!.length() > 0) {
                 ret.put("filePath", videoFile?.absolutePath)
                 ret.put("type", "video")
                 // Reset
                 videoFile = null 
            } else if (photoFile != null && photoFile!!.exists() && photoFile!!.length() > 0) {
                 ret.put("filePath", photoFile?.absolutePath)
                 ret.put("type", "photo")
                 // Reset
                 photoFile = null
            } else {
                 call?.reject("Media capture failed or empty")
                 return
            }

            call?.resolve(ret)
        } else {
            call?.reject("Capture cancelled")
        }
    }

    private fun createVideoFile(): File {
        val storageDir = File(context.getExternalFilesDir(null), "SafetyCam/Videos")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File(storageDir, "safety_vid_${System.currentTimeMillis()}.mp4")
    }

    private fun createPhotoFile(): File {
        val storageDir = File(context.getExternalFilesDir(null), "SafetyCam/Photos")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File(storageDir, "safety_img_${System.currentTimeMillis()}.jpg")
    }
}
