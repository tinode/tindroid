package co.tinode.tindroid;

import android.Manifest;

import android.annotation.SuppressLint;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Collection;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import co.tinode.tindroid.account.ContactsManager;
import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.HorizontalListDivider;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Topic Info fragment: slf, p2p or a group topic.
 */
public class TopicInfoFragment extends Fragment implements MenuProvider, MessageActivity.DataSetChangeListener {

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
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final FragmentActivity activity = requireActivity();

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.topic_settings);
        toolbar.setSubtitle(null);
        toolbar.setLogo(null);

        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        mMembersAdapter = new MembersAdapter();
        mFailureListener = new UiUtils.ToastFailureListener(activity);

        RecyclerView rv = view.findViewById(R.id.groupMembers);
        rv.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.VERTICAL, false));
        rv.addItemDecoration(new HorizontalListDivider(activity));
        rv.setAdapter(mMembersAdapter);
        rv.setNestedScrollingEnabled(false);

        // Set up listeners

        final SwitchCompat muted = view.findViewById(R.id.switchMuted);
        muted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mTopic == null) {
                return;
            }
            mTopic.updateMuted(isChecked).thenCatch(new PromisedReply.FailureListener<>() {
                @Override
                public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                    activity.runOnUiThread(() -> muted.setChecked(!isChecked));
                    if (err instanceof NotConnectedException) {
                        Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                    }
                    return null;
                }
            });
        });

        final SwitchCompat archived = view.findViewById(R.id.switchArchived);
        archived.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mTopic == null) {
                return;
            }
            mTopic.updateArchived(isChecked).thenCatch(new PromisedReply.FailureListener<>() {
                @Override
                public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                    activity.runOnUiThread(() -> archived.setChecked(!isChecked));
                    if (err instanceof NotConnectedException) {
                        Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                    }
                    return null;
                }
            });
        });

        view.findViewById(R.id.buttonCopyID).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && mTopic != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("contact ID", mTopic.getName()));
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.displayQRCode).setOnClickListener(v -> {
            if (mTopic == null) {
                return;
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            @SuppressLint("InflateParams") final View codeView =
                    LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_qrcode, null);
            ImageView qrCodeImageView = codeView.findViewById(R.id.qrCodeImageView);
            UiUtils.generateQRCode(qrCodeImageView, UiUtils.TOPIC_URI_PREFIX + mTopic.getName());
            builder.setView(codeView).setTitle(R.string.scan_code);
            builder
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        view.findViewById(R.id.buttonCopyAlias).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && mTopic != null) {
                String alias = mTopic.tagByPrefix(Tinode.TAG_ALIAS);
                if (!TextUtils.isEmpty(alias)) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("alias", alias));
                    Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                }
            }
        });

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

        final FragmentActivity activity = requireActivity();
        final Bundle args = getArguments();
        if (args == null) {
            return;
        }

        String name = args.getString(Const.INTENT_EXTRA_TOPIC);
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);
        if (mTopic == null) {
            Log.d(TAG, "TopicInfo resumed with null topic.");
            activity.finish();
            return;
        }

        if (mTopic.isSlfType()) {
            activity.findViewById(R.id.topicIdWrapper).setVisibility(View.GONE);
            activity.findViewById(R.id.aliasIdWrapper).setVisibility(View.GONE);
        } else {
            ((TextView) activity.findViewById(R.id.topicAddress)).setText(mTopic.getName());
            activity.findViewById(R.id.topicIdWrapper).setVisibility(View.VISIBLE);
        }

        final View groupMembers = activity.findViewById(R.id.groupMembersWrapper);

        if (mTopic.isGrpType() && !mTopic.isChannel()) {
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
            // P2P topic or channel.
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
            PromisedReply<ServerMessage> response = switch (what) {
                case ACTION_REMOVE -> mTopic.eject(uid, false);
                case ACTION_BAN_MEMBER -> mTopic.eject(uid, true);
                default -> null;
            };

            if (response != null) {
                response.thenApply(new PromisedReply.SuccessListener<>() {
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
        if (Cache.getTinode().isMe(uid)) {
            return;
        }

        final FragmentActivity activity = requireActivity();
        final String userTitleFixed = TextUtils.isEmpty(userTitle) ?
                activity.getString(R.string.placeholder_contact_title) :
                userTitle;
        final String topicTitleFixed = TextUtils.isEmpty(topicTitle) ?
                activity.getString(R.string.placeholder_topic_title) :
                topicTitle;

        final LinearLayout actions = (LinearLayout) View.inflate(activity, R.layout.dialog_member_actions, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(activity);
        ((TextView) actions.findViewById(R.id.title)).setText(TextUtils.isEmpty(userTitle) ?
                activity.getString(R.string.placeholder_contact_title) :
                userTitle);
        dialog.setContentView(actions);
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
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException ignored) {
                            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "Unable to find contact manager");
                        }
                    } else {
                        mRequestContactsPermissionsLauncher.launch(new String[]{Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS});
                        Toast.makeText(activity, R.string.some_permissions_missing, Toast.LENGTH_SHORT).show();
                    }
                } else if (id == R.id.buttonSendMessage) {
                    ((MessageActivity) activity).changeTopic(uid, true);

                } else if (id == R.id.buttonPermissions) {
                    UiUtils.showEditPermissions(activity, mTopic, mode, uid,
                            Const.ACTION_UPDATE_SUB, "O");

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
        if (mTopic == null) {
            Log.w(TAG, "notifyDataSetChanged called with null topic");
            return;
        }

        notifyContentChanged();

        if (mTopic.isGrpType()) {
            mMembersAdapter.resetContent();
        }
    }

    // Called when topic description is changed.
    private void notifyContentChanged() {
        final FragmentActivity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View fragmentView = getView();
        if (fragmentView == null) {
            return;
        }
        final ImageView avatar = fragmentView.findViewById(R.id.imageAvatar);
        final TextView title = fragmentView.findViewById(R.id.topicTitle);
        final TextView subtitle = fragmentView.findViewById(R.id.topicComment);
        final View descriptionWrapper = fragmentView.findViewById(R.id.topicDescriptionWrapper);
        final TextView description = fragmentView.findViewById(R.id.topicDescription);

        VxCard pub = mTopic.getPub();
        if (pub != null && !TextUtils.isEmpty(pub.fn)) {
            title.setText(pub.fn);
            title.setTypeface(null, Typeface.NORMAL);
            title.setTextIsSelectable(true);
            if (!TextUtils.isEmpty(pub.note)) {
                description.setText(pub.note);
                descriptionWrapper.setVisibility(View.VISIBLE);
            } else {
                descriptionWrapper.setVisibility(View.GONE);
            }
        } else if (mTopic.isSlfType()) {
            title.setText(R.string.self_topic_title);
            title.setTypeface(null, Typeface.NORMAL);
            title.setTextIsSelectable(false);
            description.setText(R.string.self_topic_description);
            descriptionWrapper.setVisibility(View.VISIBLE);
        } else {
            title.setText(R.string.placeholder_contact_title);
            title.setTypeface(null, Typeface.ITALIC);
            title.setTextIsSelectable(false);
            avatar.setImageResource(mTopic.isP2PType() ?
                    R.drawable.ic_person_circle : R.drawable.ic_group_grey);
            descriptionWrapper.setVisibility(View.GONE);
        }

        Drawable icon = null;

        if (mTopic.hasChannelAccess()) {
            icon = AppCompatResources.getDrawable(activity, R.drawable.ic_channel);
        } else if (mTopic.isGrpType()) {
            icon = AppCompatResources.getDrawable(activity, R.drawable.ic_group_2);
        }

        if (icon != null) {
            icon.setBounds(0, 0, 64, 64);
            title.setCompoundDrawables(null, null, icon, null);
        } else {
            title.setCompoundDrawables(null, null, null, null);
        }

        // Trusted flags.
        fragmentView.findViewById(R.id.verified).setVisibility(mTopic.isTrustedVerified() ? View.VISIBLE : View.GONE);
        fragmentView.findViewById(R.id.staff).setVisibility(mTopic.isTrustedStaff() ? View.VISIBLE : View.GONE);
        fragmentView.findViewById(R.id.danger).setVisibility(mTopic.isTrustedDanger() ? View.VISIBLE : View.GONE);

        UiUtils.setAvatar(avatar, pub, mTopic.getName(), mTopic.isDeleted());

        PrivateType priv = mTopic.getPriv();
        if (priv != null && !TextUtils.isEmpty(priv.getComment())) {
            subtitle.setText(priv.getComment());
            subtitle.setTextIsSelectable(true);
            subtitle.setVisibility(View.VISIBLE);
        } else {
            subtitle.setVisibility(View.GONE);
        }

        if (mTopic.isSlfType()) {
            fragmentView.findViewById(R.id.switchMutedWrapper).setVisibility(View.GONE);
            activity.findViewById(R.id.aliasIdWrapper).setVisibility(View.GONE);
        } else {
            ((SwitchCompat) fragmentView.findViewById(R.id.switchMuted)).setChecked(mTopic.isMuted());
            fragmentView.findViewById(R.id.switchMutedWrapper).setVisibility(View.VISIBLE);

            String alias = mTopic.alias();
            if (!TextUtils.isEmpty(alias)) {
                ((TextView) activity.findViewById(R.id.alias)).setText("@" + alias);
                activity.findViewById(R.id.aliasIdWrapper).setVisibility(View.VISIBLE);
            } else {
                activity.findViewById(R.id.aliasIdWrapper).setVisibility(View.GONE);
            }
        }
        ((SwitchCompat) fragmentView.findViewById(R.id.switchArchived)).setChecked(mTopic.isArchived());
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_edit, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            FragmentActivity activity = requireActivity();
            if (activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }

            ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_GENERAL,
                    null, true);
            return true;
        }
        return false;
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        MenuProvider.super.onPrepareMenu(menu);
        if (mTopic != null && mTopic.isSlfType()) {
            menu.clear();
        }
    }

    private static class MemberViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView extraInfo;
        final LinearLayout statusContainer;
        final TextView[] status;
        final ImageButton more;
        final ImageView avatar;


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
            avatar = item.findViewById(android.R.id.icon);
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

            String title = isMe ? activity.getString(R.string.current_user) : null;
            if (sub.pub != null) {
                if (title == null) {
                    title = !TextUtils.isEmpty(sub.pub.fn) ? sub.pub.fn :
                            activity.getString(R.string.placeholder_contact_title);
                }
            } else {
                Log.w(TAG, "Pub is null for " + sub.user);
            }
            holder.name.setText(title);
            holder.extraInfo.setText(sub.acs != null ? sub.acs.getMode() : "");

            int i = 0;
            UiUtils.AccessModeLabel[] labels = UiUtils.accessModeLabels(sub.acs, ss.status);
            if (labels != null) {
                for (UiUtils.AccessModeLabel l : labels) {
                    holder.status[i].setText(l.nameId());
                    holder.status[i].setTextColor(l.color());
                    ((GradientDrawable) holder.status[i].getBackground()).setStroke(2, l.color());
                    holder.status[i++].setVisibility(View.VISIBLE);
                }
            }
            for (; i < holder.status.length; i++) {
                holder.status[i].setVisibility(View.GONE);
            }

            UiUtils.setAvatar(holder.avatar, sub.pub, sub.user, false);

            final View.OnClickListener action = v -> {
                int pos = holder.getBindingAdapterPosition();
                final Subscription<VxCard, PrivateType> sub1 = mItems[pos];
                VxCard pub = mTopic != null ? mTopic.getPub() : null;
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
