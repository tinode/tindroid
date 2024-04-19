package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.HorizontalListDivider;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Fragment for editing group members
 */
public class EditMembersFragment extends Fragment {

    private static final String TAG = "EditMembersFragment";
    private static final int LOADER_ID = 103;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private ComTopic<VxCard> mTopic;

    // Members of the group.
    private MembersAdapter mSelectedAdapter;
    // All contacts.
    private ContactsAdapter mContactsAdapter;

    private ContactsLoaderCallback mContactsLoaderCallback;

    // Break an endless loop of requesting permissions.
    // Ask for permissions only once.
    private boolean mPermissionsDenied = false;

    private final ActivityResultLauncher<String[]> mRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    // Check if all required permissions are granted.
                    if (!e.getValue()) {
                        mPermissionsDenied = true;
                        return;
                    }
                }
                // Permissions are granted.
                FragmentActivity activity = getActivity();
                UiUtils.onContactsPermissionsGranted(activity);
                mContactsAdapter.setContactsPermissionGranted();
                // Try to open the image selector again.
                restartLoader(activity);
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstance) {
        return inflater.inflate(R.layout.fragment_edit_members, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstance) {

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        mFailureListener = new UiUtils.ToastFailureListener(activity);

        // Recycler view with all available Tinode contacts.
        RecyclerView rv = view.findViewById(R.id.contact_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(false);
        rv.addItemDecoration(new HorizontalListDivider(activity));
        mContactsAdapter = new ContactsAdapter(activity, (position, unique, displayName, photoUri) -> {
            if (!mContactsAdapter.isSelected(unique)) {
                mSelectedAdapter.append(position, unique, displayName, photoUri);
                mContactsAdapter.toggleSelected(unique, position);
            } else {
                if (mSelectedAdapter.remove(unique)) {
                    mContactsAdapter.toggleSelected(unique, position);
                }
            }
        });
        rv.setAdapter(mContactsAdapter);

        mContactsLoaderCallback = new ContactsLoaderCallback(LOADER_ID, activity, mContactsAdapter);

        // Recycler view with selected contacts.
        rv = view.findViewById(R.id.selected_members);
        FlexboxLayoutManager lm = new FlexboxLayoutManager(activity);
        lm.setFlexDirection(FlexDirection.ROW);
        lm.setJustifyContent(JustifyContent.FLEX_START);
        rv.setLayoutManager(lm);
        rv.setHasFixedSize(false);

        final Bundle args = getArguments();
        final Tinode tinode = Cache.getTinode();
        // noinspection unchecked
        mTopic = (ComTopic<VxCard>) tinode.getTopic(args == null ? null :
                args.getString(Const.INTENT_EXTRA_TOPIC));
        final ArrayList<MembersAdapter.Member> members = new ArrayList<>();
        boolean cancelable = false;
        if (mTopic != null) {
            cancelable = mTopic.isApprover();

            Collection<Subscription<VxCard, PrivateType>> subs = mTopic.getSubscriptions();
            if (subs != null) {
                boolean manager = mTopic.isManager();
                for (Subscription<VxCard, PrivateType> sub : subs) {
                    mContactsAdapter.toggleSelected(sub.user, -1);
                    members.add(new MembersAdapter.Member(-1, sub.user, sub.pub,
                            !tinode.isMe(sub.user) && manager));
                }
            }
        }

        mSelectedAdapter = new MembersAdapter(members, (unique, pos) -> {
            // onClick is called after removing the item.
            mContactsAdapter.toggleSelected(unique, pos);
        }, cancelable);
        rv.setAdapter(mSelectedAdapter);

        // This button updates the group members.
        view.findViewById(R.id.goNext).setOnClickListener(view1 -> updateContacts(activity));
    }

    @Override
    public void onResume() {
        super.onResume();

        restartLoader(getActivity());
    }

    // Restarts the loader. This triggers onCreateLoader(), which builds the
    // necessary content Uri from mSearchTerm.
    private void restartLoader(final FragmentActivity activity) {
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            return;
        }
        List<String> missing = UiUtils.getMissingPermissions(activity,
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS});
        if (missing.isEmpty()) {
            LoaderManager.getInstance(activity).restartLoader(LOADER_ID, null, mContactsLoaderCallback);
        } else if (!mPermissionsDenied) {
            mRequestPermissionLauncher.launch(missing.toArray(new String[]{}));
        }
    }

    private void updateContacts(final Activity activity) {
        try {
            for (String key : mSelectedAdapter.getAdded()) {
                mTopic.invite(key, null /* use default */).thenCatch(mFailureListener);
            }
            for (String key : mSelectedAdapter.getRemoved()) {
                mTopic.eject(key, false).thenCatch(mFailureListener);
            }

            ((MessageActivity) activity).getSupportFragmentManager().popBackStack();

        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
            // Go back to contacts
        } catch (Exception ex) {
            Log.w(TAG, "Failed to change member's status", ex);
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
