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
 * Holder and execution bridge for the on-device LiteRT-LM Engine instance.
 * The engine is initialized on application startup and stored here for access by the
 * local query engine and grounded RAG synthesis.
 */
object AIEngineProvider {
    @Volatile
    var engine: Engine? = null

    fun isAvailable(): Boolean {
        val eng = engine
        return eng != null && eng.isInitialized()
    }

    /**
     * Executes prompt inference locally on the device using Gemma 3 1B IT.
     * Returns null if engine is unavailable or if inference errors.
     */
    suspend fun generateText(prompt: String): String? {
        val eng = engine ?: return null
        if (!eng.isInitialized()) return null

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
                            if (cont.isActive) {
                                cont.resume(sb.toString().trim())
                            }
                        }

                        override fun onError(throwable: Throwable) {
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
            try {
                Log.w("AIEngineProvider", "Gemma inference failed: ${e.message}")
            } catch (_: Throwable) {}
            null
        }
    }
}
