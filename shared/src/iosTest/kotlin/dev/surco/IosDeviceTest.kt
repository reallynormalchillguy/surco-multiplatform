package dev.surco

import dev.surco.ios.IosDevice
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosDeviceTest {
    @Test
    fun oledByModel() {
        assertTrue(IosDevice.isOled("iPhone18,1"))   // iPhone 17
        assertTrue(IosDevice.isOled("iPhone10,3"))   // iPhone X
        assertFalse(IosDevice.isOled("iPhone12,1"))  // iPhone 11 (LCD)
        assertFalse(IosDevice.isOled("iPhone14,6"))  // SE 3.ª gen.
        assertFalse(IosDevice.isOled("iPhone9,1"))   // iPhone 7
        assertTrue(IosDevice.isOled("iPad16,3"))     // iPad Pro M4
        assertFalse(IosDevice.isOled("iPad14,1"))
        println("Simulador: ${IosDevice.model} → OLED=${IosDevice.isOled()}")
    }
}
