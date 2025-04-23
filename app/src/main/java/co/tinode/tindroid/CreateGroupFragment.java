package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.AttachmentPickerDialog;
import co.tinode.tindroid.widgets.HorizontalListDivider;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Fragment for adding/editing a group topic
 */
public class CreateGroupFragment extends Fragment implements UtilsMedia.MediaPreviewer {
    private static final int LOADER_ID = 102;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    // Contacts selected as group members.
    private MembersAdapter mSelectedAdapter;
    // All contacts.
    private ContactsAdapter mContactsAdapter;

    // Callback which receives notifications of contacts loading status;
    private ContactsLoaderCallback mContactsLoaderCallback;

    private final ActivityResultLauncher<PickVisualMediaRequest> mGalleryPickerLauncher =
            UtilsMedia.pickMediaLauncher(this, this);

    private final ActivityResultLauncher<Void> mCameraPreviewLauncher =
            UtilsMedia.takePreviewPhotoLauncher(this, this);

    private final ActivityResultLauncher<String> mRequestAvatarPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    mCameraPreviewLauncher.launch(null);
                }
            });

    private final ActivityResultLauncher<String[]> mRequestContactsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    // Check if all required permissions are granted.
                    if (!e.getValue()) {
                        return;
                    }
                }
                mContactsAdapter.setContactsPermissionGranted();
                restartLoader((StartChatActivity) getActivity());
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstance) {
        final FragmentActivity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        mFailureListener = new PromisedReply.FailureListener<>() {
            @Override
            public PromisedReply<ServerMessage> onFailure(final Exception err) {
                activity.runOnUiThread(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (err instanceof NotConnectedException) {
                        Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                    }
                    startActivity(new Intent(activity, ChatsActivity.class));
                });
                return null;
            }
        };

        AvatarViewModel avatarVM = new ViewModelProvider(activity).get(AvatarViewModel.class);
        avatarVM.getAvatar().observe(getViewLifecycleOwner(), bmp ->
            UiUtils.acceptAvatar(activity, view.findViewById(R.id.imageAvatar), bmp));

        view.findViewById(R.id.uploadAvatar).setOnClickListener(v -> {
            if (activity.isDestroyed() || activity.isFinishing()) {
                return;
            }
            new AttachmentPickerDialog.Builder()
                    .setGalleryLauncher(mGalleryPickerLauncher)
                    .setCameraPreviewLauncher(mCameraPreviewLauncher, mRequestAvatarPermissionsLauncher)
                    .build()
                    .show(getChildFragmentManager());
        });

        // Recycler view with selected contacts.
        RecyclerView rv = view.findViewById(R.id.selected_members);
        FlexboxLayoutManager lm = new FlexboxLayoutManager(activity);
        lm.setFlexDirection(FlexDirection.ROW);
        lm.setJustifyContent(JustifyContent.FLEX_START);
        rv.setLayoutManager(lm);
        rv.setHasFixedSize(false);

        mSelectedAdapter = new MembersAdapter(null,
                (unique, pos) -> mContactsAdapter.toggleSelected(unique, pos), true);
        rv.setAdapter(mSelectedAdapter);

        // Recycler view with all available Tinode contacts.
        rv = view.findViewById(R.id.contact_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(false);
        rv.addItemDecoration(new HorizontalListDivider(activity));
        rv.setNestedScrollingEnabled(false);

        mContactsAdapter = new ContactsAdapter(activity, (position, unique, displayName, photoUri) -> {
            if (!mContactsAdapter.isSelected(unique)) {
                mSelectedAdapter.append(position, unique, displayName, photoUri);
            } else {
                mSelectedAdapter.remove(unique);
            }
            mContactsAdapter.toggleSelected(unique, position);
        });
        rv.setAdapter(mContactsAdapter);

        mContactsLoaderCallback = new ContactsLoaderCallback(LOADER_ID, activity, mContactsAdapter);

        // This button creates the new group.
        view.findViewById(R.id.goNext).setOnClickListener(v -> {
            EditText titleEdit = activity.findViewById(R.id.editTitle);
            String topicTitle = titleEdit.getText().toString();
            if (TextUtils.isEmpty(topicTitle)) {
                titleEdit.setError(getString(R.string.name_required));
                return;
            }
            // Make sure topic title is not too long.
            if (topicTitle.length() > Const.MAX_TITLE_LENGTH) {
                topicTitle = topicTitle.substring(0, Const.MAX_TITLE_LENGTH);
            }

            String description = ((EditText) activity.findViewById(R.id.topicDescription)).getText().toString();
            if (description.length() > Const.MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, Const.MAX_DESCRIPTION_LENGTH);
            }

            // Private comment.
            String pcomment = ((EditText) activity.findViewById(R.id.editPrivate)).getText().toString();
            if (pcomment.length() > Const.MAX_TITLE_LENGTH) {
                pcomment = pcomment.substring(0, Const.MAX_TITLE_LENGTH);
            }

            final String tags = ((EditText) activity.findViewById(R.id.editTags)).getText().toString();

            boolean isChannel = ((SwitchCompat) activity.findViewById(R.id.isChannel)).isChecked();
            String[] members = mSelectedAdapter.getAdded();
            if (members.length == 0 && !isChannel) {
                Toast.makeText(activity, R.string.add_one_member, Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bmp = null;
            try {
                bmp = ((BitmapDrawable) ((ImageView) activity.findViewById(R.id.imageAvatar))
                        .getDrawable()).getBitmap();
            } catch (ClassCastException ignored) {
                // If image is not loaded, the drawable is a vector. Ignore it.
            }

            createTopic(activity, topicTitle, bmp, description, pcomment, isChannel,
                    Cache.getTinode().parseTags(tags), members);
        });
    }


    @Override
    public void onResume() {
        super.onResume();

        restartLoader((StartChatActivity) getActivity());
    }

    private void createTopic(final Activity activity, final String title, final Bitmap avatar, final String description,
                             final String pcomment, final boolean isChannel, final String[] tags,
                             final String[] members) {
        final ComTopic<VxCard> topic = new ComTopic<>(Cache.getTinode(), null, isChannel);
        topic.setComment(pcomment);
        topic.setTags(tags);
        topic.setPub(new VxCard(title, description));
        AttachmentHandler.uploadAvatar(topic.getPub(), avatar, null)
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage unused) {
                        return topic.subscribe().thenApply(new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage unused) {
                                for (String user : members) {
                                    topic.invite(user, null /* use default */);
                                }

                                Intent intent = new Intent(activity, MessageActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                intent.putExtra(Const.INTENT_EXTRA_TOPIC, topic.getName());
                                startActivity(intent);

                                return null;
                            }
                        });
                    }
                })
                .thenCatch(mFailureListener);

        startActivity(new Intent(activity, ChatsActivity.class));
    }

    // Restarts the loader. This triggers onCreateLoader(), which builds the
    // necessary content Uri from mSearchTerm.
    private void restartLoader(StartChatActivity activity) {
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        if (UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS)) {
            LoaderManager.getInstance(activity).restartLoader(LOADER_ID, null, mContactsLoaderCallback);
        } else if (activity.shouldRequestReadContactsPermission()) {
            activity.setReadContactsPermissionRequested();
            mRequestContactsPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS});
        }
    }

    @Override
    public void handleMedia(Bundle args) {
        StartChatActivity activity = (StartChatActivity) getActivity();
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        activity.showFragment(StartChatActivity.FRAGMENT_AVATAR_PREVIEW, args, true);
    }
}
