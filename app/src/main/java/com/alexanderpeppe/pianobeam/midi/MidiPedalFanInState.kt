package com.alexanderpeppe.pianobeam.midi

/**
 * Aggregates pedal controller values when one or more source channels share an output channel.
 *
 * Source values are retained independently from routing and output-value mode. Callers provide the
 * complete current source-to-destination map whenever output is requested, which makes rerouting
 * and fan-in deterministic. This class is intentionally not synchronized; playback should own it
 * from a single serialized dispatcher.
 */
class MidiPedalFanInState {
    enum class ValueMode {
        Binary,
        Continuous
    }

    data class OutputMessage(
        val destinationChannel: Int,
        val controller: Int,
        val value: Int
    ) {
        init {
            require(destinationChannel in MIDI_CHANNELS)
            require(controller in SupportedControllers)
            require(value in MIDI_VALUES)
        }

        fun toMidiData(): ByteArray =
            byteArrayOf(
                (CONTROL_CHANGE_STATUS or (destinationChannel - 1)).toByte(),
                controller.toByte(),
                value.toByte()
            )
    }

    private val rawValues = Array(MIDI_CHANNEL_COUNT) { IntArray(SupportedControllers.size) }
    private val lastOutputValues = Array(MIDI_CHANNEL_COUNT) { IntArray(SupportedControllers.size) }
    private val hasLastOutputValue = Array(MIDI_CHANNEL_COUNT) { BooleanArray(SupportedControllers.size) }

    /**
     * Records one source event and emits only destination values that changed.
     *
     * [destinationsBySource] is the complete current routing map, not just the route for
     * [sourceChannel]. If a previously emitted destination is absent from the map, a zero value is
     * emitted before any current-destination messages so a held pedal cannot be stranded there.
     */
    fun update(
        sourceChannel: Int,
        controller: Int,
        rawValue: Int,
        destinationsBySource: Map<Int, List<Int>>,
        valueMode: ValueMode
    ): List<OutputMessage> {
        if (!updateWithoutEmitting(sourceChannel, controller, rawValue)) return emptyList()
        return outputMessages(
            destinationsBySource = destinationsBySource,
            valueMode = valueMode,
            controllerIndexes = intArrayOf(controllerIndex(controller)),
            forceCurrentDestinations = false
        )
    }

    /** Records a source value without changing destination output bookkeeping or emitting data. */
    fun updateWithoutEmitting(sourceChannel: Int, controller: Int, rawValue: Int): Boolean {
        val sourceIndex = sourceChannel - 1
        val controllerIndex = controllerIndex(controller)
        if (sourceIndex !in rawValues.indices || controllerIndex < 0 || rawValue !in MIDI_VALUES) {
            return false
        }
        rawValues[sourceIndex][controllerIndex] = rawValue
        return true
    }

    /** Alias for [updateWithoutEmitting], intended for reconstructing state before a seek. */
    fun prime(sourceChannel: Int, controller: Int, rawValue: Int): Boolean =
        updateWithoutEmitting(sourceChannel, controller, rawValue)

    /**
     * Recomputes every supported controller and emits only values that differ from the last output.
     * This is useful when routing or value mode changes without a new source pedal event.
     */
    fun changedCurrentStateMessages(
        destinationsBySource: Map<Int, List<Int>>,
        valueMode: ValueMode
    ): List<OutputMessage> =
        outputMessages(
            destinationsBySource = destinationsBySource,
            valueMode = valueMode,
            controllerIndexes = ALL_CONTROLLER_INDEXES,
            forceCurrentDestinations = false
        )

    /**
     * Emits the current aggregate for every supported controller on every current destination,
     * even when it matches the last emitted value. Use after an external pedal release/panic for
     * pause-resume, seek, or routing restoration.
     */
    fun forcedCurrentStateMessages(
        destinationsBySource: Map<Int, List<Int>>,
        valueMode: ValueMode
    ): List<OutputMessage> =
        outputMessages(
            destinationsBySource = destinationsBySource,
            valueMode = valueMode,
            controllerIndexes = ALL_CONTROLLER_INDEXES,
            forceCurrentDestinations = true
        )

    fun rawValue(sourceChannel: Int, controller: Int): Int? {
        val sourceIndex = sourceChannel - 1
        val controllerIndex = controllerIndex(controller)
        if (sourceIndex !in rawValues.indices || controllerIndex < 0) return null
        return rawValues[sourceIndex][controllerIndex]
    }

    /** Clears both retained source values and destination-output history without emitting data. */
    fun clear() {
        rawValues.forEach { it.fill(0) }
        lastOutputValues.forEach { it.fill(0) }
        hasLastOutputValue.forEach { it.fill(false) }
    }

    /** Alias for [clear]. */
    fun reset() = clear()

    private fun outputMessages(
        destinationsBySource: Map<Int, List<Int>>,
        valueMode: ValueMode,
        controllerIndexes: IntArray,
        forceCurrentDestinations: Boolean
    ): List<OutputMessage> {
        val routes = normalizedRoutes(destinationsBySource)
        val currentDestinations = routes.values.flatten().toSortedSet()
        val previousDestinations = MIDI_CHANNELS.filter { destinationChannel ->
            controllerIndexes.any { controllerIndex ->
                hasLastOutputValue[destinationChannel - 1][controllerIndex]
            }
        }
        val removedDestinations = previousDestinations
            .filterNot(currentDestinations::contains)
            .sorted()

        return buildList {
            if (!forceCurrentDestinations) {
                removedDestinations.forEach { destinationChannel ->
                    controllerIndexes.forEach { controllerIndex ->
                        val destinationIndex = destinationChannel - 1
                        if (
                            hasLastOutputValue[destinationIndex][controllerIndex] &&
                            lastOutputValues[destinationIndex][controllerIndex] != 0
                        ) {
                            add(
                                OutputMessage(
                                    destinationChannel = destinationChannel,
                                    controller = SupportedControllers[controllerIndex],
                                    value = 0
                                )
                            )
                        }
                        hasLastOutputValue[destinationIndex][controllerIndex] = false
                        lastOutputValues[destinationIndex][controllerIndex] = 0
                    }
                }
            }

            currentDestinations.forEach { destinationChannel ->
                controllerIndexes.forEach { controllerIndex ->
                    val destinationIndex = destinationChannel - 1
                    val aggregate = aggregateValue(
                        destinationChannel = destinationChannel,
                        controllerIndex = controllerIndex,
                        routes = routes,
                        valueMode = valueMode
                    )
                    val changed = !hasLastOutputValue[destinationIndex][controllerIndex] && aggregate != 0 ||
                        hasLastOutputValue[destinationIndex][controllerIndex] &&
                        lastOutputValues[destinationIndex][controllerIndex] != aggregate
                    if (forceCurrentDestinations || changed) {
                        add(
                            OutputMessage(
                                destinationChannel = destinationChannel,
                                controller = SupportedControllers[controllerIndex],
                                value = aggregate
                            )
                        )
                    }
                    hasLastOutputValue[destinationIndex][controllerIndex] = true
                    lastOutputValues[destinationIndex][controllerIndex] = aggregate
                }
            }
        }
    }

    private fun aggregateValue(
        destinationChannel: Int,
        controllerIndex: Int,
        routes: Map<Int, List<Int>>,
        valueMode: ValueMode
    ): Int {
        val maximumRawValue = routes.entries.maxOfOrNull { (sourceChannel, destinations) ->
            if (destinationChannel in destinations) {
                rawValues[sourceChannel - 1][controllerIndex]
            } else {
                0
            }
        } ?: 0
        return when (valueMode) {
            ValueMode.Binary -> if (maximumRawValue >= BINARY_ON_THRESHOLD) MIDI_VALUE_MAX else 0
            ValueMode.Continuous -> maximumRawValue
        }
    }

    private fun normalizedRoutes(destinationsBySource: Map<Int, List<Int>>): Map<Int, List<Int>> =
        destinationsBySource.entries
            .asSequence()
            .filter { (sourceChannel, _) -> sourceChannel in MIDI_CHANNELS }
            .map { (sourceChannel, destinations) ->
                sourceChannel to destinations
                    .asSequence()
                    .filter(MIDI_CHANNELS::contains)
                    .distinct()
                    .sorted()
                    .toList()
            }
            .filter { (_, destinations) -> destinations.isNotEmpty() }
            .toMap()

    private fun controllerIndex(controller: Int): Int = SupportedControllers.indexOf(controller)

    companion object {
        const val BINARY_ON_THRESHOLD = 64
        val SupportedControllers: List<Int> = listOf(64, 66, 67, 69)

        private const val MIDI_CHANNEL_COUNT = 16
        private const val MIDI_VALUE_MAX = 127
        private const val CONTROL_CHANGE_STATUS = 0xB0
        private val MIDI_CHANNELS = 1..MIDI_CHANNEL_COUNT
        private val MIDI_VALUES = 0..MIDI_VALUE_MAX
        private val ALL_CONTROLLER_INDEXES = SupportedControllers.indices.toList().toIntArray()
    }
}
