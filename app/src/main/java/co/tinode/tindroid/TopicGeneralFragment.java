package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.AttachmentPickerDialog;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.FndTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Topic general info fragment: p2p or a group topic.
 */
public class TopicGeneralFragment extends Fragment implements MenuProvider, UtilsMedia.MediaPreviewer,
        UiUtils.AliasChecker, MessageActivity.DataSetChangeListener {

    private static final String TAG = "TopicGeneralFragment";

    private ComTopic<VxCard> mTopic;

    private final ActivityResultLauncher<PickVisualMediaRequest> mAvatarGalleryLauncher =
            UtilsMedia.pickMediaLauncher(this, this);

    private final ActivityResultLauncher<Void> mAvatarCameraLauncher =
            UtilsMedia.takePreviewPhotoLauncher(this, this);

    private final ActivityResultLauncher<String> mRequestAvatarPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    mAvatarCameraLauncher.launch(null);
                }
            });

    private UiUtils.ValidatorHandler mAliasChecker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tpc_general, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final FragmentActivity activity = requireActivity();

        mAliasChecker = new UiUtils.ValidatorHandler(this);

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.topic_settings);
        toolbar.setSubtitle(null);
        toolbar.setLogo(null);

        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        view.findViewById(R.id.uploadAvatar).setOnClickListener(v -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            new AttachmentPickerDialog.Builder()
                    .setGalleryLauncher(mAvatarGalleryLauncher)
                    .setCameraPreviewLauncher(mAvatarCameraLauncher, mRequestAvatarPermissionsLauncher)
                    .build()
                    .show(getChildFragmentManager());
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAliasChecker.removeMessages();
    }

    private void refreshTags(Activity activity, String[] tags) {
        View fragmentView = getView();
        if (fragmentView == null) {
            return;
        }

        final View tagManager = fragmentView.findViewById(R.id.tagsManagerWrapper);
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
                fragmentView.findViewById(R.id.noTagsFound).setVisibility(View.GONE);
                for (String tag : tags) {
                    if (tag.startsWith(Tinode.TAG_ALIAS)) {
                        // Skip alias tag. It's shown elsewhere.
                        continue;
                    }
                    TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                    label.setText(tag);
                    tagsView.addView(label);
                }
            } else {
                tagsView.setVisibility(View.GONE);
                fragmentView.findViewById(R.id.noTagsFound).setVisibility(View.VISIBLE);
            }
        } else {
            // P2P topic
            tagManager.setVisibility(View.GONE);
        }
    }

    // Dialog for editing tags.
    private void showEditTags() {
        final Activity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // Get tags and remove the 'alias:abc123' - it's edited elsewhere.
        String[] tagArray = Tinode.clearTagPrefix(mTopic.getTags(), Tinode.TAG_ALIAS);
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
                    String tagList = tagsEditor.getText().toString();
                    // Add back the alias tag.
                    String alias = mTopic.tagByPrefix(Tinode.TAG_ALIAS);
                    if (!TextUtils.isEmpty(alias)) {
                        tagList = alias + "," + tagList;
                    }
                    // noinspection unchecked
                    mTopic.updateTags(tagList)
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

        View fragmentView = getView();
        if (fragmentView == null) {
            return;
        }
        final AppCompatImageView avatar = fragmentView.findViewById(R.id.imageAvatar);
        final View uploadAvatar = fragmentView.findViewById(R.id.uploadAvatar);
        final TextView title = fragmentView.findViewById(R.id.topicTitle);
        final TextView alias = fragmentView.findViewById(R.id.alias);
        final TextView comment = fragmentView.findViewById(R.id.topicComment);
        final TextView description = fragmentView.findViewById(R.id.topicDescription);
        final View descriptionWrapper = fragmentView.findViewById(R.id.topicDescriptionWrapper);
        ((TextView) fragmentView.findViewById(R.id.topicAddress)).setText(mTopic.getName());

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

        String aliasTag = mTopic.alias();
        if (!TextUtils.isEmpty(aliasTag)) {
            alias.setText(aliasTag);
        }

        alias.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                alias.setError(UiUtils.validateAlias(activity, mAliasChecker, s.toString()));
            }
        });

        refreshTags(activity, mTopic.getTags());
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_save, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save && mTopic != null) {
            final FragmentActivity activity = requireActivity();
            if (activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }

            View fragmentView = getView();
            if (fragmentView == null) {
                return false;
            }

            final String newTitle = ((TextView) fragmentView.findViewById(R.id.topicTitle)).getText().toString().trim();
            final String newComment = ((TextView) fragmentView.findViewById(R.id.topicComment)).getText().toString().trim();
            final String newDescription = mTopic.isGrpType() && mTopic.isOwner() ?
                    ((TextView) fragmentView.findViewById(R.id.topicDescription)).getText().toString().trim() : null;
            final String newAlias = ((TextView) fragmentView.findViewById(R.id.alias)).getText().toString().trim();

            UiUtils.updateTopicDesc(mTopic, newTitle, newComment, newDescription, newAlias)
                    .thenApply(new PromisedReply.SuccessListener<>() {
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
    public void handleMedia(Bundle args) {
        final FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_AVATAR_PREVIEW, args, true);
    }

    private void setValidationError(final String error) {
        View fv = getView();
        if (fv != null && isVisible()) {
            Activity activity = requireActivity();
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> ((TextView) fv.findViewById(R.id.alias)).setError(error));
        }
    }

    public void checkUniqueness(String alias) {
        if (mTopic == null || !isVisible()) {
            return;
        }

        // Check if the alias is already taken.
        FndTopic<?> fnd = Cache.getTinode().getFndTopic();
        fnd.checkTagUniqueness(alias, mTopic.getName()).thenApply(new PromisedReply.SuccessListener<>() {
            @Override
            public PromisedReply<Boolean> onSuccess(Boolean result) {
                if (result) {
                    setValidationError(null);
                } else {
                    setValidationError(getString(R.string.alias_already_taken));
                }
                return null;
            }
        }).thenCatch(new PromisedReply.FailureListener<>() {
            @Override
            public <E extends Exception> PromisedReply<Boolean> onFailure(E err) {
                setValidationError(err.toString());
                return null;
            }
        });
    }
}
