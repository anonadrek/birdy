package se.birdy.app.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-specific IO dispatcher. `Dispatchers.IO` is not visible from common code
 * once a Native target exists (mirrors the same fix already applied in shared:data).
 * On Android, uses Dispatchers.IO for thread pool efficiency. On Native (iOS), uses
 * Dispatchers.Default since there's no distinct IO thread pool.
 */
internal expect val ioDispatcher: CoroutineDispatcher
