package se.birdy.app.photo

import android.content.Context

actual object PhotoStorageProvider {
    private var instance: PhotoStorage? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun get(): PhotoStorage {
        instance?.let { return it }
        val storage = AndroidPhotoStorage(appContext.filesDir)
        instance = storage
        return storage
    }
}
