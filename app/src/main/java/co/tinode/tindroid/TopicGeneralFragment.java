package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;

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
                FragmentActivity activity = getActivity();
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

        view.findViewById(R.id.uploadAvatar).setOnClickListener(v -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            mAvatarPickerLauncher.launch(UiUtils.avatarSelectorIntent(activity, mRequestAvatarPermissionsLauncher));
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    // onResume sets up the form with values and views which do not change + sets up listeners.
    public void onResume() {
        super.onResume();

        final Activity activity = getActivity();
        final Bundle args = getArguments();

        if (activity == null || args == null) {
            return;
        }

        String name = args.getString("topic");
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);
        if (mTopic == null) {
            Log.d(TAG, "TopicPermissions resumed with null topic.");
            activity.finish();
            return;
        }

        ((TextView) activity.findViewById(R.id.topicAddress)).setText(mTopic.getName());

        final View tagManager = activity.findViewById(R.id.tagsManagerWrapper);
        if (mTopic.isGrpType() && mTopic.isOwner()) {
            // Group topic
            tagManager.setVisibility(View.VISIBLE);
            tagManager.findViewById(R.id.buttonManageTags)
                    .setOnClickListener(view -> showEditTags());

            LayoutInflater inflater = LayoutInflater.from(activity);
            FlexboxLayout tagsView = activity.findViewById(R.id.tagList);
            tagsView.removeAllViews();

            String[] tags = mTopic.getTags();
            if (tags != null) {
                for (String tag : tags) {
                    TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                    label.setText(tag);
                    tagsView.addView(label);
                }
            }
        } else {
            // P2P topic
            tagManager.setVisibility(View.GONE);
        }

        notifyDataSetChanged();
    }

    // Dialog for editing tags.
    private void showEditTags() {
        final Activity activity = getActivity();
        if (activity == null) {
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
                    mTopic.setMeta(new MsgSetMeta(tags1))
                            .thenCatch(new UiUtils.ToastFailureListener(activity));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void notifyDataSetChanged() {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final AppCompatImageView avatar = activity.findViewById(R.id.imageAvatar);
        final EditText title = activity.findViewById(R.id.topicTitle);
        final EditText subtitle = activity.findViewById(R.id.topicSubtitle);
        final EditText description = activity.findViewById(R.id.topicDescription);
        final View descriptionWrapper = activity.findViewById(R.id.topicDescriptionWrapper);

        boolean editable = mTopic.isGrpType() && mTopic.isOwner();
        title.setEnabled(editable);

        VxCard pub = mTopic.getPub();
        if (pub != null) {
            title.setText(pub.fn);
            if (!editable && TextUtils.isEmpty(pub.note)) {
                descriptionWrapper.setVisibility(View.GONE);
            } else {
                description.setText(pub.note);
                descriptionWrapper.setVisibility(View.VISIBLE);
            }
        }

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
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_save, menu);
        super.onCreateOptionsMenu(menu, inflater);
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
