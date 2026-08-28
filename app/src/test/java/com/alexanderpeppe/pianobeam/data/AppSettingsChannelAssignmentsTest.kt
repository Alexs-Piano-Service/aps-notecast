package com.alexanderpeppe.pianobeam.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsChannelAssignmentsTest {
    @Test
    fun routingDefaultsPreserveExistingBehavior() {
        val settings = AppSettings()

        assertEquals(1, settings.acousticPianoInputChannel)
        assertFalse(settings.mergeAllInstrumentsToPianoChannel)
        assertTrue(settings.channelAssignmentsForSong("song").isEmpty())
        assertEquals("", settings.channelAssignmentSignatureForSong("song"))
    }

    @Test
    fun assignmentIsStoredPerSongAndCanBeUpdated() {
        val settings = AppSettings()
            .withSongChannelAssignment("song-a", sourceChannel = 1, outputChannel = 2)
            .withSongChannelAssignment("song-b", sourceChannel = 1, outputChannel = 3)
            .withSongChannelAssignment("song-a", sourceChannel = 1, outputChannel = 4)

        assertEquals(mapOf(1 to 4), settings.channelAssignmentsForSong("song-a"))
        assertEquals(mapOf(1 to 3), settings.channelAssignmentsForSong("song-b"))
    }

    @Test
    fun identityAssignmentIsPreservedToOverrideAutomaticRouting() {
        val settings = AppSettings()
            .withSongChannelAssignment("song", sourceChannel = 5, outputChannel = 5)

        assertEquals(mapOf(5 to 5), settings.channelAssignmentsForSong("song"))
    }

    @Test
    fun nullOutputRemovesAssignmentAndEmptySongEntry() {
        val settings = AppSettings()
            .withSongChannelAssignment("song", sourceChannel = 1, outputChannel = 2)
            .withSongChannelAssignment("song", sourceChannel = 1, outputChannel = null)

        assertTrue(settings.channelAssignmentsForSong("song").isEmpty())
        assertTrue(settings.songChannelAssignments.none { it.songId == "song" })
    }

    @Test
    fun signatureAndStoredEntriesHaveDeterministicOrder() {
        val settings = AppSettings()
            .withSongChannelAssignment("z-song", sourceChannel = 9, outputChannel = 4)
            .withSongChannelAssignment("a-song", sourceChannel = 12, outputChannel = 3)
            .withSongChannelAssignment("a-song", sourceChannel = 2, outputChannel = 7)

        assertEquals(listOf("a-song", "z-song"), settings.songChannelAssignments.map { it.songId })
        assertEquals(listOf(2, 12), settings.channelAssignmentsForSong("a-song").keys.toList())
        assertEquals("2:7|12:3", settings.channelAssignmentSignatureForSong("a-song"))
    }

    @Test
    fun clearRemovesOnlyRequestedSongAssignments() {
        val settings = AppSettings()
            .withSongChannelAssignment("song-a", sourceChannel = 1, outputChannel = 2)
            .withSongChannelAssignment("song-b", sourceChannel = 3, outputChannel = 4)
            .withClearedSongChannelAssignments("song-a")

        assertTrue(settings.channelAssignmentsForSong("song-a").isEmpty())
        assertEquals(mapOf(3 to 4), settings.channelAssignmentsForSong("song-b"))
    }

    @Test
    fun blankSongAndInvalidChannelsAreRejectedWithoutMutation() {
        val settings = AppSettings()

        assertSame(settings, settings.withSongChannelAssignment("", 1, 2))
        assertSame(settings, settings.withSongChannelAssignment("song", 0, 2))
        assertSame(settings, settings.withSongChannelAssignment("song", 17, 2))
        assertSame(settings, settings.withSongChannelAssignment("song", 1, 0))
        assertSame(settings, settings.withSongChannelAssignment("song", 1, 17))
    }

    @Test
    fun nullAndBlankSongLookupsAreEmpty() {
        val settings = AppSettings()
            .withSongChannelAssignment("song", sourceChannel = 1, outputChannel = 2)

        assertTrue(settings.channelAssignmentsForSong(null).isEmpty())
        assertTrue(settings.channelAssignmentsForSong("").isEmpty())
        assertEquals("", settings.channelAssignmentSignatureForSong(null))
    }
}
