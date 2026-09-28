package com.example.freedom.domain.ai

import com.google.ai.edge.litertlm.Engine

/**
 * Simple holder for the globally‑available Litert‑LM engine instance.
 * The engine is created in MyApplication and stored here for easy access by the
 * query engine and other components.
 */
object AIEngineProvider {
    @Volatile
    var engine: Engine? = null
}
