package dev.surco.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMTForDate
import platform.Foundation.timeIntervalSince1970

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
actual fun utcOffsetSeconds(epochSec: Long): Int =
    NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate(timeIntervalSinceReferenceDate = epochSec - 978_307_200.0)).toInt()
actual val IoDispatcher: CoroutineDispatcher get() = Dispatchers.IO
