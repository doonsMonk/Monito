package com.monito.expense

import com.monito.expense.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UpdateCheckerTest {

    @Test
    fun versionsCompareNumerically() {
        assertTrue(UpdateChecker.isNewer("9.1", "9.0"))
        assertTrue(UpdateChecker.isNewer("v9.10", "9.9"))
        assertTrue(UpdateChecker.isNewer("10.0", "9.9.9"))
        assertTrue(UpdateChecker.isNewer("9.1.1", "9.1"))
        assertFalse(UpdateChecker.isNewer("9.1", "9.1"))
        assertFalse(UpdateChecker.isNewer("9.1.0", "9.1"))
        assertFalse(UpdateChecker.isNewer("9.0", "9.1"))
        assertFalse(UpdateChecker.isNewer("garbage", "9.1"))
    }

    @Test
    fun parsesTheLatestReleaseAnswer() {
        val json = """{"tag_name":"v9.1","html_url":"https://github.com/doonsMonk/Monito/releases/tag/v9.1","body":"Notes ",
            "assets":[{"name":"notes.txt","browser_download_url":"https://example.org/notes.txt"},
                      {"name":"Monito-9.1.apk","browser_download_url":"https://github.com/doonsMonk/Monito/releases/download/v9.1/Monito-9.1.apk"}]}"""
        val r = UpdateChecker.parse(json)!!
        assertEquals("9.1", r.version)
        assertEquals("https://github.com/doonsMonk/Monito/releases/download/v9.1/Monito-9.1.apk", r.apkUrl)
        assertEquals("https://github.com/doonsMonk/Monito/releases/tag/v9.1", r.pageUrl)
        assertEquals("Notes", r.notes)
    }

    @Test
    fun releaseWithoutApkFallsBackToThePage() {
        val r = UpdateChecker.parse("""{"tag_name":"v9.2","assets":[]}""")!!
        assertNull(r.apkUrl)
        assertEquals(UpdateChecker.RELEASES_PAGE, r.pageUrl)
    }

    @Test
    fun anythingElseIsNotARelease() {
        assertNull(UpdateChecker.parse("""{"message":"Not Found"}"""))
        assertNull(UpdateChecker.parse("<html>"))
    }
}
