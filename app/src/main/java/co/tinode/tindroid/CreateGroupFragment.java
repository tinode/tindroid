package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.pchmn.materialchips.ChipsInput;
import com.pchmn.materialchips.model.Chip;
import com.pchmn.materialchips.model.ChipInterface;

import java.util.List;

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
public class CreateGroupFragment extends Fragment implements UiUtils.ContactsLoaderResultReceiver {

    private static final String TAG = "CreateGroupFragment";

    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private ChipsInput mChipsInput;

    // Sorted set of selected contacts (cursor positions of selected contacts).
    // private TreeSet<Integer> mSelectedContacts;

    public CreateGroupFragment() {
        mFailureListener = new PromisedReply.FailureListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onFailure(final Exception err) {
                final Activity activity = getActivity();
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (err instanceof NotConnectedException) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                        }
                        startActivity(new Intent(activity, ContactsActivity.class));
                    }
                });
                return null;
            }
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mImageLoader = new ImageLoader(getActivity(), UiUtils.getListPreferredItemHeight(this),
                getActivity().getSupportFragmentManager()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                // This gets called in a background thread and passed the data from
                // ImageLoader.loadImage().
                return UiUtils.loadContactPhotoThumbnail(CreateGroupFragment.this, (String) data, getImageSize());
            }
        };
        // Set a placeholder loading image for the image loader
        mImageLoader.setLoadingImage(R.drawable.ic_person_circle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");

        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_add_group, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        Log.d(TAG, "onActivityCreated");
        final Activity activity = getActivity();

        activity.findViewById(R.id.uploadAvatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.requestAvatar(CreateGroupFragment.this);
            }
        });

        mChipsInput = (ChipsInput)  activity.findViewById(R.id.groupMembers);
        //setListAdapter(mContactsAdapter);

        activity.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText titleEdit = ((EditText) activity.findViewById(R.id.editTitle));
                final String topicTitle = titleEdit.getText().toString();
                if (TextUtils.isEmpty(topicTitle)) {
                    titleEdit.setError(getString(R.string.name_required));
                    return;
                }
                final String subtitle = ((EditText) activity.findViewById(R.id.editPrivate)).getText().toString();

                List<Chip> selected = (List<Chip>) mChipsInput.getSelectedChipList();
                Log.d(TAG, "Chips count: " + selected.size() + " == " + mChipsInput.getChildCount());
                if (selected.size() == 0) {
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

                createTopic(activity, topicTitle, bmp, subtitle);
            }
        });

        getLoaderManager().initLoader(0, null,
                new UiUtils.ContactsLoaderCallback(getActivity(), this));
    }


    @Override
    public void onResume() {
        super.onResume();
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
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.acceptAvatar(getActivity(), (ImageView) getActivity().findViewById(R.id.imageAvatar), data);
        }
    }

    @Override
    public void receiveResult(int id, Cursor data) {
        mChipsInput.setFilterableList(UiUtils.createChipsInputFilteredList(data));
    }

    private void createTopic(final Activity activity, final String title, final Bitmap avatar, final String subtitle) {
        final ComTopic<VxCard> topic = new ComTopic<>(Cache.getTinode(), (Topic.Listener) null);
        topic.setPub(new VxCard(title, avatar));
        topic.setPriv(subtitle);
        try {
            topic.subscribe().thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                    for (ChipInterface chip : mChipsInput.getSelectedChipList()) {
                        topic.invite((String) chip.getId(), null /* use default */);
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
        } catch (Exception e) {
            Toast.makeText(activity, R.string.failed_to_create_topic, Toast.LENGTH_SHORT).show();
        }

        startActivity(new Intent(activity, ContactsActivity.class));
    }
}
