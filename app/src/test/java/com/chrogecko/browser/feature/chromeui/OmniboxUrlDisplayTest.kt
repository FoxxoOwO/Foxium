package com.chrogecko.browser.feature.chromeui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OmniboxUrlDisplayTest {

    private val transformation = OmniboxDomainVisualTransformation(
        hostColor = Color.Black,
        mutedColor = Color.Gray,
    )

    @Test
    fun hidesHttpsAndHighlightsDomainWithValidOffsets() {
        val raw = "https://www.example.com/a/b?q=1"
        val span = computeOmniboxUrlSpan(raw)
        assertNotNull(span)
        assertEquals(8, span!!.displayPrefixEnd)
        assertEquals("www.example.com", raw.substring(span.hostStart, span.hostEnd))

        val transformed = transformation.filter(AnnotatedString(raw))
        assertEquals("www.example.com/a/b?q=1", transformed.text.text)

        for (i in 0..raw.length) {
            val mapped = transformed.offsetMapping.originalToTransformed(i)
            assertEquals(true, mapped in 0..transformed.text.length)
        }
        for (j in 0..transformed.text.length) {
            val back = transformed.offsetMapping.transformedToOriginal(j)
            assertEquals(true, back in 0..raw.length)
        }
        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(0, transformed.offsetMapping.originalToTransformed(8))
        assertEquals(8, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(raw.length, transformed.offsetMapping.transformedToOriginal(transformed.text.length))
    }

    @Test
    fun handlesHttpLocalhostWithPort() {
        val raw = "http://localhost:8080/test"
        val span = computeOmniboxUrlSpan(raw)
        assertNotNull(span)
        assertEquals(7, span!!.displayPrefixEnd)
        assertEquals("localhost", raw.substring(span.hostStart, span.hostEnd))

        val transformed = transformation.filter(AnnotatedString(raw))
        assertEquals("localhost:8080/test", transformed.text.text)
        assertEquals(raw.length, transformed.offsetMapping.transformedToOriginal(transformed.text.length))
    }

    @Test
    fun leavesPlainQueriesAndBlanksUntouched() {
        assertNull(computeOmniboxUrlSpan("hello world"))
        assertNull(computeOmniboxUrlSpan(""))
        assertNull(computeOmniboxUrlSpan("   "))
        assertNull(computeOmniboxUrlSpan("https://"))

        assertEquals("hello world", transformation.filter(AnnotatedString("hello world")).text.text)
        assertEquals("", transformation.filter(AnnotatedString("")).text.text)
    }
}
