package app.still.launcher.shortvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShortVideoRulesTest {

    @Test
    fun `patched builds of YouTube use the YouTube rule`() {
        assertEquals(YOUTUBE_SHORTS, shortVideoRuleFor("app.revanced.android.youtube"))
        assertEquals(INSTAGRAM_REELS, shortVideoRuleFor("com.instagram.android"))
        assertNull(shortVideoRuleFor("com.whatsapp"))
    }

    @Test
    fun `the player filling the screen means a short video is showing, whatever package names it`() {
        val views = listOf(SeenView("com.google.android.youtube:id/toolbar", 0.1f), SeenView("app.revanced.android.youtube:id/reel_watch_player", 1f))
        assertTrue(showsShortVideo(YOUTUBE_SHORTS, views))
        assertTrue(showsShortVideo(INSTAGRAM_REELS, listOf(SeenView("com.instagram.android:id/clips_viewer_view_pager", 0.9f))))
    }

    @Test
    fun `a small player is a preview in a feed`() {
        assertFalse(showsShortVideo(YOUTUBE_SHORTS, listOf(SeenView("com.google.android.youtube:id/reel_watch_player", 0.3f))))
    }

    @Test
    fun `the rest of the app isn't a short video`() {
        val youtube = listOf(SeenView("com.google.android.youtube:id/results", 0.9f), SeenView("com.google.android.youtube:id/reel_shelf", 0.3f))
        assertFalse(showsShortVideo(YOUTUBE_SHORTS, youtube))
        // Without an id the view's name can't be told apart.
        assertFalse(showsShortVideo(INSTAGRAM_REELS, listOf(SeenView("com.instagram.android:id/feed_tab", 0.1f), SeenView("clips_viewer_view_pager", 1f))))
    }

    @Test
    fun `Android only sends the service events from the apps and browsers the rules cover`() {
        val xml = File("src/main/res/xml/short_video_service.xml").readText()
        val listed = Regex("android:packageNames=\"([^\"]*)\"").find(xml)!!.groupValues[1].split(',').map { it.trim() }.toSet()
        assertEquals(SHORT_VIDEO_RULES.flatMap { it.packages }.toSet() + BROWSERS.map { it.packageName }, listed)
        assertEquals(listed, watchedPackages(emptySet(), OWN))
    }

    @Test
    fun `leave first, then home when leaving keeps landing on another`() {
        assertEquals(ShortVideoExit.LEAVE, shortVideoExit(emptyList(), nowMillis = 10_000))
        assertEquals(ShortVideoExit.LEAVE, shortVideoExit(listOf(9_000), nowMillis = 10_000))
        assertEquals(ShortVideoExit.HOME, shortVideoExit(listOf(8_000, 9_500), nowMillis = 10_000))
        assertEquals(ShortVideoExit.LEAVE, shortVideoExit(listOf(1_000, 2_000), nowMillis = 10_000))
    }

    @Test
    fun `short-video addresses, however the address bar shows them`() {
        assertEquals(YOUTUBE_SHORTS, shortVideoRuleForAddress("m.youtube.com/shorts/abc123"))
        assertEquals(YOUTUBE_SHORTS, shortVideoRuleForAddress("https://www.youtube.com/shorts/abc123?feature=share"))
        assertEquals(YOUTUBE_SHORTS, shortVideoRuleForAddress("youtube.com/shorts"))
        assertEquals(YOUTUBE_SHORTS, shortVideoRuleForAddress("M.YouTube.com:443/Shorts/abc"))
        assertEquals(INSTAGRAM_REELS, shortVideoRuleForAddress("instagram.com/reels/"))
        assertEquals(INSTAGRAM_REELS, shortVideoRuleForAddress("https://www.instagram.com/reel/C1x2y3/?igsh=abc"))
    }

    @Test
    fun `the rest of the sites, and other sites, aren't short videos`() {
        assertNull(shortVideoRuleForAddress("m.youtube.com"))
        assertNull(shortVideoRuleForAddress("m.youtube.com/watch?v=abc"))
        // A channel's grid of shorts; opening one goes to /shorts/….
        assertNull(shortVideoRuleForAddress("m.youtube.com/@channel/shorts"))
        assertNull(shortVideoRuleForAddress("m.youtube.com/?q=shorts"))
        assertNull(shortVideoRuleForAddress("instagram.com/someone/reels/"))
        assertNull(shortVideoRuleForAddress("google.com/search?q=youtube.com/shorts/abc"))
        assertNull(shortVideoRuleForAddress("notyoutube.com/shorts/abc"))
        // Chrome shows what was searched for in place of the address.
        assertNull(shortVideoRuleForAddress("youtube shorts"))
        assertNull(shortVideoRuleForAddress(""))
    }

    @Test
    fun `any page on the sites tells which player to look for`() {
        assertEquals(INSTAGRAM_REELS, webPlaceOf("instagram.com/")?.let(::shortVideoRuleForSite))
        assertEquals(YOUTUBE_SHORTS, webPlaceOf("m.youtube.com/watch?v=abc")?.let(::shortVideoRuleForSite))
        assertNull(webPlaceOf("example.com/reels/")?.let(::shortVideoRuleForSite))
    }

    @Test
    fun `a reel's overlay filling the page is a short video, a small one isn't`() {
        // As Instagram's site names each reel's overlay.
        val reel = SeenView("clipsoverlay4015639076690268187_24843372091", 0.9f)
        assertTrue(showsShortVideoPage(INSTAGRAM_REELS, listOf(reel)))
        assertFalse(showsShortVideoPage(INSTAGRAM_REELS, listOf(reel.copy(heightShare = 0.3f))))
        assertFalse(showsShortVideoPage(YOUTUBE_SHORTS, listOf(reel)))
        assertFalse(showsShortVideoPage(INSTAGRAM_REELS, listOf(SeenView("feed", 1f))))
    }

    @Test
    fun `only the apps whose switches are on are watched, never no app at all`() {
        assertEquals(INSTAGRAM_REELS.packages + SNAPCHAT_SPOTLIGHT.packages + BROWSERS.map { it.packageName }, watchedPackages(setOf(YOUTUBE_SHORTS.id), OWN))
        assertEquals(YOUTUBE_SHORTS.packages + INSTAGRAM_REELS.packages + SNAPCHAT_SPOTLIGHT.packages, watchedPackages(setOf(BROWSERS_ID), OWN))
        // Browsers alone have nothing to look for.
        assertEquals(setOf(OWN), watchedPackages(setOf(YOUTUBE_SHORTS.id, INSTAGRAM_REELS.id, SNAPCHAT_SPOTLIGHT.id), OWN))
    }

    private companion object {
        const val OWN = "app.still.launcher"
    }

    @Test
    fun `Snapchat Spotlight on the web is a short video, the rest of Snapchat isn't`() {
        assertEquals(SNAPCHAT_SPOTLIGHT, shortVideoRuleForAddress("https://www.snapchat.com/spotlight/W7_EDlXWTBiXAEEniNoMPwAAYZm"))
        assertEquals(SNAPCHAT_SPOTLIGHT, shortVideoRuleForAddress("snapchat.com/spotlight"))
        assertNull(shortVideoRuleForAddress("snapchat.com"))
        assertNull(shortVideoRuleForAddress("https://www.snapchat.com/add/someone"))
    }

    @Test
    fun `in Snapchat, Spotlight shows while its tab is the selected one`() {
        assertTrue(onShortVideoTab(SNAPCHAT_SPOTLIGHT, listOf("com.snapchat.android:id/ngs_spotlight_icon_container")))
        assertFalse(onShortVideoTab(SNAPCHAT_SPOTLIGHT, listOf("com.snapchat.android:id/ngs_chat_icon_container")))
        assertFalse(onShortVideoTab(SNAPCHAT_SPOTLIGHT, emptyList()))
        // Apps whose player has ids of its own have no tab rule.
        assertFalse(onShortVideoTab(YOUTUBE_SHORTS, listOf("com.google.android.youtube:id/ngs_spotlight_icon_container")))
    }
}
