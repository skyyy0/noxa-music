package com.example.data.remote

import android.util.Log
import com.example.BuildConfig

/**
 * Reads optional build-time configuration produced by the Secrets Gradle Plugin
 * (see `.env` / `.env.example`) without creating a hard compile-time dependency
 * on the generated BuildConfig fields.
 *
 * The secrets plugin only generates `BuildConfig.<NAME>` when the property is
 * present in `.env`/`.env.example`, so a fresh clone without a `.env` file would
 * otherwise fail to compile. A missing key simply resolves to an empty string,
 * which lets the app surface a clear "not configured" state instead of crashing.
 */
object Secrets {

    fun get(name: String): String = try {
        val field = BuildConfig::class.java.getField(name)
        (field.get(null) as? String).orEmpty()
    } catch (e: NoSuchFieldException) {
        ""
    } catch (e: Exception) {
        Log.w("Secrets", "Unable to read BuildConfig field \"$name\"")
        ""
    }
}
