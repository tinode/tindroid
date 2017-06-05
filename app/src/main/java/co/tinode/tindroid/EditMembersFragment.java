package co.tinode.tindroid;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.pchmn.materialchips.ChipsInput;
import com.pchmn.materialchips.model.Chip;
import com.pchmn.materialchips.model.ChipInterface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Fragment for editing group members
 */
public class EditMembersFragment extends Fragment implements UiUtils.ContactsLoaderResultReceiver {

    private static final String TAG = "EditMembersFragment";

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private Topic<?,?,String> mTopic;
    private HashMap<String, Boolean> mInitialMembers;

    private ChipsInput mChipsInput;

    // Sorted set of selected contacts (cursor positions of selected contacts).
    // private TreeSet<Integer> mSelectedContacts;

    public EditMembersFragment() {
        mInitialMembers = new HashMap<>();

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
                    }
                });
                return null;
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Log.d(TAG, "onCreateView");

        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_edit_members, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        final Activity activity = getActivity();

        mChipsInput = (ChipsInput) activity.findViewById(R.id.groupMembers);

        activity.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Chip> selected = (List<Chip>) mChipsInput.getSelectedChipList();
                // Log.d(TAG, "Chips count: " + selected.size() + " == " + mChipsInput.getChildCount());
                if (selected.size() == 0) {
                    Toast.makeText(activity, R.string.add_one_member, Toast.LENGTH_SHORT).show();
                }

                updateContacts(activity);
            }
        });

        getLoaderManager().initLoader(0, null,
                new UiUtils.ContactsLoaderCallback(getActivity(), this));
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        mTopic = Cache.getTinode().getTopic(bundle.getString("topic"));

        Collection<Subscription<VCard,String>> subs = (Collection) mTopic.getSubscriptions();
        Activity activity = getActivity();
        for (Subscription<VCard,String> sub : subs) {
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
            final String inviteText = activity.getString(R.string.invitation_text);
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
                mTopic.invite((String) chip.getId(), null /* use default */, inviteText).thenApply(null, mFailureListener);
            }

            ((MessageActivity) activity).showFragment(MessageActivity.FRAGMENT_INFO, false);

        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
            // Go back to contacts
        } catch (Exception e) {
            Log.d(TAG, "failed:", e);
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
