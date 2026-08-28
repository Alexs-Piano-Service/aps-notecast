package com.alexanderpeppe.pianobeam.midi

import com.alexanderpeppe.pianobeam.data.PlaybackChannelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiChannelRouterTest {
    @Test
    fun configurablePianoTargetIsUsedForChannelOne() {
        assertEquals(
            6,
            MidiChannelRouter.outputChannel(
                sourceChannel = 1,
                acousticPianoInputChannel = 6
            )
        )
    }

    @Test
    fun channelTwoFoldsOnlyWhenEnabled() {
        assertEquals(
            7,
            MidiChannelRouter.outputChannel(
                sourceChannel = 2,
                acousticPianoInputChannel = 7,
                foldChannel2IntoPianoChannel = true
            )
        )
        assertEquals(
            2,
            MidiChannelRouter.outputChannel(
                sourceChannel = 2,
                acousticPianoInputChannel = 7,
                foldChannel2IntoPianoChannel = false
            )
        )
    }

    @Test
    fun onlyAcousticGrandProgramIsAutomaticallyRouted() {
        val acousticGrand = channelInfo(channel = 5, programs = listOf(0))
        val brightAcoustic = channelInfo(channel = 5, programs = listOf(1))

        assertEquals(
            3,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = acousticGrand,
                acousticPianoInputChannel = 3
            )
        )
        assertEquals(
            5,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = brightAcoustic,
                acousticPianoInputChannel = 3
            )
        )
    }

    @Test
    fun acousticGrandNameMatchIsExactApartFromCaseAndWhitespace() {
        val exactName = channelInfo(channel = 5, instrumentName = "  aCoUsTiC gRaNd PiAnO  ")
        val longerName = channelInfo(channel = 5, instrumentName = "Acoustic Grand Piano Layer")

        assertEquals(
            4,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = exactName,
                acousticPianoInputChannel = 4
            )
        )
        assertEquals(
            5,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = longerName,
                acousticPianoInputChannel = 4
            )
        )
    }

    @Test
    fun acousticGrandMetadataDoesNotAutomaticallyRoutePercussionChannel() {
        assertEquals(
            10,
            MidiChannelRouter.outputChannel(
                sourceChannel = 10,
                channelInfo = channelInfo(channel = 10, programs = listOf(0)),
                acousticPianoInputChannel = 4
            )
        )
    }

    @Test
    fun instrumentOverrideTakesPriorityOverDetectedInstrument() {
        val detectedAcousticGrand = channelInfo(channel = 5, programs = listOf(0))

        assertEquals(
            5,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = detectedAcousticGrand,
                instrumentOverrideProgram = 1,
                acousticPianoInputChannel = 8
            )
        )
        assertEquals(
            8,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = channelInfo(channel = 5, programs = listOf(40)),
                instrumentOverrideProgram = 0,
                acousticPianoInputChannel = 8
            )
        )
    }

    @Test
    fun explicitIdentityAssignmentPreventsAutomaticRouting() {
        assertEquals(
            5,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = channelInfo(channel = 5, programs = listOf(0)),
                explicitAssignments = mapOf(5 to 5),
                acousticPianoInputChannel = 2
            )
        )
    }

    @Test
    fun explicitReassignmentPrecedesAutomaticAndFoldRouting() {
        assertEquals(
            9,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = channelInfo(channel = 5, programs = listOf(0)),
                explicitAssignments = mapOf(5 to 9),
                acousticPianoInputChannel = 2
            )
        )
        assertEquals(
            12,
            MidiChannelRouter.outputChannel(
                sourceChannel = 2,
                explicitAssignments = mapOf(2 to 12),
                acousticPianoInputChannel = 2,
                foldChannel2IntoPianoChannel = true
            )
        )
    }

    @Test
    fun explicitAssignmentsAreResolvedDirectlyRatherThanTransitively() {
        val assignments = mapOf(1 to 2, 2 to 3)

        assertEquals(
            2,
            MidiChannelRouter.outputChannel(
                sourceChannel = 1,
                explicitAssignments = assignments
            )
        )
        assertEquals(
            3,
            MidiChannelRouter.outputChannel(
                sourceChannel = 2,
                explicitAssignments = assignments
            )
        )
    }

    @Test
    fun mergeAllHasHighestPriorityForEveryChannel() {
        for (sourceChannel in 1..16) {
            assertEquals(
                "source channel $sourceChannel",
                6,
                MidiChannelRouter.outputChannel(
                    sourceChannel = sourceChannel,
                    explicitAssignments = mapOf(sourceChannel to sourceChannel),
                    acousticPianoInputChannel = 6,
                    foldChannel2IntoPianoChannel = false,
                    mergeAllInstrumentsToPianoChannel = true
                )
            )
        }
    }

    @Test
    fun mergeAllIncludesGeneralMidiPercussionChannelTen() {
        assertEquals(
            4,
            MidiChannelRouter.outputChannel(
                sourceChannel = 10,
                explicitAssignments = mapOf(10 to 12),
                acousticPianoInputChannel = 4,
                mergeAllInstrumentsToPianoChannel = true
            )
        )
        assertTrue(
            MidiChannelRouter.isMergedAsAcousticPiano(
                sourceChannel = 10,
                mergeAllInstrumentsToPianoChannel = true
            )
        )
    }

    @Test
    fun mergeAllMarksEveryValidSourceAsLikelyPiano() {
        assertTrue(
            MidiChannelRouter.isLikelyPianoSource(
                sourceChannel = 10,
                channelInfo = null,
                instrumentOverrideProgram = null,
                explicitAssignments = emptyMap(),
                acousticPianoInputChannel = 3,
                mergeAllInstrumentsToPianoChannel = true
            )
        )
        assertFalse(
            MidiChannelRouter.isLikelyPianoSource(
                sourceChannel = 17,
                channelInfo = null,
                instrumentOverrideProgram = 0,
                explicitAssignments = emptyMap(),
                acousticPianoInputChannel = 3,
                mergeAllInstrumentsToPianoChannel = true
            )
        )
    }

    @Test
    fun nonPianoOverrideStopsDetectedPartFromBeingClassifiedAsPiano() {
        assertFalse(
            MidiChannelRouter.isLikelyPianoSource(
                sourceChannel = 5,
                channelInfo = channelInfo(channel = 5, programs = listOf(0)),
                instrumentOverrideProgram = 40,
                explicitAssignments = emptyMap(),
                acousticPianoInputChannel = 3,
                mergeAllInstrumentsToPianoChannel = false
            )
        )
    }

    @Test
    fun explicitAssignmentToSelectedPianoInputIsClassifiedAsPiano() {
        assertTrue(
            MidiChannelRouter.isLikelyPianoSource(
                sourceChannel = 5,
                channelInfo = channelInfo(channel = 5, programs = listOf(40)),
                instrumentOverrideProgram = 40,
                explicitAssignments = mapOf(5 to 3),
                acousticPianoInputChannel = 3,
                mergeAllInstrumentsToPianoChannel = false
            )
        )
    }

    @Test
    fun invalidSourceChannelsPassThroughUnchanged() {
        assertEquals(
            0,
            MidiChannelRouter.outputChannel(
                sourceChannel = 0,
                explicitAssignments = mapOf(0 to 4),
                mergeAllInstrumentsToPianoChannel = true
            )
        )
        assertEquals(
            17,
            MidiChannelRouter.outputChannel(
                sourceChannel = 17,
                instrumentOverrideProgram = 0,
                mergeAllInstrumentsToPianoChannel = true
            )
        )
    }

    @Test
    fun invalidExplicitDestinationsAreIgnored() {
        assertEquals(
            7,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                channelInfo = channelInfo(channel = 5, programs = listOf(0)),
                explicitAssignments = mapOf(5 to 0),
                acousticPianoInputChannel = 7
            )
        )
        assertEquals(
            5,
            MidiChannelRouter.outputChannel(
                sourceChannel = 5,
                explicitAssignments = mapOf(5 to 17),
                acousticPianoInputChannel = 7
            )
        )
    }

    @Test
    fun invalidPianoTargetsAreClampedToMidiRange() {
        assertEquals(
            1,
            MidiChannelRouter.outputChannel(
                sourceChannel = 1,
                acousticPianoInputChannel = 0
            )
        )
        assertEquals(
            16,
            MidiChannelRouter.outputChannel(
                sourceChannel = 1,
                acousticPianoInputChannel = 17
            )
        )
    }

    private fun channelInfo(
        channel: Int,
        programs: List<Int> = emptyList(),
        instrumentName: String? = null
    ): PlaybackChannelInfo =
        PlaybackChannelInfo(
            channel = channel,
            instrumentName = instrumentName,
            programNumbers = programs
        )
}
