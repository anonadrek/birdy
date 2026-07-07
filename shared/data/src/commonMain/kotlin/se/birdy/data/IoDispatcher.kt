package se.birdy.data

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-specific IO dispatcher. On JVM, uses Dispatchers.IO for thread pool efficiency.
 * On Native (iOS), uses Dispatchers.Default since there's no distinct IO thread pool.
 */
expect val ioDispatcher: CoroutineDispatcher
