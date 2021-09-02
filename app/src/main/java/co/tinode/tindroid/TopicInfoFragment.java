package co.tinode.tindroid;

import android.Manifest;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collection;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import co.tinode.tindroid.account.ContactsManager;
import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.HorizontalListDivider;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Topic Info fragment: p2p or a group topic.
 */
public class TopicInfoFragment extends Fragment implements MessageActivity.DataSetChangeListener {

    private static final String TAG = "TopicInfoFragment";

    private static final int ACTION_REMOVE = 1;
    private static final int ACTION_BAN_MEMBER = 2;

    private ComTopic<VxCard> mTopic;
    private MembersAdapter mMembersAdapter;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private final ActivityResultLauncher<String[]> mRequestContactsPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {});

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_info, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.topic_settings);
        toolbar.setSubtitle(null);
        toolbar.setLogo(null);

        mMembersAdapter = new MembersAdapter();
        mFailureListener = new UiUtils.ToastFailureListener(activity);

        RecyclerView rv = view.findViewById(R.id.groupMembers);
        rv.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.VERTICAL, false));
        rv.addItemDecoration(new HorizontalListDivider(activity));
        rv.setAdapter(mMembersAdapter);
        rv.setNestedScrollingEnabled(false);

        // Set up listeners

        final SwitchCompat muted = view.findViewById(R.id.switchMuted);
        muted.setOnCheckedChangeListener((buttonView, isChecked) ->
                mTopic.updateMuted(isChecked).thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                        activity.runOnUiThread(() -> muted.setChecked(!isChecked));
                        if (err instanceof NotConnectedException) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    }
                }));

        final SwitchCompat archived = view.findViewById(R.id.switchArchived);
        archived.setOnCheckedChangeListener((buttonView, isChecked) ->
                mTopic.updateArchived(isChecked).thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                        activity.runOnUiThread(() -> archived.setChecked(!isChecked));
                        if (err instanceof NotConnectedException) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    }
                }));

        view.findViewById(R.id.permissions).setOnClickListener(v ->
                ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_PERMISSIONS, null,
                        true));

        view.findViewById(R.id.buttonAddMembers).setOnClickListener(v ->
                ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_EDIT_MEMBERS,
                        null, true));
    }

    @Override
    @SuppressWarnings("unchecked")
    // onResume sets up the form with values and views which do not change + sets up listeners.
    public void onResume() {
        super.onResume();

        final FragmentActivity activity = getActivity();
        final Bundle args = getArguments();

        if (activity == null || args == null) {
            return;
        }

        String name = args.getString("topic");
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);
        if (mTopic == null) {
            Log.d(TAG, "TopicInfo resumed with null topic.");
            activity.finish();
            return;
        }

        ((TextView)activity.findViewById(R.id.topicAddress)).setText(mTopic.getName());

        final View groupMembers = activity.findViewById(R.id.groupMembersWrapper);

        if (mTopic.isGrpType()) {
            // Group topic
            groupMembers.setVisibility(View.VISIBLE);

            Button button = activity.findViewById(R.id.buttonAddMembers);
            if (!mTopic.isSharer() && !mTopic.isManager()) {
                // FIXME: allow sharers to add members but not remove.
                // Disable and gray out "invite members" button because only admins can
                // invite group members.
                button.setEnabled(false);
                button.setVisibility(View.GONE);
            } else {
                button.setEnabled(true);
                button.setVisibility(View.VISIBLE);
            }
        } else {
            // P2P topic
            groupMembers.setVisibility(View.GONE);
        }

        notifyDataSetChanged();
    }

    // Confirmation dialog "Do you really want to do X?"
    //  uid - user to apply action to
    //  message_id - id of the string resource to use as an explanation.
    //  what - action to take on success, ACTION_*
    private void showConfirmationDialog(final String arg1, final String arg2,
                                        final String uid,
                                        int title_id, int message_id,
                                        final int what) {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.no, null);
        if (title_id != 0) {
            confirmBuilder.setTitle(title_id);
        }
        String message = activity.getString(message_id, arg1, arg2);
        confirmBuilder.setMessage(message);

        confirmBuilder.setPositiveButton(android.R.string.yes, (dialog, which) -> {
            PromisedReply<ServerMessage> response = null;
            switch (what) {
                case ACTION_REMOVE:
                    response = mTopic.eject(uid, false);
                    break;
                case ACTION_BAN_MEMBER:
                    response = mTopic.eject(uid, true);
                    break;
            }

            if (response != null) {
                response.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        Intent intent = new Intent(activity, ChatsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        activity.finish();
                        return null;
                    }
                }).thenCatch(mFailureListener);
            }
        });
        confirmBuilder.show();
    }

    // Dialog-menu with actions for individual subscribers, like "send message", "change permissions", "ban", etc.
    private void showMemberAction(final String topicTitle, final String userTitle, final String uid,
                                  final String mode) {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (Cache.getTinode().isMe(uid)) {
            return;
        }

        final String userTitleFixed = TextUtils.isEmpty(userTitle) ?
                activity.getString(R.string.placeholder_contact_title) :
                userTitle;
        final String topicTitleFixed = TextUtils.isEmpty(topicTitle) ?
                activity.getString(R.string.placeholder_topic_title) :
                topicTitle;

        AlertDialog.Builder actionBuilder = new AlertDialog.Builder(activity);
        final LinearLayout actions = (LinearLayout) View.inflate(activity, R.layout.dialog_member_actions, null);
        actionBuilder
                .setTitle(TextUtils.isEmpty(userTitle) ?
                        activity.getString(R.string.placeholder_contact_title) :
                        userTitle)
                .setView(actions)
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = actionBuilder.create();
        View.OnClickListener ocl = v -> {
            try {
                Intent intent;
                int id = v.getId();
                if (id == R.id.buttonViewProfile) {
                    if (UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS)) {
                        // This requires READ_CONTACTS permission
                        String lookupKey = ContactsManager.getLookupKey(activity.getContentResolver(), uid);
                        intent = new Intent(Intent.ACTION_VIEW,
                                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey));
                        if (intent.resolveActivity(activity.getPackageManager()) != null) {
                            startActivity(intent);
                        }
                    } else {
                        mRequestContactsPermissionsLauncher.launch(new String[]{Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS});
                        Toast.makeText(activity, R.string.some_permissions_missing, Toast.LENGTH_SHORT).show();
                    }
                } else if (id == R.id.buttonSendMessage) {
                    intent = new Intent(activity, MessageActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    intent.putExtra("topic", uid);
                    startActivity(intent);

                } else if (id == R.id.buttonPermissions) {
                    UiUtils.showEditPermissions(activity, mTopic, mode, uid,
                            UiUtils.ACTION_UPDATE_SUB, "O");

                } else if (id == R.id.buttonMakeOwner) {
                    mTopic.updateMode(uid, "+O").thenApply(null, mFailureListener);
                } else if (id == R.id.buttonRemove) {
                    showConfirmationDialog(userTitleFixed, topicTitleFixed, uid,
                            R.string.remove_from_group,
                            R.string.confirm_member_removal, ACTION_REMOVE);

                } else if (id == R.id.buttonBlock) {
                    showConfirmationDialog(userTitleFixed, topicTitleFixed, uid,
                            R.string.block,
                            R.string.confirm_member_ban, ACTION_BAN_MEMBER);
                }
            } catch (NotConnectedException ignored) {
                Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
            }

            dialog.dismiss();
        };
        actions.findViewById(R.id.buttonViewProfile).setOnClickListener(ocl);
        actions.findViewById(R.id.buttonSendMessage).setOnClickListener(ocl);
        if (mTopic.isOwner()) {
            actions.findViewById(R.id.buttonMakeOwner).setOnClickListener(ocl);
        } else {
            actions.findViewById(R.id.buttonMakeOwner).setVisibility(View.GONE);
        }
        if (mTopic.isManager()) {
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

    public void notifyDataSetChanged() {
        final FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        notifyContentChanged();

        if (mTopic.isGrpType()) {
            mMembersAdapter.resetContent();
        }
    }

    // Called when topic description is changed.
    private void notifyContentChanged() {
        final FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final AppCompatImageView avatar = activity.findViewById(R.id.imageAvatar);
        final TextView title = activity.findViewById(R.id.topicTitle);
        final TextView subtitle = activity.findViewById(R.id.topicSubtitle);
        final TextView description = activity.findViewById(R.id.topicDescription);

        VxCard pub = mTopic.getPub();
        if (pub != null && !TextUtils.isEmpty(pub.fn)) {
            title.setText(pub.fn);
            title.setTypeface(null, Typeface.NORMAL);
            title.setTextIsSelectable(true);
            description.setText(mTopic.getPub().note);
        } else {
            title.setText(R.string.placeholder_contact_title);
            title.setTypeface(null, Typeface.ITALIC);
            title.setTextIsSelectable(false);
        }

        // Trusted flags.
        activity.findViewById(R.id.verified).setVisibility(mTopic.isTrustedVerified() ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.staff).setVisibility(mTopic.isTrustedStaff() ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.danger).setVisibility(mTopic.isTrustedDanger() ? View.VISIBLE : View.GONE);

        final Bitmap bmp = pub != null ? pub.getBitmap() : null;
        if (bmp != null) {
            avatar.setImageDrawable(new RoundImageDrawable(getResources(), bmp));
        } else {
            avatar.setImageDrawable(
                    new LetterTileDrawable(requireContext())
                            .setIsCircular(true)
                            .setContactTypeAndColor(
                                    mTopic.getTopicType() == Topic.TopicType.P2P ?
                                            LetterTileDrawable.ContactType.PERSON :
                                            LetterTileDrawable.ContactType.GROUP)
                            .setLetterAndColor(pub != null ? pub.fn : null, mTopic.getName()));
        }

        PrivateType priv = mTopic.getPriv();
        if (priv != null && !TextUtils.isEmpty(priv.getComment())) {
            subtitle.setText(priv.getComment());
            subtitle.setTextIsSelectable(true);
            subtitle.setVisibility(View.VISIBLE);
        } else {
            subtitle.setVisibility(View.GONE);
        }

        ((SwitchCompat) activity.findViewById(R.id.switchMuted)).setChecked(mTopic.isMuted());
        ((SwitchCompat) activity.findViewById(R.id.switchArchived)).setChecked(mTopic.isArchived());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_edit, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }

            ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_GENERAL,
                    null, true);
            return true;
        }
        return false;
    }

    private static class MemberViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView extraInfo;
        final LinearLayout statusContainer;
        final TextView[] status;
        final ImageButton more;
        final AppCompatImageView icon;


        MemberViewHolder(View item) {
            super(item);

            name = item.findViewById(android.R.id.text1);
            extraInfo = item.findViewById(android.R.id.text2);
            statusContainer = item.findViewById(R.id.statusContainer);
            status = new TextView[statusContainer.getChildCount()];
            for (int i = 0; i < status.length; i++) {
                status[i] = (TextView) statusContainer.getChildAt(i);
            }
            more = item.findViewById(R.id.optionsMenu);
            icon = item.findViewById(android.R.id.icon);
        }
    }

    private class MembersAdapter extends RecyclerView.Adapter<MemberViewHolder> {

        private Subscription<VxCard, PrivateType>[] mItems;
        private int mItemCount;

        @SuppressWarnings("unchecked")
        MembersAdapter() {
            mItems = (Subscription<VxCard, PrivateType>[]) new Subscription[8];
            mItemCount = 0;
        }

        /**
         * Must be run on UI thread
         */
        void resetContent() {
            if (mTopic != null) {
                Collection<Subscription<VxCard, PrivateType>> c = mTopic.getSubscriptions();
                if (c != null) {
                    mItemCount = c.size();
                    mItems = c.toArray(mItems);
                } else {
                    mItemCount = 0;
                }

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
            final FragmentActivity activity = getActivity();
            if (activity == null) {
                return;
            }

            final Subscription<VxCard, PrivateType> sub = mItems[position];
            final StoredSubscription ss = (StoredSubscription) sub.getLocal();
            final boolean isMe = Cache.getTinode().isMe(sub.user);

            Bitmap bmp = null;
            String title = isMe ? activity.getString(R.string.current_user) : null;
            if (sub.pub != null) {
                if (title == null) {
                    title = !TextUtils.isEmpty(sub.pub.fn) ? sub.pub.fn :
                            activity.getString(R.string.placeholder_contact_title);
                }
                bmp = sub.pub.getBitmap();
            } else {
                Log.w(TAG, "Pub is null for " + sub.user);
            }
            holder.name.setText(title);
            holder.extraInfo.setText(sub.acs != null ? sub.acs.getMode() : "");

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

            holder.icon.setImageDrawable(UiUtils.avatarDrawable(activity, bmp,
                    sub.pub != null ? sub.pub.fn : null, sub.user));

            final View.OnClickListener action = v -> {
                int position1 = holder.getBindingAdapterPosition();
                final Subscription<VxCard, PrivateType> sub1 = mItems[position1];
                VxCard pub = mTopic.getPub();
                showMemberAction(pub != null ? pub.fn : null, holder.name.getText().toString(), sub1.user,
                        sub1.acs.getGiven());
            };

            holder.itemView.setOnClickListener(action);
            if (isMe) {
                holder.more.setVisibility(View.INVISIBLE);
            } else {
                holder.more.setVisibility(View.VISIBLE);
                holder.more.setOnClickListener(action);
            }
        }
    }
}
