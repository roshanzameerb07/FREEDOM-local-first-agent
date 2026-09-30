package com.example.freedom.domain.ai

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Holder and execution bridge for the on-device LiteRT-LM Qwen3 1.7B Engine instance.
 * Exposes engine availability, model metadata, and text generation metrics.
 */
object AIEngineProvider {

    @Volatile
    var engine: Engine? = null

    @Volatile
    var modelName: String = "Qwen3-1.7B INT4"

    @Volatile
    var backendType: String = "CPU"

    @Volatile
    var initDurationMs: Long = 0L

    @Volatile
    var lastInferenceTimeMs: Long = 0L

    @Volatile
    var lastError: String? = null

    fun isAvailable(): Boolean {
        val eng = engine
        return eng != null && eng.isInitialized()
    }

    /**
     * Executes prompt inference locally on the device using Qwen3 1.7B INT4.
     * Returns null if engine is unavailable or if inference throws an error.
     */
    suspend fun generateText(prompt: String): String? {
        val eng = engine ?: return null
        if (!eng.isInitialized()) return null

        val startTime = System.currentTimeMillis()
        return try {
            val conversation = eng.createConversation()
            try {
                suspendCancellableCoroutine { cont ->
                    val sb = StringBuilder()
                    conversation.sendMessageAsync(prompt, object : MessageCallback {
                        override fun onMessage(message: Message) {
                            for (content in message.contents.contents) {
                                if (content is Content.Text) {
                                    sb.append(content.text)
                                }
                            }
                        }

                        override fun onDone() {
                            val duration = System.currentTimeMillis() - startTime
                            lastInferenceTimeMs = duration
                            Log.d("AIEngineProvider", "Qwen3 inference completed in ${duration}ms")
                            if (cont.isActive) {
                                cont.resume(sb.toString().trim())
                            }
                        }

                        override fun onError(throwable: Throwable) {
                            lastError = throwable.message
                            Log.e("AIEngineProvider", "Qwen3 inference error: ${throwable.message}")
                            if (cont.isActive) {
                                cont.resumeWithException(throwable)
                            }
                        }
                    })
                }
            } finally {
                try {
                    conversation.close()
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            lastError = e.message
            try {
                Log.w("AIEngineProvider", "Qwen3 inference failed: ${e.message}")
            } catch (_: Throwable) {}
            null
        }
    }
}
