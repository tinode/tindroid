package co.tinode.tindroid;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Fragment for editing group members
 */
public class EditMembersFragment extends Fragment implements UiUtils.ContactsLoaderResultReceiver {

    private static final String TAG = "EditMembersFragment";

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private ComTopic<VxCard> mTopic;
    private HashMap<String, Boolean> mInitialMembers = new HashMap<>();


    // Sorted set of selected contacts (cursor positions of selected contacts).
    // private TreeSet<Integer> mSelectedContacts;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_edit_members, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        mFailureListener = new UiUtils.ToastFailureListener(activity);
        mSelectedMembers = activity.findViewById(R.id.selectedMembers);

        activity.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Chip> selected = (List<Chip>) mChipsInput.getSelectedChipList();
                if (selected.size() == 0) {
                    Toast.makeText(activity, R.string.add_one_member, Toast.LENGTH_SHORT).show();
                }

                updateContacts(activity);
            }
        });

        LoaderManager.getInstance(activity).initLoader(0, null,
                new UiUtils.ContactsLoaderCallback(getActivity(), this));
    }

    @Override
    public void onResume() {
        super.onResume();

        final Activity activity = getActivity();
        final Bundle bundle = getArguments();
        if (activity == null || bundle == null) {
            return;
        }

        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(bundle.getString("topic"));

        Collection<Subscription<VxCard,PrivateType>> subs = mTopic.getSubscriptions();
        for (Subscription<VxCard,PrivateType> sub : subs) {
            mInitialMembers.put(sub.user, true);
            mChipsInput.addChip(sub.user,
                    new BitmapDrawable(activity.getResources(), sub.pub.getBitmap()), sub.pub.fn, null);
        }
    }

    @Override
    public void receiveResult(int id, Cursor data) {
        mChipsInput.setFilterableList(UiUtils.createChipsInputFilteredList(data));
    }

    private void updateContacts(final Activity activity) {
        try {
            final List<ChipInterface> list = (List<ChipInterface>) mChipsInput.getSelectedChipList();
            final List<ChipInterface> remove = new ArrayList<>(list.size());
            for (ChipInterface chip : list) {
                // If the member is present in HashMap then it's unchanged, remove it from the map
                // and from the list.
                if (mInitialMembers.remove((String) chip.getId()) != null) {
                    remove.add(chip);
                }
            }
            list.removeAll(remove);

            // The hash map contains members to delete, the list contains members to add.
            for (String key : mInitialMembers.keySet()) {
                mTopic.eject(key, false).thenApply(null, mFailureListener);
            }
            for (ChipInterface chip : list) {
                mTopic.invite((String) chip.getId(), null /* use default */).thenApply(null, mFailureListener);
            }

            ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_INFO, false, null);

        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
            // Go back to contacts
        } catch (Exception ex) {
            Log.w(TAG, "Failed to change member's status", ex);
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
