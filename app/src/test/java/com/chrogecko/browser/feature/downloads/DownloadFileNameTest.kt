package com.chrogecko.browser.feature.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFileNameTest {

    @Test
    fun usesQuotedDispositionName() {
        assertEquals(
            "report.pdf",
            DownloadManager.fileNameFor("https://x.test/file", "attachment; filename=\"report.pdf\""),
        )
    }

    @Test
    fun prefersEncodedDispositionName() {
        assertEquals(
            "naïve.txt",
            DownloadManager.fileNameFor(
                "https://x.test/file",
                "attachment; filename*=UTF-8''na%C3%AFve.txt",
            ),
        )
    }

    @Test
    fun fallsBackToPathAndStripsQuery() {
        assertEquals(
            "archive.zip",
            DownloadManager.fileNameFor("https://x.test/a/archive.zip?sig=1", null),
        )
    }

    @Test
    fun replacesIllegalCharactersAndBlanks() {
        assertEquals("a_b", DownloadManager.fileNameFor("https://x.test/a:b?.bin", null))
        assertEquals("download.bin", DownloadManager.fileNameFor("https://x.test/", null))
    }

    @Test
    fun rejectsDotOnlyFilenames() {
        assertEquals("download.bin", DownloadManager.fileNameFor("https://x.test/.", null))
        assertEquals("download.bin", DownloadManager.fileNameFor("https://x.test/..", null))
        assertEquals("download.bin", DownloadManager.fileNameFor("https://x.test/file", "attachment; filename=\"...\""))
    }
}
