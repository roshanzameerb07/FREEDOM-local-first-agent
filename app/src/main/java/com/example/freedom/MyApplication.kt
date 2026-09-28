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

class MyApplication : Application() {
    private var engine: Engine? = null

    override fun onCreate() {
        super.onCreate()
        // Verify device memory (minimum 5000 MiB available to OS for a 6 GB device)
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem
        val minRam = 5000L * 1024 * 1024 // 5000 MiB threshold for 6 GB physical RAM devices
        if (totalRam < minRam) {
            Log.w("MyApplication", "Insufficient RAM (${totalRam / (1024 * 1024)} MiB) for Gemma-3 1B model; skipping AI initialization.")
            return
        }

        // Initialise the Litert-LM engine on a background thread with the model file
        Thread {
            try {
                val modelFile = File(filesDir, "gemma3-1b-it-int4.litertlm")
                if (!modelFile.exists() || modelFile.length() < 500_000_000L) {
                    val tmpFile = File("/data/local/tmp/gemma3-1b-it-int4.litertlm")
                    if (tmpFile.exists() && tmpFile.canRead() && tmpFile.length() >= 500_000_000L) {
                        Log.i("MyApplication", "Copying Gemma model from /data/local/tmp...")
                        tmpFile.inputStream().use { input ->
                            FileOutputStream(modelFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        Log.i("MyApplication", "Extracting Gemma model from assets to filesDir...")
                        assets.open("gemma3-1b-it-int4.litertlm").use { input ->
                            FileOutputStream(modelFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    Log.i("MyApplication", "Model ready at ${modelFile.absolutePath} (${modelFile.length()} bytes)")
                }

                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU()
                )
                val engineInstance = Engine(config)
                engineInstance.initialize()
                engine = engineInstance
                // Expose engine globally for AI modules
                com.example.freedom.domain.ai.AIEngineProvider.engine = engineInstance
                Log.i("MyApplication", "Litert-LM engine initialized successfully with Gemma-3 model at ${modelFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("MyApplication", "Litert-LM engine init failed: " + e.message, e)
                // Graceful fallback – UI can still function without AI
            }
        }.start()
    }
}
