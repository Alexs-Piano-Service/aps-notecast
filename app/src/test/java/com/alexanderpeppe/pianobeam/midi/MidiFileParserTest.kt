package com.alexanderpeppe.pianobeam.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.Charset

class MidiFileParserTest {
    @Test
    fun endOfTrackPreservesThreeSecondsOfTrailingSilence() {
        val sequence = fixture("notecast_end_of_track_silence.mid")
        assertEquals(4_000_000L, sequence.durationUs)
        assertEquals(1_000_000L, sequence.events.last().timeUs)
        assertEquals(2, sequence.events.size)
    }

    @Test
    fun tempoChangesDuringTrailingSilenceAffectDuration() {
        val sequence = parse(track(
            0 to bytes(0x90, 60, 100),
            960 to bytes(0x80, 60, 0),
            960 to bytes(0xFF, 0x51, 3, 0x0F, 0x42, 0x40), // one second per quarter, at 2 seconds
            1920 to bytes(0xFF, 0x2F, 0)
        ))
        assertEquals(6_000_000L, sequence.durationUs)
        assertEquals(listOf(0L, 1_000_000L), sequence.events.map { it.timeUs })
    }

    @Test
    fun longestTrackIncludingConductorTrackDefinesDuration() {
        val sequence = parse(
            track(3840 to bytes(0xFF, 0x2F, 0)),
            track(0 to bytes(0x90, 60, 100), 960 to bytes(0x80, 60, 0), 0 to bytes(0xFF, 0x2F, 0))
        )
        assertEquals(4_000_000L, sequence.durationUs)
    }

    @Test
    fun silentFileStillHasItsIntendedDuration() {
        val sequence = parse(track(1920 to bytes(0xFF, 0x2F, 0)))
        assertTrue(sequence.events.isEmpty())
        assertEquals(2_000_000L, sequence.durationUs)
    }

    @Test
    fun smpteEndOfTrackUsesFrameTimingAndIgnoresTempo() {
        val sequence = parse(track(
            0 to bytes(0x90, 60, 100),
            1000 to bytes(0x80, 60, 0),
            1000 to bytes(0xFF, 0x51, 3, 0x0F, 0x42, 0x40),
            2000 to bytes(0xFF, 0x2F, 0)
        ), division = 0xE728) // 25 frames/second, 40 ticks/frame
        assertEquals(4_000_000L, sequence.durationUs)
        assertEquals(1_000_000L, sequence.events.last().timeUs)
    }

    @Test
    fun missingEndOfTrackUsesLastParsedTickAndEndOfTrackStopsParsing() {
        assertEquals(1_000_000L, parse(track(960 to bytes(0x90, 60, 100))).durationUs)
        val sequence = parse(track(960 to bytes(0xFF, 0x2F, 0), 960 to bytes(0x90, 60, 100)))
        assertEquals(1_000_000L, sequence.durationUs)
        assertTrue(sequence.events.isEmpty())
    }

    @Test
    fun utf8AccentedTitleDecodesWithoutMojibake() {
        assertEquals("Café", fixture("notecast_utf8_title.mid").title)
        assertTitle("Café — 日本語 🎹", Charsets.UTF_8)
        assertTitle("Âme", Charsets.UTF_8)
    }

    @Test
    fun legacyLatin1Windows1252AndShiftJisTitlesStillDecode() {
        assertTitle("Café", Charsets.ISO_8859_1)
        assertTitle("Grüße", Charsets.ISO_8859_1)
        assertTitle("éà", Charsets.ISO_8859_1)
        assertTitle("“Café” — étude", Charset.forName("windows-1252"))
        assertTitle("—Piano", Charset.forName("windows-1252"))
        assertTitle("日本語の歌", Charset.forName("Shift_JIS"))
        assertTitle("東京", Charset.forName("Shift_JIS"))
        assertTitle("ピアノ", Charset.forName("Shift_JIS"))
    }

    @Test
    fun utf8DecoderRejectsMalformedInputBeforeLegacyFallback() {
        val sequence = parse(track(0 to meta(3, bytes(0xC3, 0x28)), 0 to bytes(0xFF, 0x2F, 0)))
        assertEquals("Ã(", sequence.title)
    }

    @Test
    fun decodingAlsoAppliesToTrackAndInstrumentLabels() {
        val sequence = parse(
            track(0 to meta(3, "Song".toByteArray()), 0 to bytes(0xFF, 0x2F, 0)),
            track(
                0 to meta(3, "Café".toByteArray()),
                0 to meta(4, "日本語".toByteArray(Charset.forName("Shift_JIS"))),
                0 to bytes(0x93, 60, 100), 960 to bytes(0x83, 60, 0), 0 to bytes(0xFF, 0x2F, 0)
            )
        )
        assertEquals(listOf("Café"), sequence.channelMetadata.getValue(4).trackNames)
        assertEquals(listOf("日本語"), sequence.channelMetadata.getValue(4).metaInstrumentNames)
    }

    private fun assertTitle(title: String, charset: Charset) {
        val sequence = parse(track(0 to meta(3, title.toByteArray(charset)), 0 to bytes(0xFF, 0x2F, 0)))
        assertEquals(charset.name(), title, sequence.title)
    }

    private fun parse(vararg tracks: ByteArray, division: Int = 480): MidiFileParser.MidiSequence {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeBytes("MThd")
            output.writeInt(6)
            output.writeShort(if (tracks.size == 1) 0 else 1)
            output.writeShort(tracks.size)
            output.writeShort(division)
            tracks.forEach { track ->
                output.writeBytes("MTrk")
                output.writeInt(track.size)
                output.write(track)
            }
        }
        return MidiFileParser.parse(buffer.toByteArray(), "Fallback.mid")
    }

    private fun track(vararg events: Pair<Int, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        events.forEach { (delta, data) ->
            output.write(vlq(delta))
            output.write(data)
        }
    }.toByteArray()

    private fun meta(type: Int, data: ByteArray) = bytes(0xFF, type) + vlq(data.size) + data
    private fun vlq(value: Int): ByteArray {
        var remaining = value ushr 7
        val result = mutableListOf((value and 0x7F).toByte())
        while (remaining > 0) {
            result.add(0, ((remaining and 0x7F) or 0x80).toByte())
            remaining = remaining ushr 7
        }
        return result.toByteArray()
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun fixture(name: String) = MidiFileParser.parse(
        javaClass.getResourceAsStream("/midi/$name")!!.use { it.readBytes() }, name
    )
}
