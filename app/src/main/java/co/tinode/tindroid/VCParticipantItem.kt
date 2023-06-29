package co.tinode.tindroid

import android.util.Log
import android.view.View
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.util.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VCParticipantItem(
        private val room: Room,
        private val participant: Participant,
) {
    companion object {
        private val TAG: String? = VCParticipantItem::class.simpleName
    }
    private var boundVideoTrack: VideoTrack? = null
    private var coroutineScope: CoroutineScope? = null

    fun initialize(holder: VCParticipantsAdapter.RecyclerViewHolder) {
        holder.mParticipant = this
    }
    private fun ensureCoroutineScope() {
        if (coroutineScope == null) {
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
    }

    fun bind(holder: VCParticipantsAdapter.RecyclerViewHolder) {

        ensureCoroutineScope()
        coroutineScope?.launch {
            participant::identity.flow.collect { identity ->
                holder.mIdentityText.text = identity
            }
        }
        coroutineScope?.launch {
            participant::isSpeaking.flow.collect { isSpeaking ->
                if (isSpeaking) {
                    showFocus(holder)
                } else {
                    hideFocus(holder)
                }
            }
        }
        coroutineScope?.launch {
            participant::audioTracks.flow
                    .flatMapLatest { tracks ->
                        val audioTrack = tracks.firstOrNull()?.first
                        if (audioTrack != null) {
                            audioTrack::muted.flow
                        } else {
                            flowOf(true)
                        }
                    }
                    .collect { muted ->
                        holder.mMuteIndicator.visibility = if (muted) View.VISIBLE else View.INVISIBLE
                    }
        }
        coroutineScope?.launch {
            participant::connectionQuality.flow
                    .collect { quality ->
                        holder.mConnectionQuality.visibility =
                                if (quality == ConnectionQuality.POOR) View.VISIBLE else View.INVISIBLE
                    }
        }

        // observe videoTracks changes.
        val videoTrackPubFlow = participant::videoTracks.flow
                .map { participant to it }
                .flatMapLatest { (participant, videoTracks) ->
                    // Prioritize any screenshare streams.
                    val trackPublication = participant.getTrackPublication(Track.Source.SCREEN_SHARE)
                            ?: participant.getTrackPublication(Track.Source.CAMERA)
                            ?: videoTracks.firstOrNull()?.first

                    flowOf(trackPublication)
                }

        coroutineScope?.launch {
            val videoTrackFlow = videoTrackPubFlow
                    .flatMapLatestOrNull { pub -> pub::track.flow }

            // Configure video view with track
            launch {
                videoTrackFlow.collectLatest { videoTrack ->
                    setupVideoIfNeeded(videoTrack as? VideoTrack, holder)
                }
            }

            // TODO: for local participants, mirror camera if using front camera.
            //if (participant == room.localParticipant) {
            //    launch {
            //        videoTrackFlow
            //                .flatMapLatestOrNull { track -> (track as LocalVideoTrack)::options.flow }
            //                .collectLatest { options ->
            //                    holder.mRenderer.setMirror(options?.position == CameraPosition.FRONT)
            //                }
            //    }
            //}
        }

        // Handle muted changes
        coroutineScope?.launch {
            videoTrackPubFlow
                    .flatMapLatestOrNull { pub -> pub::muted.flow }
                    .collectLatest { muted ->
                        holder.mRenderer.visibleOrInvisible(!(muted ?: true))
                    }
        }
        val existingTrack = getVideoTrack()
        if (existingTrack != null) {
            setupVideoIfNeeded(existingTrack, holder)
        }
    }

    private fun getVideoTrack(): VideoTrack? {
        return participant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
    }

    private fun setupVideoIfNeeded(videoTrack: VideoTrack?, holder: VCParticipantsAdapter.RecyclerViewHolder) {
        if (boundVideoTrack == videoTrack) {
            return
        }
        boundVideoTrack?.removeRenderer(holder.mRenderer)
        boundVideoTrack = videoTrack
        Log.i(TAG, "Adding renderer to " + videoTrack)
        videoTrack?.addRenderer(holder.mRenderer)
    }

    fun unbind(holder: VCParticipantsAdapter.RecyclerViewHolder) {
        coroutineScope?.cancel()
        coroutineScope = null
        //super.unbind(viewHolder)
        boundVideoTrack?.removeRenderer(holder.mRenderer)
        boundVideoTrack = null
    }
}

private fun View.visibleOrGone(visible: Boolean) {
    visibility = if (visible) {
        View.VISIBLE
    } else {
        View.GONE
    }
}

private fun View.visibleOrInvisible(visible: Boolean) {
    visibility = if (visible) {
        View.VISIBLE
    } else {
        View.INVISIBLE
    }
}

private fun showFocus(holder: VCParticipantsAdapter.RecyclerViewHolder) {
    holder.mSpeakingIndicator.visibility = View.VISIBLE
}

private fun hideFocus(holder: VCParticipantsAdapter.RecyclerViewHolder) {
    holder.mSpeakingIndicator.visibility = View.INVISIBLE
}

private inline fun <T, R> Flow<T?>.flatMapLatestOrNull(
        crossinline transform: suspend (value: T) -> Flow<R>
): Flow<R?> {
    return flatMapLatest {
        if (it == null) {
            flowOf(null)
        } else {
            transform(it)
        }
    }
}