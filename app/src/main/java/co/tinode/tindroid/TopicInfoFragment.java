package co.tinode.tindroid;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collection;

import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Topic Info fragment.
 */
public class TopicInfoFragment extends Fragment {

    private static final String TAG = "TopicInfoFragment";

    Topic<VCard,String,String> mTopic;
    private MembersAdapter mAdapter;

    public TopicInfoFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_info, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        mAdapter = new MembersAdapter();

        final Activity activity = getActivity();

        RecyclerView rv = (RecyclerView) activity.findViewById(R.id.groupMembers);
        rv.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        rv.setAdapter(mAdapter);
        rv.setNestedScrollingEnabled(false);

        // Log.d(TAG, "onActivityCreated");
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        String name = bundle.getString("topic");
        mTopic = Cache.getTinode().getTopic(name);


        final Activity activity = getActivity();
        AppCompatImageView avatar = (AppCompatImageView) activity.findViewById(R.id.imageAvatar);
        final TextView title = (TextView) activity.findViewById(R.id.topicTitle);
        final TextView subtitle = (TextView) activity.findViewById(R.id.topicSubtitle);
        final TextView address = (TextView) activity.findViewById(R.id.topicAddress);
        final TextView permissions = (TextView) activity.findViewById(R.id.permissions);

        mTopic.setListener(new Topic.Listener<VCard, String, String>() {
            @Override
            public void onSubsUpdated() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.resetContent();
                    }
                });
            }

            @Override
            public void onMetaDesc(Description ignored) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        permissions.setText(mTopic.getAccessMode().getMode());
                    }
                });
            }
        });


        VCard pub = mTopic.getPub();
        if (pub != null) {
            if (!TextUtils.isEmpty(pub.fn)) {
                title.setText(pub.fn);
                title.setTypeface(null, Typeface.NORMAL);
                title.setTextIsSelectable(true);
            } else {
                title.setText(R.string.placeholder_contact_title);
                title.setTypeface(null, Typeface.ITALIC);
                title.setTextIsSelectable(false);
            }
            final Bitmap bmp = pub.getBitmap();
            if (bmp != null) {
                avatar.setImageBitmap(bmp);
            }
        }
        String priv = mTopic.getPriv();
        if (!TextUtils.isEmpty(priv)) {
            subtitle.setText(priv);
            subtitle.setTypeface(null, Typeface.NORMAL);
            subtitle.setTextIsSelectable(true);
        } else {
            subtitle.setText(R.string.placeholder_private);
            subtitle.setTypeface(null, Typeface.ITALIC);
            subtitle.setTextIsSelectable(false);
        }

        address.setText(mTopic.getName());
        permissions.setText(mTopic.getAccessMode().getMode());

        final Switch muted = (Switch) activity.findViewById(R.id.switchMuted);
        muted.setChecked(mTopic.isMuted());
        muted.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                Log.d(TAG, "isChecke=" + isChecked + ", muted=" + mTopic.isMuted());
                if (mTopic.isMuted() != isChecked) {
                    try {
                        mTopic.updateMuted(isChecked);
                    } catch (NotConnectedException ignored) {
                        muted.setChecked(!isChecked);
                        Toast.makeText(activity, R.string.error_connection_failed, Toast.LENGTH_SHORT).show();
                    } catch(Exception ignored){
                        muted.setChecked(!isChecked);
                    }
                }
            }
        });

        mAdapter.resetContent();
    }

    @Override
    public void onPause() {
        super.onPause();

        mTopic.setListener(null);
    }

    private class MembersAdapter extends RecyclerView.Adapter<MemberViewHolder> {

        private Subscription<VCard,String>[] mItems;
        private int mItemCount;

        @SuppressWarnings("unchecked")
        MembersAdapter() {
            mItems = (Subscription<VCard,String>[]) new Subscription[8];
            mItemCount = 0;
        }

        /** Must be run on UI thread */
        void resetContent() {
            if (mTopic != null) {
                Collection<Subscription<VCard, String>> c = mTopic.getSubscriptions();
                if (c != null) {
                    mItemCount = c.size();
                    mItems = c.toArray(mItems);
                } else {
                    mItemCount = 0;
                }
                // Log.d(TAG, "resetContent got " + mItemCount + " items");
                notifyDataSetChanged();
            }
        }

        @Override
        public int getItemCount() {
            return mItemCount;
        }

        @Override
        public long getItemId(int i) {
            return StoredSubscription.getId(mItems[i]);
        }

        @Override
        public MemberViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // create a new view
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_member, parent, false);
            return new MemberViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final MemberViewHolder holder, int position) {
            final Subscription<VCard, String> sub = mItems[position];
            final StoredSubscription ss = (StoredSubscription) sub.getLocal();

            Bitmap bmp = null;
            if (sub.pub != null) {
                if (!TextUtils.isEmpty(sub.pub.fn)) {
                    holder.name.setText(sub.pub.fn);
                } else {
                    holder.name.setText(R.string.placeholder_contact_title);
                }
                bmp = sub.pub.getBitmap();
            } else {
                Log.d(TAG, "Pub is null for " + sub.user);
            }

            holder.contactPriv.setText(sub.priv);

            int i = 0;
            UiUtils.AccessModeLabel[] labels = UiUtils.accessModeLabels(sub.acs, ss.status);
            if (labels != null) {
                for (UiUtils.AccessModeLabel l : labels) {
                    holder.status[i].setText(l.nameId);
                    holder.status[i].setTextColor(l.color);
                    ((GradientDrawable) holder.status[i].getBackground()).setStroke(2, l.color);
                    holder.status[i++].setVisibility(View.VISIBLE);
                }
            }
            for (; i<holder.status.length; i++) {
                holder.status[i].setVisibility(View.GONE);
            }

            UiUtils.assignBitmap(getActivity(), holder.icon, bmp, R.drawable.ic_person_circle);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Click, pos=" + holder.getAdapterPosition());
                }
            });
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_topic_info, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView contactPriv;
        LinearLayout statusContainer;
        TextView[] status;
        AppCompatImageView icon;

        MemberViewHolder(View item) {
            super(item);

            name = (TextView) item.findViewById(android.R.id.text1);
            contactPriv = (TextView) item.findViewById(android.R.id.text2);
            statusContainer = (LinearLayout) item.findViewById(R.id.statusContainer);
            status = new TextView[statusContainer.getChildCount()];
            for (int i=0; i < status.length; i++) {
                status[i] = (TextView) statusContainer.getChildAt(i);
            }
            icon = (AppCompatImageView) item.findViewById(android.R.id.icon);
        }
    }
}
