package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.livekit.android.renderer.TextureViewRenderer;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.Participant;

public class VCParticipantsAdapter extends RecyclerView.Adapter<VCParticipantsAdapter.RecyclerViewHolder> {
    private static final String TAG = "VCParticipantsAdapter";
    private Context mContext;

    private Room mRoom;

    private List<Participant> mParticipants;

    VCParticipantsAdapter(Context c) {
        mContext = c;
    }

    public void setRoom(Room r) {
        mRoom = r;
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate Layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.vc_participant_item, parent, false);

        DisplayMetrics displaymetrics = new DisplayMetrics();
        ((Activity)mContext).getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

        RecyclerViewHolder holder = new RecyclerViewHolder(view);
        holder.mRenderer.getLayoutParams().height = displaymetrics.heightPixels / 2;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewHolder holder, int position) {
        Log.v(TAG, "Attaching VC participant at position " + position);
        Participant p = mParticipants.get(position);
        VCParticipantItem item = new VCParticipantItem(mRoom, p);
        item.initialize(holder);
        item.bind(holder);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerViewHolder holder) {
        Log.v(TAG, "Detaching " + holder.toString());
        if (holder.mParticipant != null) {
            holder.mParticipant.unbind(holder);
        }
        holder.mParticipant = null;
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public int getItemCount() {
        return mParticipants != null ? mParticipants.size() : 0;
    }

    public void update(List<Participant> participants) {
        mParticipants = participants;
    }

    public class RecyclerViewHolder extends RecyclerView.ViewHolder {
        VCParticipantItem mParticipant;

        TextureViewRenderer mRenderer;
        ImageView mConnectionQuality;
        ImageView mSpeakingIndicator;
        ImageView mMuteIndicator;
        TextView mIdentityText;

        public RecyclerViewHolder(@NonNull View itemView) {
            super(itemView);

            mRenderer = itemView.findViewById(R.id.renderer);
            mConnectionQuality = itemView.findViewById(R.id.connection_quality);
            mSpeakingIndicator = itemView.findViewById(R.id.speaking_indicator);
            mMuteIndicator = itemView.findViewById(R.id.mute_indicator);
            mIdentityText = itemView.findViewById(R.id.identity_text);

            mRoom.initVideoRenderer(mRenderer);
        }
    }
}
