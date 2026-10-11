package org.aimlds.mymilo.local

import java.io.File

/**
 * The JNI bridge to the on-device engine (llama.cpp, via
 * libmilo_llama). Every call is defensive: if the native
 * library is missing (an architecture the build doesn't
 * carry), the engine reports itself unavailable and chat
 * falls back to a plain explanation instead of crashing.
 *
 * R8 note: the native side calls these methods by their
 * exact JNI names — proguard-rules.pro keeps this class.
 */
object LocalEngine {

    private val libraryLoaded: Boolean = try {
        System.loadLibrary("milo_llama")
        true
    } catch (e: Throwable) {
        false
    }

    @Volatile
    private var loadedPath: String? = null

    private external fun nativeLoad(path: String): Boolean
    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
    ): String
    private external fun nativeUnload()

    fun available(): Boolean = libraryLoaded

    /** Load (or switch) the model file. False on any failure. */
    fun ensureLoaded(file: File): Boolean {
        if (!libraryLoaded) return false
        if (loadedPath == file.absolutePath) return true
        return try {
            val ok = nativeLoad(file.absolutePath)
            loadedPath = if (ok) file.absolutePath else null
            ok
        } catch (e: Throwable) {
            loadedPath = null
            false
        }
    }

    /** One completion. Empty string on any failure. */
    fun generate(prompt: String, maxTokens: Int = 256): String {
        if (!libraryLoaded || loadedPath == null) return ""
        return try {
            nativeGenerate(prompt, maxTokens)
        } catch (e: Throwable) {
            ""
        }
    }

    fun unload() {
        if (!libraryLoaded) return
        try {
            nativeUnload()
        } catch (e: Throwable) {
            // nothing to free our side either way
        }
        loadedPath = null
    }
}
