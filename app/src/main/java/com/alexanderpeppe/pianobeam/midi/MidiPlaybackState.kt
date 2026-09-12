package com.alexanderpeppe.pianobeam.midi

/** Source-channel state, independent of live routing, volume, instrument overrides, and pedal mode. */
class MidiPlaybackState {
    class Channel {
        var program = 0
        var bankMsb = 0
        var bankLsb = 0
        var pendingBankMsb = 0
        var pendingBankLsb = 0
        val controllers = IntArray(120) { defaultControllerValue(it) }
        val notes = IntArray(128)
        var pitchBend = 8192
        var pressure = 0
        private var nrpnSelected = false
        // General MIDI defaults for pitch-bend sensitivity, fine tuning, and coarse tuning.
        private val parameters = mutableMapOf(0 to 256, 1 to 8192, 2 to 8192)

        fun parameterController(controller: Int, value: Int) {
            when (controller) {
                98, 99, 100, 101 -> {
                    controllers[controller] = value
                    nrpnSelected = controller == 98 || controller == 99
                }
                else -> {
                    val msb = controllers[if (nrpnSelected) 99 else 101]
                    val lsb = controllers[if (nrpnSelected) 98 else 100]
                    val parameter = (msb shl 7) or lsb
                    if (parameter == 16383) return
                    val key = parameter or (if (nrpnSelected) 16384 else 0)
                    val previous = parameters[key] ?: 0
                    parameters[key] = when (controller) {
                        6 -> (value shl 7) or (previous and 0x7F)
                        38 -> (previous and 0x3F80) or value
                        // Coarse tuning increments its MSB; pitch sensitivity uses cents 0..99.
                        96, 97 -> {
                            val step = if (controller == 96) 1 else -1
                            when (key) {
                                0 -> {
                                    val cents = ((previous ushr 7) * 100 + (previous and 0x7F) + step).coerceIn(0, 12799)
                                    ((cents / 100) shl 7) or (cents % 100)
                                }
                                2 -> (previous + step * 128).coerceIn(0, 16383)
                                else -> (previous + step).coerceIn(0, 16383)
                            }
                        }
                        else -> previous
                    }
                }
            }
        }

        fun parameterMessages(outputChannel: Int): List<ByteArray> = buildList {
            val cc = (0xB0 or (outputChannel - 1)).toByte()
            parameters.toSortedMap().forEach { (key, value) ->
                val nrpn = key >= 16384
                add(byteArrayOf(cc, (if (nrpn) 99 else 101).toByte(), ((key and 16383) ushr 7).toByte()))
                add(byteArrayOf(cc, (if (nrpn) 98 else 100).toByte(), (key and 0x7F).toByte()))
                add(byteArrayOf(cc, 6, (value ushr 7).toByte()))
                add(byteArrayOf(cc, 38, (value and 0x7F).toByte()))
            }
            // Restore both selectors, with the active family last, so subsequent data entry works.
            val selectors = if (nrpnSelected) listOf(101, 100, 99, 98) else listOf(99, 98, 101, 100)
            selectors.forEach { add(byteArrayOf(cc, it.toByte(), controllers[it].toByte())) }
        }

        fun programMessages(outputChannel: Int, overrideProgram: Int? = null): List<ByteArray> {
            val cc = (0xB0 or (outputChannel - 1)).toByte()
            return buildList {
                add(byteArrayOf(cc, 0, (if (overrideProgram == null) bankMsb else 0).toByte()))
                add(byteArrayOf(cc, 32, (if (overrideProgram == null) bankLsb else 0).toByte()))
                add(byteArrayOf((0xC0 or (outputChannel - 1)).toByte(), (overrideProgram ?: program).toByte()))
                if (overrideProgram == null && (pendingBankMsb != bankMsb || pendingBankLsb != bankLsb)) {
                    add(byteArrayOf(cc, 0, pendingBankMsb.toByte()))
                    add(byteArrayOf(cc, 32, pendingBankLsb.toByte()))
                }
            }
        }

        fun copy(): Channel = Channel().also {
            it.program = program
            it.bankMsb = bankMsb
            it.bankLsb = bankLsb
            it.pendingBankMsb = pendingBankMsb
            it.pendingBankLsb = pendingBankLsb
            controllers.copyInto(it.controllers)
            notes.copyInto(it.notes)
            it.pitchBend = pitchBend
            it.pressure = pressure
            it.nrpnSelected = nrpnSelected
            it.parameters.clear()
            it.parameters.putAll(parameters)
        }

        fun resetControllers() {
            // CC121 leaves the instrument, bank, volume, pan, effects and sound controls alone.
            for (controller in listOf(1, 2, 4, 11, 33, 34, 36, 43, 64, 65, 66, 67, 68, 69)) {
                controllers[controller] = defaultControllerValue(controller)
            }
            pitchBend = 8192
            pressure = 0
            for (controller in 98..101) controllers[controller] = 127
            nrpnSelected = false
        }
    }

    val channels: Map<Int, Channel> get() = channelStates
    private val channelStates = sortedMapOf<Int, Channel>()

    fun copy(): MidiPlaybackState = MidiPlaybackState().also { result ->
        channelStates.forEach { (channel, state) -> result.channelStates[channel] = state.copy() }
    }

    fun accept(data: ByteArray) {
        val status = data.firstOrNull()?.toInt()?.and(0xFF) ?: return
        if (status !in 0x80..0xEF || data.size < 2) return
        val channel = (status and 0x0F) + 1
        val state = channelStates.getOrPut(channel) { Channel() }
        val value1 = data[1].toInt() and 0x7F
        val value2 = data.getOrNull(2)?.toInt()?.and(0x7F) ?: 0
        when (status and 0xF0) {
            0x80 -> state.notes[value1] = (state.notes[value1] - 1).coerceAtLeast(0)
            0x90 -> if (value2 == 0) {
                state.notes[value1] = (state.notes[value1] - 1).coerceAtLeast(0)
            } else {
                state.notes[value1]++
            }
            0xB0 -> when (value1) {
                0 -> state.pendingBankMsb = value2
                32 -> state.pendingBankLsb = value2
                120, 123 -> state.notes.fill(0)
                121 -> state.resetControllers()
                6, 38, in 96..101 -> state.parameterController(value1, value2)
                in 0..119 -> state.controllers[value1] = value2
            }
            0xC0 -> {
                state.program = value1
                // Bank select only takes effect on a program change; retain later pending selects.
                state.bankMsb = state.pendingBankMsb
                state.bankLsb = state.pendingBankLsb
            }
            0xD0 -> state.pressure = value1
            0xE0 -> state.pitchBend = value1 or (value2 shl 7)
        }
    }

    /** Ordinary, persistent controllers only; parameter-selection/data-entry commands need ordering. */
    fun controllerMessages(sourceChannels: Collection<Int>, controllersByChannel: Map<Int, Set<Int>>): List<ByteArray> =
        buildList {
            sourceChannels.sorted().forEach { channel ->
                val state = channelStates[channel] ?: Channel()
                val status = 0xB0 or (channel - 1)
                val controllers = DefaultRestoredControllers + controllersByChannel[channel].orEmpty()
                controllers.sorted().filter(::isRestorableController).forEach { controller ->
                    add(byteArrayOf(status.toByte(), controller.toByte(), state.controllers[controller].toByte()))
                }
                if (controllersByChannel[channel].orEmpty().any { it == 6 || it == 38 || it in 96..101 }) {
                    addAll(state.parameterMessages(channel))
                }
                add(byteArrayOf((0xD0 or (channel - 1)).toByte(), state.pressure.toByte()))
                add(byteArrayOf((0xE0 or (channel - 1)).toByte(), (state.pitchBend and 0x7F).toByte(), (state.pitchBend ushr 7).toByte()))
            }
        }

    companion object {
        private val DefaultRestoredControllers = setOf(1, 7, 10, 11)

        fun isRestorableController(controller: Int): Boolean =
            controller in 1..119 && controller !in setOf(6, 32, 38, 64, 66, 67, 69, 84, 88, 96, 97, 98, 99, 100, 101)

        private fun defaultControllerValue(controller: Int): Int = when (controller) {
            7 -> 100
            8, 10, in 70..79 -> 64
            11 -> 127
            91 -> 40
            in 98..101 -> 127
            else -> 0
        }
    }
}

/** Sparse snapshots bound each seek to at most [checkpointInterval] input events after a binary search. */
class MidiSeekIndex(
    private val events: List<MidiFileParser.ScheduledMidiEvent>,
    private val checkpointInterval: Int = 1024
) {
    data class Position(val eventIndex: Int, val state: MidiPlaybackState)

    val sourceChannels: Set<Int>
    val controllersByChannel: Map<Int, Set<Int>>
    private val checkpoints = mutableListOf<MidiPlaybackState>()

    init {
        require(checkpointInterval > 0)
        val state = MidiPlaybackState()
        val controllers = mutableMapOf<Int, MutableSet<Int>>()
        checkpoints += state.copy()
        events.forEachIndexed { index, event ->
            state.accept(event.data)
            val status = event.data.firstOrNull()?.toInt()?.and(0xFF) ?: 0
            if (status in 0xB0..0xBF && event.data.size >= 3) {
                controllers.getOrPut((status and 0x0F) + 1) { mutableSetOf() } += event.data[1].toInt() and 0x7F
            }
            if ((index + 1) % checkpointInterval == 0) checkpoints += state.copy()
        }
        sourceChannels = state.channels.keys.toSet()
        controllersByChannel = controllers
    }

    /** State strictly before the destination: events exactly at the destination play normally. */
    fun positionAt(timeUs: Long): Position {
        var low = 0
        var high = events.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (events[middle].timeUs < timeUs) low = middle + 1 else high = middle
        }
        val checkpoint = low / checkpointInterval
        val state = checkpoints[checkpoint].copy()
        for (index in checkpoint * checkpointInterval until low) state.accept(events[index].data)
        return Position(low, state)
    }
}
