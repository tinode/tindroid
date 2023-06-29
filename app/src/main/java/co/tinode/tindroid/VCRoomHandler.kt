package co.tinode.tindroid

import android.app.Application
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalScreencastVideoTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VCRoomHandler(
        val url: String,
        val token: String,
        application: Application,
        listener: VCListener
) {
    interface VCListener {
        fun beforeConnect(room: Room)
        fun onParticipants(participants: List<@JvmSuppressWildcards Participant>)
    }
    companion object {
        private val TAG: String? = VCRoomHandler::class.simpleName
    }

    val audioHandler = AudioSwitchHandler(application)
    val room = LiveKit.create(
            appContext = application,
            options = RoomOptions(adaptiveStream = true, dynacast = true),
            overrides = LiveKitOverrides(
                    audioHandler = audioHandler
            )
    )

    val participants = room::remoteParticipants.flow
            .map { remoteParticipants ->
                listOf<Participant>(room.localParticipant) +
                        remoteParticipants
                                .keys
                                .sortedBy { it }
                                .mapNotNull { remoteParticipants[it] }
            }

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val mutableError = MutableStateFlow<Throwable?>(null)
    val error = mutableError.hide()

    private val mutablePrimarySpeaker = MutableStateFlow<Participant?>(null)
    val primarySpeaker: StateFlow<Participant?> = mutablePrimarySpeaker

    val activeSpeakers = room::activeSpeakers.flow

    private var localScreencastTrack: LocalScreencastVideoTrack? = null

    // Emits a string whenever a data message is received.
    private val mutableDataReceived = MutableSharedFlow<String>()
    val dataReceived = mutableDataReceived

    // Whether other participants are allowed to subscribe to this participant's tracks.
    private val mutablePermissionAllowed = MutableStateFlow(true)
    val permissionAllowed = mutablePermissionAllowed.hide()

    init {
        coroutineScope.launch {
            // Collect any errors.
            launch {
                error.collect { Log.i(TAG, it.toString()) }
            }

            // Handle any changes in speakers.
            launch {
                combine(participants, activeSpeakers) { participants, speakers -> participants to speakers }
                        .collect { (participantsList, speakers) ->
                            handlePrimarySpeaker(
                                    participantsList,
                                    speakers,
                                    room
                            )
                        }
            }

            // Handle room events.
            launch {
                room.events.collect {
                    when (it) {
                        is RoomEvent.FailedToConnect -> mutableError.value = it.error
                        is RoomEvent.DataReceived -> {
                            val identity = it.participant?.identity ?: "server"
                            val message = it.data.toString(Charsets.UTF_8)
                            mutableDataReceived.emit("$identity: $message")
                        }

                        is RoomEvent.TrackSubscribed -> {
                            launch { collectTrackStats(it) }
                        }

                        else -> {
                            Log.i(TAG, "Room event: $it")
                        }
                    }
                }
            }

            launch {
                participants.collect {
                    listener.onParticipants(it)
                }
            }

            listener.beforeConnect(room)
            connectToRoom()
        }
    }

    private suspend fun collectTrackStats(event: RoomEvent.TrackSubscribed) {
        val pub = event.publication
        while (true) {
            delay(10000)
            if (pub.subscribed) {
                Log.i(TAG, "${pub.sid} - subscribed")
            }
        }

    }

    private suspend fun connectToRoom() {
        try {
            room.connect(
                    url = url,
                    token = token,
            )

            // Create and publish audio/video tracks
            val localParticipant = room.localParticipant
            localParticipant.setMicrophoneEnabled(true)
            localParticipant.setCameraEnabled(true)

            // Update the speaker
            handlePrimarySpeaker(emptyList(), emptyList(), room)
        } catch (e: Throwable) {
            mutableError.value = e
        }
    }

    private fun handlePrimarySpeaker(participantsList: List<Participant>, speakers: List<Participant>, room: Room?) {

        var speaker = mutablePrimarySpeaker.value

        // If speaker is local participant (due to defaults),
        // attempt to find another remote speaker to replace with.
        if (speaker is LocalParticipant) {
            val remoteSpeaker = participantsList
                    .filterIsInstance<RemoteParticipant>() // Try not to display local participant as speaker.
                    .firstOrNull()

            if (remoteSpeaker != null) {
                speaker = remoteSpeaker
            }
        }

        // If previous primary speaker leaves
        if (!participantsList.contains(speaker)) {
            // Default to another person in room, or local participant.
            speaker = participantsList.filterIsInstance<RemoteParticipant>()
                    .firstOrNull()
                    ?: room?.localParticipant
        }

        if (speakers.isNotEmpty() && !speakers.contains(speaker)) {
            val remoteSpeaker = speakers
                    .filterIsInstance<RemoteParticipant>() // Try not to display local participant as speaker.
                    .firstOrNull()

            if (remoteSpeaker != null) {
                speaker = remoteSpeaker
            }
        }

        mutablePrimarySpeaker.value = speaker
    }

    fun close() {
        room.disconnect()
        room.release()
        coroutineScope.cancel()
    }

    fun setMicEnabled(enabled: Boolean) {
        coroutineScope.launch {
            room.localParticipant.setMicrophoneEnabled(enabled)
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        coroutineScope.launch {
            room.localParticipant.setCameraEnabled(enabled)
        }
    }

    fun flipCamera() {
        val videoTrack = room.localParticipant.getTrackPublication(Track.Source.CAMERA)
                ?.track as? LocalVideoTrack
                ?: return

        val newPosition = when (videoTrack.options.position) {
            CameraPosition.FRONT -> CameraPosition.BACK
            CameraPosition.BACK -> CameraPosition.FRONT
            else -> null
        }

        videoTrack.switchCamera(position = newPosition)
    }

    fun toggleSubscriptionPermissions() {
        mutablePermissionAllowed.value = !mutablePermissionAllowed.value
        room.localParticipant.setTrackSubscriptionPermissions(mutablePermissionAllowed.value)
    }

    // Debug functions
    fun simulateMigration() {
        room.sendSimulateScenario(Room.SimulateScenario.MIGRATION)
    }

    fun simulateNodeFailure() {
        room.sendSimulateScenario(Room.SimulateScenario.NODE_FAILURE)
    }

    fun reconnect() {
        Log.i(TAG, "Reconnecting.")
        mutablePrimarySpeaker.value = null
        room.disconnect()
        coroutineScope.launch {
            connectToRoom()
        }
    }
}

private fun <T> MutableStateFlow<T>.hide(): StateFlow<T> = this
private fun <T> Flow<T>.hide(): Flow<T> = this