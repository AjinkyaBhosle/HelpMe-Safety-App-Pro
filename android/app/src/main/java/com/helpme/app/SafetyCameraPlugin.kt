package com.helpme.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File
import java.io.FileOutputStream

@CapacitorPlugin(name = "SafetyCameraPlugin")
class SafetyCameraPlugin : Plugin() {

    private var videoUri: Uri? = null
    private var videoFile: File? = null
    
    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private var currentWatermarkText: String = ""

    private fun saveState(key: String, value: String?) {
        val prefs = context.getSharedPreferences("SafetyCameraPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    private fun getState(key: String): String? {
        val prefs = context.getSharedPreferences("SafetyCameraPrefs", android.content.Context.MODE_PRIVATE)
        return prefs.getString(key, null)
    }

    private fun clearState() {
        val prefs = context.getSharedPreferences("SafetyCameraPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @PluginMethod
    fun captureVideo(call: PluginCall) {
        currentWatermarkText = call.getString("watermark") ?: ""
        saveState("currentWatermarkText", currentWatermarkText)
        
        videoFile = createVideoFile()
        saveState("videoFile", videoFile!!.absolutePath)
        saveState("photoFile", null)
        
        videoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile!!
        )

        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1) // High quality
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 300) // 5 min max
        }

        startActivityForResult(call, intent, "handleMediaResult")
    }

    @PluginMethod
    fun capturePhoto(call: PluginCall) {
        currentWatermarkText = call.getString("watermark") ?: ""
        saveState("currentWatermarkText", currentWatermarkText)
        
        photoFile = createPhotoFile()
        saveState("photoFile", photoFile!!.absolutePath)
        saveState("videoFile", null)
        
        photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile!!
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }

        startActivityForResult(call, intent, "handleMediaResult")
    }

    @ActivityCallback
    private fun handleMediaResult(call: PluginCall?, result: androidx.activity.result.ActivityResult?) {
        // Restore state if process was killed by Android OS
        if (videoFile == null) getState("videoFile")?.let { videoFile = File(it) }
        if (photoFile == null) getState("photoFile")?.let { photoFile = File(it) }
        if (currentWatermarkText.isEmpty()) currentWatermarkText = getState("currentWatermarkText") ?: ""

        if (result != null && result.resultCode == Activity.RESULT_OK) {
            val ret = JSObject()
            ret.put("success", true)
            
            if (videoFile != null && videoFile!!.exists() && videoFile!!.length() > 0) {
                 val inputPath = videoFile!!.absolutePath
                 
                 // If no watermark text, resolve immediately
                 if (currentWatermarkText.isEmpty()) {
                     ret.put("filePath", inputPath)
                     ret.put("type", "video")
                     call?.resolve(ret)
                     videoFile = null
                     return
                 }
                 
                 // Process Video Watermark async
                 processVideoWatermark(inputPath, currentWatermarkText, call, ret)
                 videoFile = null 
                 
            } else if (photoFile != null && photoFile!!.exists() && photoFile!!.length() > 0) {
                 val inputPath = photoFile!!.absolutePath
                 
                 if (currentWatermarkText.isNotEmpty()) {
                     watermarkPhoto(inputPath, currentWatermarkText)
                 }
                 
                 ret.put("filePath", inputPath)
                 ret.put("type", "photo")
                 call?.resolve(ret)
                 photoFile = null
                 clearState()
                 
            } else {
                 call?.reject("Media capture failed or empty")
                 clearState()
            }
        } else {
            call?.reject("Capture cancelled")
            clearState()
        }
    }

    private fun createWatermarkImage(watermarkText: String, videoWidth: Int): String? {
        try {
            val calculatedTextSize = (videoWidth * 0.035f).coerceAtLeast(20f).coerceAtMost(60f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textSize = calculatedTextSize
                style = Paint.Style.FILL
                setShadowLayer(5f, 3f, 3f, Color.BLACK)
            }
            val bgPaint = Paint().apply {
                color = Color.parseColor("#99000000") // 60% black
            }
            
            val lines = watermarkText.split("\n")
            val padding = 20f
            
            var maxWidth = 0f
            for (line in lines) {
                val w = paint.measureText(line)
                if (w > maxWidth) maxWidth = w
            }
            
            // Constrain text to fit inside the video frame horizontally (accounting for 30px padding on left/right)
            val maxAllowedWidth = videoWidth - 60f
            if (maxWidth > maxAllowedWidth && maxWidth > 0f) {
                val scale = maxAllowedWidth / maxWidth
                paint.textSize = paint.textSize * scale
                
                // Recalculate max width after scaling down
                maxWidth = 0f
                for (line in lines) {
                    val w = paint.measureText(line)
                    if (w > maxWidth) maxWidth = w
                }
            }
            
            val width = (maxWidth + padding * 2).toInt()
            val height = ((lines.size * (paint.textSize + 15f)) + padding * 2).toInt()
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            val bgRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRect(bgRect, bgPaint)
            
            var yOffset = padding + paint.textSize
            val xOffset = padding
            
            for (line in lines) {
                canvas.drawText(line, xOffset, yOffset, paint)
                yOffset += paint.textSize + 15f
            }
            
            val file = File(context.cacheDir, "watermark_${System.currentTimeMillis()}.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            
            return file.absolutePath
        } catch (e: Exception) {
            Log.e("SafetyCameraPlugin", "Image generation failed", e)
            return null
        }
    }

    private fun processVideoWatermark(inputPath: String, watermarkText: String, call: PluginCall?, ret: JSObject) {
        val outputPath = inputPath.replace(".mp4", "_wm.mp4")
        
        var videoWidth = 720
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(inputPath)
            val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            
            var w = widthStr?.toIntOrNull() ?: 720
            var h = heightStr?.toIntOrNull() ?: 1280
            val rotation = rotationStr?.toIntOrNull() ?: 0
            
            if (rotation == 90 || rotation == 270) {
                w = h.also { h = w }
            }
            videoWidth = w
            retriever.release()
        } catch (e: Exception) {
            Log.e("SafetyCameraPlugin", "Failed to extract video metadata", e)
        }
        
        val watermarkImagePath = createWatermarkImage(watermarkText, videoWidth)
        
        Thread {
            try {
                if (watermarkImagePath == null) {
                    call?.reject("Failed to generate watermark image")
                    return@Thread
                }
                
                // Reverting to h264_mediacodec since libx264 is not compiled into the current FFmpegKit package
                val cmd = "-y -i \"$inputPath\" -i \"$watermarkImagePath\" -filter_complex \"[0:v]scale=trunc(iw/2)*2:trunc(ih/2)*2[v0];[v0][1:v]overlay=30:main_h-overlay_h-30\" -c:v h264_mediacodec -b:v 3M -c:a copy \"$outputPath\""
                
                val session = FFmpegKit.execute(cmd)
                File(watermarkImagePath).delete() // Clean up watermark image
                
                if (ReturnCode.isSuccess(session.returnCode)) {
                    val inFile = File(inputPath)
                    val outFile = File(outputPath)
                    if (outFile.exists()) {
                        inFile.delete()
                        outFile.renameTo(inFile)
                    }
                    
                    ret.put("filePath", inputPath)
                    ret.put("type", "video")
                    ret.put("success", true)
                    call?.resolve(ret)
                } else {
                    val errorLog = session.allLogsAsString
                    Log.e("SafetyCameraPlugin", "FFmpeg failed: $errorLog")
                    call?.reject("Watermark failed: $errorLog")
                }
            } catch (e: Exception) {
                Log.e("SafetyCameraPlugin", "FFmpeg Exception: ${e.message}")
                call?.reject("Watermark exception: ${e.message}")
            }
        }.start()
    }

    private fun watermarkPhoto(photoPath: String, watermarkText: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            
            // Auto scale font size based on image width, made smaller to fit long URLs
            val calculatedTextSize = (bitmap.width * 0.025f).coerceAtLeast(24f)
            
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textSize = calculatedTextSize
                style = Paint.Style.FILL
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }

            val bgPaint = Paint().apply {
                color = Color.parseColor("#99000000") // 60% black
            }

            val lines = watermarkText.split("\n")
            val padding = 20f
            var yOffset = canvas.height.toFloat() - (lines.size * (paint.textSize + 15f)) - padding
            val xOffset = padding + 10f

            // Draw Background Rect
            val maxLineWidth = lines.maxOf { paint.measureText(it) }
            val bgRect = RectF(
                xOffset - 15f,
                yOffset - paint.textSize - 15f,
                xOffset + maxLineWidth + 15f,
                canvas.height.toFloat() - padding + 5f
            )
            canvas.drawRect(bgRect, bgPaint)

            // Draw Text
            for (line in lines) {
                canvas.drawText(line, xOffset, yOffset, paint)
                yOffset += paint.textSize + 15f
            }

            val out = FileOutputStream(photoPath)
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            Log.e("SafetyCameraPlugin", "Photo watermark failed", e)
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
