package co.tinode.tindroid;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
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
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

import static android.app.Activity.RESULT_OK;

/**
 * Topic Info fragment: p2p or a group topic.
 */
public class TopicInfoFragment extends Fragment {

    private static final String TAG = "TopicInfoFragment";

    private static final int ACTION_REMOVE = 4;
    private static final int ACTION_BAN = 5;

    ComTopic<VxCard> mTopic;
    private MembersAdapter mAdapter;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

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

        mFailureListener = new UiUtils.ToastFailureListener(activity);

        RecyclerView rv = activity.findViewById(R.id.groupMembers);
        rv.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        rv.setAdapter(mAdapter);
        rv.setNestedScrollingEnabled(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        String name = bundle.getString("topic");
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);

        final Activity activity = getActivity();
        final TextView title = activity.findViewById(R.id.topicTitle);
        final TextView subtitle = activity.findViewById(R.id.topicSubtitle);
        final TextView address = activity.findViewById(R.id.topicAddress);
        final Switch muted = activity.findViewById(R.id.switchMuted);

        final View groupMembersCard = activity.findViewById(R.id.groupMembersCard);
        final View defaultPermissionsCard = activity.findViewById(R.id.defaultPermissionsCard);
        final View uploadAvatarButton = activity.findViewById(R.id.uploadAvatar);

        // Launch edit dialog when title or subtitle is clicked.
        final View.OnClickListener l = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditTopicText();
            }
        };
        if (mTopic.isAdmin() || mTopic.isOwner()) {
            title.setOnClickListener(l);

            uploadAvatarButton.setVisibility(View.VISIBLE);
            uploadAvatarButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.requestAvatar(TopicInfoFragment.this);
                }
            });
        } else {
            uploadAvatarButton.setVisibility(View.GONE);
        }
        subtitle.setOnClickListener(l);

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

        Acs am = mTopic.getAccessMode();

        if (mTopic.getTopicType() == Topic.TopicType.GRP) {
            groupMembersCard.setVisibility(View.VISIBLE);

            activity.findViewById(R.id.singleUserPermissions).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.p2pPermissions).setVisibility(View.GONE);
            TextView myPermissions = activity.findViewById(R.id.permissions);
            myPermissions.setText(mTopic.getAccessMode().getWant());
            myPermissions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.showEditPermissions(activity, mTopic, mTopic.getAccessMode().getWant(), null,
                            UiUtils.ACTION_UPDATE_SELF_SUB, false);
                }
            });

            Button button = activity.findViewById(R.id.buttonLeaveGroup);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTopic.isOwner()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage(R.string.owner_cannot_unsubscribe)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setCancelable(true)
                                .setNegativeButton(android.R.string.ok, null)
                                .show();
                    } else {
                        try {
                            mTopic.delete().thenApply(null, mFailureListener);
                        } catch (NotConnectedException ignored) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) {
                            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

            button = activity.findViewById(R.id.buttonAddMembers);
            if (!mTopic.isAdmin() && !mTopic.isOwner()) {
                // Disable and gray out "invite members" button because only admins can
                // invite group members.
                button.setEnabled(false);
                button.setAlpha(0.5f);
            } else {
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_EDIT_MEMBERS,
                                true, null);
                    }
                });
            }

            mAdapter.resetContent();
        } else {
            groupMembersCard.setVisibility(View.GONE);

            activity.findViewById(R.id.singleUserPermissions).setVisibility(View.GONE);
            activity.findViewById(R.id.p2pPermissions).setVisibility(View.VISIBLE);

            TextView myPermissions = activity.findViewById(R.id.userOne);
            myPermissions.setText(mTopic.getAccessMode().getWant());
            myPermissions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.showEditPermissions(activity, mTopic, mTopic.getAccessMode().getWant(), null,
                            UiUtils.ACTION_UPDATE_SELF_SUB, true);
                }
            });

            VxCard two = mTopic.getPub();
            ((TextView) activity.findViewById(R.id.userTwoLabel)).setText(two != null && two.fn != null ?
                    two.fn : mTopic.getName());
            TextView otherPermissions = activity.findViewById(R.id.userTwo);
            final String permissionTwo = mTopic.getSubscription(mTopic.getName()).acs.getGiven();
            otherPermissions.setText(permissionTwo);
            otherPermissions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.showEditPermissions(activity, mTopic, permissionTwo, mTopic.getName(),
                            UiUtils.ACTION_UPDATE_SUB, true);
                }
            });
        }

        address.setText(mTopic.getName());

        if (am != null && (am.isAdmin() || am.isOwner()) && !mTopic.isP2PType()) {
            defaultPermissionsCard.setVisibility(View.VISIBLE);
            final TextView auth = activity.findViewById(R.id.authPermissions);
            auth.setText(mTopic.getAuthAcsStr());
            final TextView anon = activity.findViewById(R.id.anonPermissions);
            anon.setText(mTopic.getAnonAcsStr());
            auth.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.showEditPermissions(activity, mTopic, mTopic.getAuthAcsStr(), null,
                            UiUtils.ACTION_UPDATE_AUTH, true);
                }
            });
            anon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.showEditPermissions(activity, mTopic, mTopic.getAnonAcsStr(), null,
                            UiUtils.ACTION_UPDATE_ANON, true);
                }
            });
        } else {
            defaultPermissionsCard.setVisibility(View.GONE);
        }

        notifyContentChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    // Dialog for editing pub.fn and priv
    private void showEditTopicText() {
        VxCard pub = mTopic.getPub();
        final String title = pub == null ? null : pub.fn;
        final PrivateType priv = mTopic.getPriv();
        final Activity activity = getActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = View.inflate(builder.getContext(), R.layout.dialog_edit_group, null);
        builder.setView(editor).setTitle(R.string.edit_topic);

        final EditText titleEditor = editor.findViewById(R.id.editTitle);
        final EditText subtitleEditor = editor.findViewById(R.id.editPrivate);
        if (mTopic.isAdmin()) {
            if (!TextUtils.isEmpty(title)) {
                titleEditor.setText(title);
            }
        } else {
            editor.findViewById(R.id.editTitleWrapper).setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(priv.getComment())) {
            subtitleEditor.setText(priv.getComment());
        }

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newTitle = null;
                if (mTopic.isAdmin()) {
                    newTitle = titleEditor.getText().toString();
                }
                String newPriv = subtitleEditor.getText().toString();
                UiUtils.updateTitle(getActivity(), mTopic, newTitle, newPriv);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    // Confirmation dialog "Do you really want to do X?"
    private void showConfirmationDialog(final String topicTitle,
                                        final String title, final String uid, int message_id, final int what) {
        final Activity activity = getActivity();
        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.no, null);
        String message = activity.getString(message_id, title, topicTitle);
        confirmBuilder.setMessage(message);

        confirmBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    switch (what) {
                        case ACTION_REMOVE:
                            mTopic.eject(uid, false).thenApply(null, mFailureListener);
                            break;
                        case ACTION_BAN:
                            mTopic.eject(uid, true).thenApply(null, mFailureListener);
                            break;
                    }
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        confirmBuilder.show();
    }

    // Dialog-menu with actions for individual subscribers, like "send message", "change permissions", "ban", etc.
    private void showMemberAction(final String topicTitle, final String title, final String uid, final String mode) {
        final Activity activity = getActivity();
        final String titleFixed = TextUtils.isEmpty(title) ?
                activity.getString(R.string.placeholder_contact_title) :
                title;
        final String topicTitleFixed = TextUtils.isEmpty(topicTitle) ?
                activity.getString(R.string.placeholder_topic_title) :
                topicTitle;

        AlertDialog.Builder actionBuilder = new AlertDialog.Builder(activity);
        final LinearLayout actions = (LinearLayout) View.inflate(activity, R.layout.dialog_member_actions, null);
        actionBuilder
                .setTitle(TextUtils.isEmpty(title) ?
                        activity.getString(R.string.placeholder_contact_title) :
                        title)
                .setView(actions)
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = actionBuilder.create();
        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    switch (v.getId()) {
                        case R.id.buttonViewProfile:
                            break;
                        case R.id.buttonSendMessage:
                            break;
                        case R.id.buttonPermissions:
                            UiUtils.showEditPermissions(activity, mTopic, mode, uid, UiUtils.ACTION_UPDATE_SUB, true);
                            break;
                        case R.id.buttonMakeOwner:
                            mTopic.updateMode(uid, "+O").thenApply(null, mFailureListener);
                            break;
                        case R.id.buttonRemove: {
                            showConfirmationDialog(topicTitleFixed, titleFixed, uid,
                                    R.string.confirm_member_removal, ACTION_REMOVE);
                            break;
                        }
                        case R.id.buttonBlock: {
                            showConfirmationDialog(topicTitleFixed, titleFixed, uid,
                                    R.string.confirm_member_ban, ACTION_BAN);
                            break;
                        }
                    }
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }

                dialog.dismiss();
            }
        };
        actions.findViewById(R.id.buttonViewProfile).setOnClickListener(ocl);
        actions.findViewById(R.id.buttonSendMessage).setOnClickListener(ocl);
        if (mTopic.isOwner()) {
            actions.findViewById(R.id.buttonMakeOwner).setOnClickListener(ocl);
        } else {
            actions.findViewById(R.id.buttonMakeOwner).setVisibility(View.GONE);
        }
        if (mTopic.isAdmin() || mTopic.isOwner()) {
            actions.findViewById(R.id.buttonPermissions).setOnClickListener(ocl);
            actions.findViewById(R.id.buttonRemove).setOnClickListener(ocl);
            actions.findViewById(R.id.buttonBlock).setOnClickListener(ocl);
        } else {
            actions.findViewById(R.id.buttonPermissions).setVisibility(View.GONE);
            actions.findViewById(R.id.buttonRemove).setVisibility(View.GONE);
            actions.findViewById(R.id.buttonBlock).setVisibility(View.GONE);
        }
        dialog.show();
    }

    void notifyDataSetChanged() {
        mAdapter.resetContent();
    }

    void notifyContentChanged() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final AppCompatImageView avatar = activity.findViewById(R.id.imageAvatar);
        final TextView title = activity.findViewById(R.id.topicTitle);
        final TextView subtitle = activity.findViewById(R.id.topicSubtitle);
        final TextView permissions = activity.findViewById(R.id.permissions);
        final Switch muted = activity.findViewById(R.id.switchMuted);
        final TextView auth = activity.findViewById(R.id.authPermissions);
        final TextView anon = activity.findViewById(R.id.anonPermissions);

        VxCard pub = mTopic.getPub();
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
                avatar.setImageDrawable(new RoundImageDrawable(bmp));
            } else {
                avatar.setImageDrawable(
                        new LetterTileDrawable(requireContext())
                                .setIsCircular(true)
                                .setContactTypeAndColor(
                                        mTopic.getTopicType() == Topic.TopicType.P2P ?
                                            LetterTileDrawable.TYPE_PERSON :
                                                LetterTileDrawable.TYPE_GROUP)
                                .setLetterAndColor(pub.fn, mTopic.getName()));
            }
        } else {
            avatar.setImageDrawable(
                    new LetterTileDrawable(requireContext()).setIsCircular(true));
        }

        PrivateType priv = mTopic.getPriv();
        if (priv != null && !TextUtils.isEmpty(priv.getComment())) {
            subtitle.setText(priv.getComment());
            subtitle.setTypeface(null, Typeface.NORMAL);
            subtitle.setTextIsSelectable(true);
        } else {
            subtitle.setText(R.string.placeholder_private);
            subtitle.setTypeface(null, Typeface.ITALIC);
            subtitle.setTextIsSelectable(false);
        }

        muted.setChecked(mTopic.isMuted());
        permissions.setText(mTopic.getAccessMode().getMode());
        auth.setText(mTopic.getAuthAcsStr());
        anon.setText(mTopic.getAnonAcsStr());
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        // Execution has to be delayed because the topic is not yet subscribed:
        // The image selection activity was on top while MessageActivity was paused. It just got
        // unpaused and did not have time to re-subscribe.
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            final MessageActivity activity = (MessageActivity) getActivity();
            if (activity != null) {
                activity.submitForExecution(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.updateAvatar(activity, mTopic, data);
                    }
                });
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // inflater.inflate(R.menu.menu_topic_info, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView extraInfo;
        LinearLayout statusContainer;
        TextView[] status;
        AppCompatImageView icon;

        MemberViewHolder(View item) {
            super(item);

            name = item.findViewById(android.R.id.text1);
            extraInfo = item.findViewById(android.R.id.text2);
            statusContainer = item.findViewById(R.id.statusContainer);
            status = new TextView[statusContainer.getChildCount()];
            for (int i = 0; i < status.length; i++) {
                status[i] = (TextView) statusContainer.getChildAt(i);
            }
            icon = item.findViewById(android.R.id.icon);
        }
    }

    private class MembersAdapter extends RecyclerView.Adapter<MemberViewHolder> {

        private Subscription<VxCard,PrivateType>[] mItems;
        private int mItemCount;

        @SuppressWarnings("unchecked")
        MembersAdapter() {
            mItems = (Subscription<VxCard,PrivateType>[]) new Subscription[8];
            mItemCount = 0;
        }

        /**
         * Must be run on UI thread
         */
        void resetContent() {
            if (mTopic != null) {
                Collection<Subscription<VxCard,PrivateType>> c = mTopic.getSubscriptions();
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

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // create a new view
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_member, parent, false);
            return new MemberViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final MemberViewHolder holder, int position) {
            final Subscription<VxCard,PrivateType> sub = mItems[position];
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
            holder.extraInfo.setText(sub.acs.getMode());

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

            UiUtils.assignBitmap(getActivity(), holder.icon, bmp,
                    sub.pub != null ? sub.pub.fn : null, sub.user);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = holder.getAdapterPosition();
                    final Subscription<VxCard,PrivateType> sub = mItems[position];
                    VxCard pub = mTopic.getPub();
                    showMemberAction(pub != null ? pub.fn : null, holder.name.getText().toString(), sub.user,
                            sub.acs.getGiven());
                    Log.d(TAG, "Click on '" + sub.user + "', pos=" + holder.getAdapterPosition());
                }
            });
        }
    }
}
