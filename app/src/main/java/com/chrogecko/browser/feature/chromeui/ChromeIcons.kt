package com.chrogecko.browser.feature.chromeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chromium's Incognito emblem: stylized fedora crown + brim above two circular glasses connected
 * by a nose bridge.
 */
@Composable
fun IncognitoBadgeIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = if (modifier === Modifier) Modifier.size(20.dp) else modifier) {
        val w = size.width
        val h = size.height

        // Hat crown (trapezoid silhouette).
        val crown = Path().apply {
            moveTo(w * 0.28f, h * 0.44f)
            lineTo(w * 0.36f, h * 0.14f)
            lineTo(w * 0.64f, h * 0.14f)
            lineTo(w * 0.72f, h * 0.44f)
            close()
        }
        drawPath(crown, color = tint)

        // Hat brim.
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.47f),
            end = Offset(w * 0.88f, h * 0.47f),
            strokeWidth = (h * 0.085f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
        )

        // Glasses lenses + bridge.
        val lensRadius = w * 0.135f
        val lensY = h * 0.73f
        val leftX = w * 0.31f
        val rightX = w * 0.69f
        val lensStroke = (w * 0.09f).coerceAtLeast(1.5f)
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = Offset(leftX, lensY),
            style = Stroke(width = lensStroke),
        )
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = Offset(rightX, lensY),
            style = Stroke(width = lensStroke),
        )
        drawLine(
            color = tint,
            start = Offset(leftX + lensRadius * 0.9f, lensY - lensRadius * 0.15f),
            end = Offset(rightX - lensRadius * 0.9f, lensY - lensRadius * 0.15f),
            strokeWidth = lensStroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Chrome Android's authentic rounded-square tab counter button graphic.
 */
@Composable
fun ChromeTabCountIcon(
    count: Int,
    isPrivate: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val label = if (count >= 100) {
        if (isPrivate) ";)" else ":D"
    } else {
        count.coerceAtLeast(0).toString()
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        border = BorderStroke(1.8.dp, tint),
        modifier = modifier.size(21.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = tint,
                fontSize = if (label.length >= 2) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 11.sp,
            )
        }
    }
}

/**
 * Four-color Chromium-inspired geometric emblem for the New Tab Page wordmark.
 */
@Composable
fun ChroGeckoLogoEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = if (modifier === Modifier) Modifier.size(44.dp) else modifier) {
        val diameter = size.minDimension
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = radius * 0.72f
        val ringStroke = radius * 0.42f
        val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
        val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

        // Red top segment, Yellow right/bottom-right segment, Green bottom-left segment.
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 205f,
            sweepAngle = 125f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = ringStroke),
        )
        drawArc(
            color = Color(0xFFFBBC04),
            startAngle = 330f,
            sweepAngle = 115f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = ringStroke),
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 85f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = ringStroke),
        )

        // Crisp white halo ring separating outer tricolor bands from the blue center orb.
        drawCircle(
            color = Color.White,
            radius = radius * 0.48f,
            center = center,
        )
        drawCircle(
            color = Color(0xFF1A73E8),
            radius = radius * 0.37f,
            center = center,
        )
    }
}

/** Range slices inside a full URL for Chrome-style scheme elision and host emphasis. */
data class OmniboxUrlSpan(
    val displayPrefixEnd: Int,
    val hostStart: Int,
    val hostEnd: Int,
)

/**
 * Computes Chrome's URL display slicing: hides `https://` / `http://` and identifies the hostname
 * range to highlight. Returns `null` when [url] is blank or does not start with an HTTP(S) scheme.
 */
fun computeOmniboxUrlSpan(url: String): OmniboxUrlSpan? {
    if (url.isBlank()) return null
    val prefixEnd = when {
        url.startsWith("https://", ignoreCase = true) -> 8
        url.startsWith("http://", ignoreCase = true) -> 7
        else -> return null
    }
    if (prefixEnd >= url.length) return null
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), startIndex = prefixEnd)
        .let { if (it < 0) url.length else it }
    val atIndex = url.indexOf('@', startIndex = prefixEnd)
    val hostStart = if (atIndex in prefixEnd until authorityEnd) atIndex + 1 else prefixEnd
    val colonIndex = url.indexOf(':', startIndex = hostStart)
    val hostEnd = if (colonIndex in hostStart until authorityEnd) colonIndex else authorityEnd
    if (hostEnd <= hostStart) return null
    return OmniboxUrlSpan(
        displayPrefixEnd = prefixEnd,
        hostStart = hostStart,
        hostEnd = hostEnd,
    )
}

/**
 * Formats an unfocused URL in the omnibox like Chrome: hides `http(s)://`, highlights the
 * domain in [hostColor] with Medium weight, and de-emphasizes port/path/query in [mutedColor].
 */
class OmniboxDomainVisualTransformation(
    private val hostColor: Color,
    private val mutedColor: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val span = computeOmniboxUrlSpan(raw) ?: return TransformedText(text, OffsetMapping.Identity)
        val shift = span.displayPrefixEnd
        val display = raw.substring(shift)
        val displayHostStart = (span.hostStart - shift).coerceIn(0, display.length)
        val displayHostEnd = (span.hostEnd - shift).coerceIn(displayHostStart, display.length)
        val styled = buildAnnotatedString {
            append(display)
            if (displayHostStart > 0) {
                addStyle(SpanStyle(color = mutedColor), 0, displayHostStart)
            }
            addStyle(
                SpanStyle(color = hostColor, fontWeight = FontWeight.Medium),
                displayHostStart,
                displayHostEnd,
            )
            if (displayHostEnd < display.length) {
                addStyle(SpanStyle(color = mutedColor), displayHostEnd, display.length)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                (offset - shift).coerceIn(0, display.length)

            override fun transformedToOriginal(offset: Int): Int =
                (offset + shift).coerceIn(0, raw.length)
        }
        return TransformedText(styled, mapping)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OmniboxDomainVisualTransformation) return false
        return hostColor == other.hostColor && mutedColor == other.mutedColor
    }

    override fun hashCode(): Int = 31 * hostColor.hashCode() + mutedColor.hashCode()
}
