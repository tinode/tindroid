package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.flexbox.FlexboxLayout;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Topic general info fragment: p2p or a group topic.
 */
public class TopicGeneralFragment extends Fragment implements UiUtils.AvatarPreviewer,
        MessageActivity.DataSetChangeListener {

    private static final String TAG = "TopicGeneralFragment";

    private ComTopic<VxCard> mTopic;

    private final ActivityResultLauncher<Intent> mAvatarPickerLauncher =
            UiUtils.avatarPickerLauncher(this, this);

    private final ActivityResultLauncher<String[]> mRequestAvatarPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    // Check if all required permissions are granted.
                    if (!e.getValue()) {
                        return;
                    }
                }
                final FragmentActivity activity = getActivity();
                if (activity != null) {
                    // Try to open the image selector again.
                    Intent launcher = UiUtils.avatarSelectorIntent(activity, null);
                    if (launcher != null) {
                        mAvatarPickerLauncher.launch(launcher);
                    }
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tpc_general, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.topic_settings);
        toolbar.setSubtitle(null);
        toolbar.setLogo(null);

        view.findViewById(R.id.uploadAvatar).setOnClickListener(v -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            Intent launcher = UiUtils.avatarSelectorIntent(activity, mRequestAvatarPermissionsLauncher);
            if (launcher != null) {
                mAvatarPickerLauncher.launch(launcher);
            }
        });
        view.findViewById(R.id.buttonCopyID).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && mTopic != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("contact ID", mTopic.getName()));
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    // onResume sets up the form with values and views which do not change + sets up listeners.
    public void onResume() {
        final Activity activity = requireActivity();
        final Bundle args = getArguments();

        if (args == null) {
            return;
        }

        String name = args.getString(Const.INTENT_EXTRA_TOPIC);
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);
        if (mTopic == null) {
            Log.d(TAG, "TopicPermissions resumed with null topic.");
            activity.finish();
            return;
        }

        ((TextView) activity.findViewById(R.id.topicAddress)).setText(mTopic.getName());

        notifyDataSetChanged();
        super.onResume();
    }

    private void refreshTags(Activity activity, String[] tags) {
        final View tagManager = activity.findViewById(R.id.tagsManagerWrapper);
        if (mTopic.isGrpType() && mTopic.isOwner()) {
            // Group topic
            tagManager.setVisibility(View.VISIBLE);
            tagManager.findViewById(R.id.buttonManageTags)
                    .setOnClickListener(view -> showEditTags());

            LayoutInflater inflater = LayoutInflater.from(activity);
            FlexboxLayout tagsView = activity.findViewById(R.id.tagList);
            tagsView.removeAllViews();

            if (tags != null) {
                tagsView.setVisibility(View.VISIBLE);
                activity.findViewById(R.id.noTagsFound).setVisibility(View.GONE);
                for (String tag : tags) {
                    TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                    label.setText(tag);
                    tagsView.addView(label);
                }
            } else {
                tagsView.setVisibility(View.GONE);
                activity.findViewById(R.id.noTagsFound).setVisibility(View.VISIBLE);
            }
        } else {
            // P2P topic
            tagManager.setVisibility(View.GONE);
        }
    }

    // Dialog for editing tags.
    private void showEditTags() {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        String[] tagArray = mTopic.getTags();
        String tags = tagArray != null ? TextUtils.join(", ", mTopic.getTags()) : "";

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        @SuppressLint("InflateParams") final View editor =
                LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_edit_tags, null);
        builder.setView(editor).setTitle(R.string.tags_management);

        final EditText tagsEditor = editor.findViewById(R.id.editTags);
        tagsEditor.setText(tags);
        tagsEditor.setSelection(tags.length());
        builder
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String[] tags1 = UiUtils.parseTags(tagsEditor.getText().toString());
                    // noinspection unchecked
                    mTopic.setMeta(new MsgSetMeta.Builder().with(tags1).build())
                            .thenApply(new PromisedReply.SuccessListener() {
                                @Override
                                public PromisedReply onSuccess(Object result) {
                                    activity.runOnUiThread(() -> refreshTags(activity, mTopic.getTags()));
                                    return null;
                                }
                            })
                            .thenCatch(new UiUtils.ToastFailureListener(activity));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void notifyDataSetChanged() {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final AppCompatImageView avatar = activity.findViewById(R.id.imageAvatar);
        final View uploadAvatar = activity.findViewById(R.id.uploadAvatar);
        final TextView title = activity.findViewById(R.id.topicTitle);
        final TextView comment = activity.findViewById(R.id.topicComment);
        final TextView description = activity.findViewById(R.id.topicDescription);
        final View descriptionWrapper = activity.findViewById(R.id.topicDescriptionWrapper);
        ((TextView) activity.findViewById(R.id.topicAddress)).setText(mTopic.getName());

        boolean editable = mTopic.isGrpType() && mTopic.isOwner();
        title.setEnabled(editable);
        uploadAvatar.setVisibility(editable ? View.VISIBLE : View.GONE);

        title.setHint(mTopic.isGrpType() ? R.string.hint_topic_title : R.string.hint_contact_name);

        VxCard pub = mTopic.getPub();
        if (pub != null) {
            title.setText(pub.fn);
            if (!editable || TextUtils.isEmpty(pub.note)) {
                descriptionWrapper.setVisibility(View.GONE);
            } else {
                description.setText(pub.note);
                descriptionWrapper.setVisibility(View.VISIBLE);
            }
        } else {
            descriptionWrapper.setVisibility(View.GONE);
        }

        UiUtils.setAvatar(avatar, pub, mTopic.getName(), false);

        PrivateType priv = mTopic.getPriv();
        if (priv != null && !TextUtils.isEmpty(priv.getComment())) {
            comment.setText(priv.getComment());
        }

        refreshTags(activity, mTopic.getTags());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_save, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            final FragmentActivity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }

            final String newTitle = ((TextView) activity.findViewById(R.id.topicTitle)).getText().toString().trim();
            final String newComment = ((TextView) activity.findViewById(R.id.topicComment)).getText().toString().trim();
            final String newDescription = mTopic.isGrpType() && mTopic.isOwner() ?
                    ((TextView) activity.findViewById(R.id.topicDescription)).getText().toString().trim() : null;

            UiUtils.updateTopicDesc(mTopic, newTitle, newComment, newDescription)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage unused) {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                activity.runOnUiThread(() -> activity.getSupportFragmentManager().popBackStack());
                            }
                            return null;
                        }
                    })
                    .thenCatch(new UiUtils.ToastFailureListener(activity));

            return true;
        }
        return false;
    }

    @Override
    public void showAvatarPreview(Bundle args) {
        final FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_AVATAR_PREVIEW, args, true);
    }
}
