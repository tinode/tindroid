package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;

import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.ServerMessage;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for adding/editing a group topic
 */
public class CreateGroupFragment extends Fragment {

    private static final String TAG = "CreateGroupFragment";
    private static final int LOADER_ID = 102;

    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private MembersAdapter mSelectedAdapter;
    private ContactsAdapter mContactsAdapter;

    // Callback which receives notifications of contacts loading status;
    private ContactsLoaderCallback mContactsLoaderCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mImageLoader = UiUtils.getImageLoaderInstance(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_add_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstance) {

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        mFailureListener = new PromisedReply.FailureListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onFailure(final Exception err) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (err instanceof NotConnectedException) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                        }
                        startActivity(new Intent(activity, ChatsActivity.class));
                    }
                });
                return null;
            }
        };

        view.findViewById(R.id.uploadAvatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.requestAvatar(CreateGroupFragment.this);
            }
        });

        // Recycler view with selected contacts.
        RecyclerView rv = view.findViewById(R.id.selected_members);
        FlexboxLayoutManager lm = new FlexboxLayoutManager(activity);
        lm.setFlexDirection(FlexDirection.ROW);
        lm.setJustifyContent(JustifyContent.FLEX_START);
        rv.setLayoutManager(lm);
        rv.setHasFixedSize(false);

        mSelectedAdapter = new MembersAdapter(null, new MembersAdapter.ClickListener() {
            @Override
            public void onClick(String unique) {
                mContactsAdapter.toggleSelected(unique);
            }
        }, true);
        rv.setAdapter(mSelectedAdapter);

        // Recycler view with all available Tinode contacts.
        rv = view.findViewById(R.id.contact_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));

        mContactsAdapter = new ContactsAdapter(activity, mImageLoader, new ContactsAdapter.ClickListener() {
            @Override
            public void onClick(final String unique, final ContactsAdapter.ViewHolder holder) {
                if (!mContactsAdapter.isSelected(unique)) {
                    mSelectedAdapter.append(unique, (String) holder.text1.getText(),
                            holder.getIconDrawable(), true);
                } else {
                    mSelectedAdapter.remove(unique);
                }
                mContactsAdapter.toggleSelected(unique);
            }
        });
        rv.setAdapter(mContactsAdapter);

        mContactsLoaderCallback = new ContactsLoaderCallback(LOADER_ID, activity, mContactsAdapter);

        // This button creates the new group.
        view.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText titleEdit = activity.findViewById(R.id.editTitle);
                String topicTitle = titleEdit.getText().toString();
                if (TextUtils.isEmpty(topicTitle)) {
                    titleEdit.setError(getString(R.string.name_required));
                    return;
                }
                // Make sure topic title is not too long.
                if (topicTitle.length() > UiUtils.MAX_TITLE_LENGTH) {
                    topicTitle = topicTitle.substring(0, UiUtils.MAX_TITLE_LENGTH);
                }

                String subtitle = ((EditText) activity.findViewById(R.id.editPrivate)).getText().toString();
                if (subtitle.length() > UiUtils.MAX_TITLE_LENGTH) {
                    subtitle = subtitle.substring(0, UiUtils.MAX_TITLE_LENGTH);
                }

                final String tags = ((EditText) activity.findViewById(R.id.editTags)).getText().toString();

                String[] members = mSelectedAdapter.getAdded();
                if (members.length == 0) {
                    Toast.makeText(activity, R.string.add_one_member, Toast.LENGTH_SHORT).show();
                    return;
                }

                Bitmap bmp = null;
                try {
                    bmp = ((BitmapDrawable) ((ImageView) activity.findViewById(R.id.imageAvatar))
                            .getDrawable()).getBitmap();
                } catch (ClassCastException ignored) {
                    // If image is not loaded, the drawable is a vector.
                    // Ignore it.
                }

                createTopic(activity, topicTitle, bmp, subtitle, UiUtils.parseTags(tags), members);
            }
        });
    }


    @Override
    public void onResume() {
        super.onResume();

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        restartLoader();
    }

    @Override
    public void onPause() {
        super.onPause();

        // In the case onPause() is called during a fling the image loader is
        // un-paused to let any remaining background work complete.
        mImageLoader.setPauseWork(false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (requestCode == UiUtils.ACTIVITY_RESULT_SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.acceptAvatar(getActivity(), (ImageView) activity.findViewById(R.id.imageAvatar), data);
        }
    }

    private void createTopic(final Activity activity, final String title,
                             final Bitmap avatar, final String subtitle, final String[] tags, final String[] members) {
        final ComTopic<VxCard> topic = new ComTopic<>(Cache.getTinode(), (Topic.Listener) null);
        topic.setPub(new VxCard(title, avatar));
        topic.setPriv(subtitle);
        topic.setTags(tags);
        try {
            topic.subscribe().thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    for (String user : members) {
                        topic.invite(user, null /* use default */);
                    }

                    Intent intent = new Intent(activity, MessageActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    intent.putExtra("topic", topic.getName());
                    startActivity(intent);

                    return null;
                }
            }, mFailureListener);
        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
            // Go back to contacts
        } catch (Exception ex) {
            Toast.makeText(activity, R.string.failed_to_create_topic, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Failed to create topic", ex);
        }

        startActivity(new Intent(activity, ChatsActivity.class));
    }

    // Restarts the loader. This triggers onCreateLoader(), which builds the
    // necessary content Uri from mSearchTerm.
    private void restartLoader() {
        final StartChatActivity activity = (StartChatActivity) getActivity();
        if (activity == null) {
            return;
        }

        if (UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS)) {
            LoaderManager.getInstance(activity).restartLoader(LOADER_ID, null, mContactsLoaderCallback);
        } else if (!activity.isReadContactsPermissionRequested()) {
            activity.setReadContactsPermissionRequested();
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS},
                    UiUtils.CONTACTS_PERMISSION_ID);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == UiUtils.CONTACTS_PERMISSION_ID) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                restartLoader();
            }
        }
    }
}
