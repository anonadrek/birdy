package se.birdy.data.observation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersistedMediaPathTest {
    @Test
    fun rebase_rewrites_stale_container_uuid() {
        val stored =
            "/var/mobile/Containers/Data/Application/" +
                "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA/Documents/observations/abc.jpg"
        val current =
            "/var/mobile/Containers/Data/Application/" +
                "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB/Documents"
        assertEquals(
            "$current/observations/abc.jpg",
            rebaseDocumentsPath(stored, current),
        )
    }

    @Test
    fun rebase_is_noop_when_container_already_matches() {
        val current =
            "/var/mobile/Containers/Data/Application/" +
                "CCCCCCCC-CCCC-CCCC-CCCC-CCCCCCCCCCCC/Documents"
        val stored = "$current/observations/abc.jpg"
        assertEquals(stored, rebaseDocumentsPath(stored, current))
    }

    @Test
    fun rebase_handles_audio_clips_under_documents() {
        val stored = "/old-uuid/Documents/audio/123.opus"
        val current = "/new-uuid/Documents"
        assertEquals("$current/audio/123.opus", rebaseDocumentsPath(stored, current))
    }

    @Test
    fun rebase_trims_trailing_slash_on_documents_dir() {
        val stored = "/old/Documents/observations/x.jpg"
        assertEquals(
            "/new/Documents/observations/x.jpg",
            rebaseDocumentsPath(stored, "/new/Documents/"),
        )
    }

    @Test
    fun rebase_returns_null_for_android_filesDir_paths() {
        assertNull(
            rebaseDocumentsPath(
                "/data/user/0/se.birdy.android/files/observations/x.jpg",
                "/irrelevant",
            ),
        )
    }

    @Test
    fun rebase_returns_null_when_documents_dir_is_blank() {
        assertNull(
            rebaseDocumentsPath(
                "/var/mobile/Containers/Data/Application/OLD/Documents/observations/x.jpg",
                "",
            ),
        )
    }

    @Test
    fun rebase_returns_null_for_documents_dir_itself_without_suffix() {
        assertNull(rebaseDocumentsPath("/old/UUID/Documents/", "/new/Documents"))
    }

    @Test
    fun resolve_on_jvm_is_identity_including_ios_shaped_paths() {
        assertEquals("/tmp/x.jpg", resolvePersistedMediaPath("/tmp/x.jpg"))
        val iosShaped =
            "/var/mobile/Containers/Data/Application/OLD/Documents/observations/x.jpg"
        assertEquals(iosShaped, resolvePersistedMediaPath(iosShaped))
        assertEquals("observations/x.jpg", resolvePersistedMediaPath("observations/x.jpg"))
    }
}
