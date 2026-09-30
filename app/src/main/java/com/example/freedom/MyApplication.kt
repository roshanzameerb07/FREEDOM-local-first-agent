package com.example.freedom

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import java.io.File
import java.io.FileOutputStream

/**
 * Application entry point for FREEDOM.
 * Initializes Qwen3 1.7B INT4 on-device LLM via LiteRT-LM.
 */
class MyApplication : Application() {

    private var engine: Engine? = null

    override fun onCreate() {
        super.onCreate()

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem
        val minRam = 4000L * 1024 * 1024 // 4000 MiB minimum threshold for Qwen3-1.7B INT4

        Log.i("MyApplication", "Device RAM: ${totalRam / (1024 * 1024)} MiB")

        if (totalRam < minRam) {
            Log.w("MyApplication", "RAM (${totalRam / (1024 * 1024)} MiB) below recommended minimum for Qwen3 1.7B; skipping AI initialization.")
            return
        }

        // Initialize LiteRT-LM engine on background thread
        Thread {
            val startTime = System.currentTimeMillis()
            try {
                // Model file asset resolution
                var modelFile = File(filesDir, "qwen3-1.7b-int4.litertlm")
                
                if (!modelFile.exists() || modelFile.length() < 300_000_000L) {
                    val fallbackAsset = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm"
                    val primaryAsset = "qwen3-1.7b-int4.litertlm"
                    
                    val assetToUse = if (assets.list("")?.contains(primaryAsset) == true) {
                        primaryAsset
                    } else {
                        fallbackAsset
                    }

                    Log.i("MyApplication", "Extracting Qwen3 model asset ($assetToUse) to filesDir...")
                    assets.open(assetToUse).use { input ->
                        FileOutputStream(modelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i("MyApplication", "Model extracted to ${modelFile.absolutePath} (${modelFile.length()} bytes)")
                }

                // Attempt GPU acceleration first, fallback to CPU
                var backendUsed = "GPU"
                var engineInstance: Engine? = null

                try {
                    val gpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.GPU()
                    )
                    engineInstance = Engine(gpuConfig)
                    engineInstance.initialize()
                    Log.i("MyApplication", "Qwen3 1.7B initialized successfully with GPU backend.")
                } catch (gpuException: Throwable) {
                    Log.w("MyApplication", "GPU backend init failed (${gpuException.message}); attempting CPU fallback...")
                    backendUsed = "CPU"
                    val cpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU()
                    )
                    engineInstance = Engine(cpuConfig)
                    engineInstance.initialize()
                    Log.i("MyApplication", "Qwen3 1.7B initialized successfully with CPU backend.")
                }

                val duration = System.currentTimeMillis() - startTime
                engine = engineInstance

                // Expose engine instance & metadata globally
                com.example.freedom.domain.ai.AIEngineProvider.apply {
                    engine = engineInstance
                    modelName = "Qwen3-1.7B INT4"
                    backendType = backendUsed
                    initDurationMs = duration
                }

                Log.i(
                    "MyApplication",
                    "LiteRT-LM Engine Ready: Model=Qwen3 1.7B, Backend=$backendUsed, InitTime=${duration}ms"
                )

            } catch (e: Exception) {
                Log.e("MyApplication", "LiteRT-LM Qwen3 engine initialization failed: " + e.message, e)
                com.example.freedom.domain.ai.AIEngineProvider.lastError = e.message
            }
        }.start()
    }
}
