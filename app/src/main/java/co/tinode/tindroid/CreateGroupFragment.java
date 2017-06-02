package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
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

import java.util.ArrayList;
import java.util.List;

import co.tinode.tindroid.account.Utils;
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

    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread

    private ContactsLoaderCallback mContactsLoaderCallback;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private ChipsInput mChipsInput;

    // Sorted set of selected contacts (cursor positions of selected contacts).
    // private TreeSet<Integer> mSelectedContacts;

    public CreateGroupFragment() {
        mFailureListener = new PromisedReply.FailureListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onFailure(final Exception err) throws Exception {
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

        mContactsLoaderCallback = new ContactsLoaderCallback();

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

                createTopic(activity, topicTitle, bmp);
            }
        });

        getLoaderManager().initLoader(0, null, mContactsLoaderCallback);
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

    private List<Chip> createFilteredList(Cursor cursor) {
        List<Chip> list = new ArrayList<>();

        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                final String uid = cursor.getString(ContactsQuery.IM_HANDLE);
                final String uriString = cursor.getString(ContactsQuery.PHOTO_THUMBNAIL_DATA);
                final Uri photoUri = uriString == null ? null : Uri.parse(uriString);
                final String displayName = cursor.getString(ContactsQuery.DISPLAY_NAME);
                list.add(new Chip(uid, photoUri, displayName, null));
                cursor.moveToNext();
            }
        }

        return list;
    }

    private void createTopic(final Activity activity, final String title, final Bitmap avatar) {
        final Topic<VCard,String,String> topic = new Topic<>(Cache.getTinode(), null);
        topic.setPub(new VCard(title, avatar));
        try {
            topic.subscribe().thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                    final String inviteText = getActivity().getString(R.string.invitation_text);
                    for (ChipInterface chip : mChipsInput.getSelectedChipList()) {
                        topic.invite((String) chip.getId(), null /* use default */, inviteText);
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

    interface ContactsQuery {
        String[] PROJECTION = {
                ContactsContract.Data._ID,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Im.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Im.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Im.DATA,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Im.PROTOCOL,
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL,
        };

        int ID = 0;
        int CONTACT_ID = 1;
        int DISPLAY_NAME = 2;
        int PHOTO_THUMBNAIL_DATA = 3;
        int IM_HANDLE = 4;

        String SELECTION = ContactsContract.Data.MIMETYPE + "=? AND " +
                ContactsContract.CommonDataKinds.Im.PROTOCOL + "=? AND " +
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL + "=?";
        String[] SELECTION_ARGS = {
                ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE,
                Integer.toString(ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM),
                Utils.IM_PROTOCOL,
        };
        String SORT_ORDER = ContactsContract.CommonDataKinds.Im.DISPLAY_NAME_PRIMARY;
    }

    private class ContactsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {

            // Returns a new CursorLoader for querying the Contacts table. No arguments are used
            // for the selection clause. The search string is either encoded onto the content URI,
            // or no contacts search string is used. The other search criteria are constants. See
            // the ContactsQuery interface.
            return new CursorLoader(getActivity(),
                    ContactsContract.Data.CONTENT_URI,
                    ContactsQuery.PROJECTION,
                    ContactsQuery.SELECTION,
                    ContactsQuery.SELECTION_ARGS,
                    ContactsQuery.SORT_ORDER);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            // This swaps the new cursor into the adapter.
            Log.d(TAG, "delivered cursor with items: " + data.getCount());
            mChipsInput.setFilterableList(createFilteredList(data));
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            // When the loader is being reset, clear the cursor from the adapter. This allows the
            // cursor resources to be freed.
            mChipsInput.setFilterableList(createFilteredList(null));
        }
    }
}
