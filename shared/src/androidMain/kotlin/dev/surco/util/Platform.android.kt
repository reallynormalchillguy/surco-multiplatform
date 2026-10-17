package dev.surco.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun nowMillis(): Long = System.currentTimeMillis()
actual fun utcOffsetSeconds(epochSec: Long): Int = java.util.TimeZone.getDefault().getOffset(epochSec * 1000) / 1000
actual val IoDispatcher: CoroutineDispatcher get() = Dispatchers.IO
