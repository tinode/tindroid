package co.tinode.tindroid;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collection;

import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Topic Info fragment.
 */
public class TopicInfoFragment extends Fragment {

    private static final String TAG = "TopicInfoFragment";

    Topic<VCard, String, String> mTopic;
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
        final View groupMembersCard = activity.findViewById(R.id.groupMembersCard);
        final View defaultPermissionsCard = activity.findViewById(R.id.defaultPermissionsCard);

        VCard pub = mTopic.getPub();
        final String titleText;
        if (pub != null) {
            if (!TextUtils.isEmpty(pub.fn)) {
                titleText = pub.fn;
                title.setText(pub.fn);
                title.setTypeface(null, Typeface.NORMAL);
                title.setTextIsSelectable(true);
            } else {
                titleText = null;
                title.setText(R.string.placeholder_contact_title);
                title.setTypeface(null, Typeface.ITALIC);
                title.setTextIsSelectable(false);
            }
            final Bitmap bmp = pub.getBitmap();
            if (bmp != null) {
                avatar.setImageBitmap(bmp);
            }
        } else {
            titleText = null;
        }

        String priv = mTopic.getPriv();
        final String subtitleText;
        if (!TextUtils.isEmpty(priv)) {
            subtitleText = priv;
            subtitle.setText(priv);
            subtitle.setTypeface(null, Typeface.NORMAL);
            subtitle.setTextIsSelectable(true);
        } else {
            subtitle.setText(R.string.placeholder_private);
            subtitle.setTypeface(null, Typeface.ITALIC);
            subtitle.setTextIsSelectable(false);
            subtitleText = null;
        }
        // Launch edit dialog when title or subtitle is clicked.
        final View.OnClickListener l = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditTopicText();
            }
        };
        if (mTopic.isAdmin()) {
            title.setOnClickListener(l);
        }
        subtitle.setOnClickListener(l);

        final Switch muted = (Switch) activity.findViewById(R.id.switchMuted);
        muted.setChecked(mTopic.isMuted());
        muted.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                Log.d(TAG, "isChecked=" + isChecked + ", muted=" + mTopic.isMuted());
                try {
                    Log.d(TAG, "Setting muted to " + isChecked);
                    mTopic.updateMuted(isChecked);
                } catch (NotConnectedException ignored) {
                    Log.d(TAG, "Offline - not changed");
                    muted.setChecked(!isChecked);
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                    Log.d(TAG, "Generic exception", ignored);
                    muted.setChecked(!isChecked);
                }
            }
        });

        if (mTopic.getTopicType() == Topic.TopicType.GRP) {
            groupMembersCard.setVisibility(View.VISIBLE);

            if (!mTopic.isAdmin()) {
                // Disable and gray out "invite members" button because only admins can
                // invite group members.
                Button button = (Button) activity.findViewById(R.id.buttonAddMembers);
                button.setEnabled(false);
                button.setAlpha(0.5f);
            }

            mAdapter.resetContent();
        } else {
            groupMembersCard.setVisibility(View.GONE);
        }

        address.setText(mTopic.getName());
        Acs am = mTopic.getAccessMode();
        permissions.setText(am.getMode());

        if (am.isAdmin()) {
            defaultPermissionsCard.setVisibility(View.VISIBLE);
            final TextView auth = (TextView) activity.findViewById(R.id.authPermissions);
            final TextView anon = (TextView) activity.findViewById(R.id.anonPermissions);
            auth.setText(mTopic.getAuthAcsStr());
            anon.setText(mTopic.getAnonAcsStr());
        } else {
            defaultPermissionsCard.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    // Dialog for editing pub.fn and priv
    private void showEditTopicText() {
        VCard pub = mTopic.getPub();
        final String title = pub == null ? null : pub.fn;
        final String priv = mTopic.getPriv();
        final Activity activity = getActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = activity.getLayoutInflater().inflate(R.layout.dialog_edit_group, null);
        builder.setView(editor).setTitle(R.string.edit_topic);

        final EditText titleEditor = (EditText) editor.findViewById(R.id.editTitle);
        final EditText subtitleEditor = (EditText) editor.findViewById(R.id.editPrivate);
        if (mTopic.isAdmin()) {
            if (!TextUtils.isEmpty(title)) {
                titleEditor.setText(title);
            }
        } else {
            editor.findViewById(R.id.editTitleWrapper).setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(priv)) {
            subtitleEditor.setText(priv);
        }

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                VCard pub = null;
                if (mTopic.isAdmin()) {
                    String newTitle = titleEditor.getText().toString();
                    if (!newTitle.equals(title)) {
                        pub = mTopic.getPub();
                        if (pub != null) {
                            pub = pub.copy();
                        } else {
                            pub = new VCard();
                        }

                        pub.fn = newTitle;
                    }
                }
                ;
                String newPriv = subtitleEditor.getText().toString();
                if (newPriv.equals(priv)) {
                    newPriv = null;
                }
                if (pub != null || newPriv != null) {
                    try {
                        mTopic.setDescription(pub, newPriv);
                    } catch (NotConnectedException ignored) {
                        Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {
                        Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                    }
                }
                Log.d(TAG, "OK");
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    void notifyDataSetChanged() {
        mAdapter.resetContent();
    }

    void notifyContentChanged() {
        final Activity activity = getActivity();

        final TextView title = (TextView) activity.findViewById(R.id.topicTitle);
        final TextView subtitle = (TextView) activity.findViewById(R.id.topicSubtitle);
        final TextView permissions = (TextView) activity.findViewById(R.id.permissions);
        final TextView auth = (TextView) activity.findViewById(R.id.authPermissions);
        final TextView anon = (TextView) activity.findViewById(R.id.anonPermissions);

        VCard pub = mTopic.getPub();
        title.setText(pub != null && pub.fn != null ? pub.fn : "");
        subtitle.setText(mTopic.getPriv());
        permissions.setText(mTopic.getAccessMode().getMode());
        auth.setText(mTopic.getAuthAcsStr());
        anon.setText(mTopic.getAnonAcsStr());
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
            for (int i = 0; i < status.length; i++) {
                status[i] = (TextView) statusContainer.getChildAt(i);
            }
            icon = (AppCompatImageView) item.findViewById(android.R.id.icon);
        }
    }

    private class MembersAdapter extends RecyclerView.Adapter<MemberViewHolder> {

        private Subscription<VCard, String>[] mItems;
        private int mItemCount;

        @SuppressWarnings("unchecked")
        MembersAdapter() {
            mItems = (Subscription<VCard, String>[]) new Subscription[8];
            mItemCount = 0;
        }

        /**
         * Must be run on UI thread
         */
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
            final Activity activity = getActivity();

            Bitmap bmp = null;
            String title = Cache.getTinode().isMe(sub.user) ? activity.getString(R.string.current_user) : null;
            if (sub.pub != null) {
                if (title == null) {
                    title = !TextUtils.isEmpty(sub.pub.fn) ? sub.pub.fn :
                            activity.getString(R.string.placeholder_contact_title);
                }
                bmp = sub.pub.getBitmap();
            } else {
                Log.d(TAG, "Pub is null for " + sub.user);
            }
            holder.name.setText(title);
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
            for (; i < holder.status.length; i++) {
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
}
