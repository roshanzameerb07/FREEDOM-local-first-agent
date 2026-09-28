package com.example.freedom

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend

class MyApplication : Application() {
    private var engine: Engine? = null

    override fun onCreate() {
        super.onCreate()
        // Verify device memory (minimum 6 GB) before attempting to load the large model
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem
        val minRam = 6L * 1024 * 1024 * 1024 // 6 GB
        if (totalRam < minRam) {
            Log.w("MyApplication", "Insufficient RAM (${totalRam / (1024 * 1024)} MiB) for Gemma‑3 1B model; skipping AI initialization.")
            return
        }

        // Initialise the Litert‑LM engine on a background thread with the correct model asset
        Thread {
            try {
                val config = EngineConfig(
                    modelPath = "gemma3-1b-it-int4.litertlm",
                    backend = Backend.CPU()
                )
                engine = Engine(config)
                // Expose engine globally for AI modules
                com.example.freedom.domain.ai.AIEngineProvider.engine = engine
                engine?.initialize()
                Log.i("MyApplication", "Litert‑LM engine initialized successfully with Gemma‑3 model")
            } catch (e: Exception) {
                Log.e("MyApplication", "Litert‑LM engine init failed: " + e.message)
                // Graceful fallback – UI can still function without AI
            }
        }.start()
    }
}
