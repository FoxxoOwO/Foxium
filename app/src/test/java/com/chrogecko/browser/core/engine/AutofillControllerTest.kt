package com.chrogecko.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutofillControllerTest {

    @Test
    fun hostOfStripsSchemePortPathAndWww() {
        assertEquals("example.com", AutofillController.hostOf("http://www.example.com:80/login"))
        assertEquals("example.com", AutofillController.hostOf("https://Example.COM/account"))
        assertEquals("example.com", AutofillController.hostOf("www.example.com"))
    }

    @Test
    fun hostOfRejectsEmptyDomains() {
        assertNull(AutofillController.hostOf(null))
        assertNull(AutofillController.hostOf("   "))
        assertNull(AutofillController.hostOf("www."))
    }
}
