package com.chrogecko.browser.feature.chromeui

import java.util.HashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabOpenerPolicyTest {

    @Test
    fun returnsOpenerOnlyWhenItStillExists() {
        val openers = mapOf("child" to "parent")
        assertEquals("parent", TabOpenerPolicy.resolve(openers, "child", setOf("child", "parent")))
        assertNull(TabOpenerPolicy.resolve(openers, "child", setOf("child")))
        assertNull(TabOpenerPolicy.resolve(openers, "other", setOf("parent")))
    }

    @Test
    fun closingParentDropsItsChildren() {
        val openers = HashMap(mapOf("a" to "root", "b" to "root", "root" to "origin"))
        TabOpenerPolicy.prune(openers, "root")
        assertEquals(emptyMap<String, String>(), openers)
    }
}
