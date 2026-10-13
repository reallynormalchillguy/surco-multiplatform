package dev.surco.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun nowMillis(): Long = System.currentTimeMillis()
actual val IoDispatcher: CoroutineDispatcher get() = Dispatchers.IO
