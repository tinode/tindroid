package co.tinode.tindroid;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
    // List of members before editing started.
    private HashMap<String, Boolean> mInitialMembers = new HashMap<>();
    private HashMap<String, Boolean> mCurrentMembers = new HashMap<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_edit_members, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        mFailureListener = new UiUtils.ToastFailureListener(activity);
        ChipGroup selectedMembers = view.findViewById(R.id.selected_members);

        activity.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentMembers.size() == 0) {
                    Toast.makeText(activity, R.string.add_one_member, Toast.LENGTH_SHORT).show();
                }

                updateContacts(activity);
            }
        });

        LoaderManager.getInstance(activity).initLoader(0, null,
                new UiUtils.ContactsLoaderCallback(getActivity(), this));

        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(bundle.getString("topic"));
        if (mTopic != null) {
            Collection<Subscription<VxCard, PrivateType>> subs = mTopic.getSubscriptions();
            for (Subscription<VxCard, PrivateType> sub : subs) {
                mInitialMembers.put(sub.getUnique(), true);
                mCurrentMembers.put(sub.getUnique(), true);
                selectedMembers.addView(UiUtils.makeChip(activity, sub));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void receiveResult(int id, Cursor data) {
        // TODO: update adapter.
    }

    private void updateContacts(final Activity activity) {
        try {
            for (String key : mCurrentMembers.keySet()) {
                if (!mInitialMembers.containsKey(key)) {
                    mTopic.invite(key, null /* use default */).thenCatch(mFailureListener);
                }
            }
            for (String key : mInitialMembers.keySet()) {
                if (!mCurrentMembers.containsKey(key)) {
                    mTopic.eject(key, false).thenCatch(mFailureListener);
                }
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
