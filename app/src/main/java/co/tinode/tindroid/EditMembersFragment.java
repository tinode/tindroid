package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.media.VxCard;
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

    private MembersAdapter mSelectedAdapter;
    private ContactsAdapter mContactsAdapter;

    private ImageLoader mImageLoader;

    private ContactsLoaderCallback mContactsLoaderCallback;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstance) {
        setHasOptionsMenu(true);

        mImageLoader = UiUtils.getImageLoaderInstance(this);

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
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mContactsAdapter = new ContactsAdapter(activity, mImageLoader, new ContactsAdapter.ClickListener() {
            @Override
            public void onClick(final String unique, final ContactsAdapter.ViewHolder holder) {
                if (!mContactsAdapter.isSelected(unique)) {
                    mSelectedAdapter.append(unique, (String) holder.text1.getText(),
                            holder.getIconDrawable(), true);
                    mContactsAdapter.toggleSelected(unique);
                } else {
                    if (mSelectedAdapter.remove(unique)) {
                        mContactsAdapter.toggleSelected(unique);
                    }
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
        mTopic = (ComTopic<VxCard>) tinode.getTopic(args == null ? null : args.getString("topic"));
        final ArrayList<MembersAdapter.Member> members = new ArrayList<>();
        boolean cancelable = false;
        if (mTopic != null) {
            cancelable = mTopic.isAdmin();

            Collection<Subscription<VxCard, PrivateType>> subs = mTopic.getSubscriptions();
            if (subs != null) {
                boolean manager = mTopic.isManager();
                for (Subscription<VxCard, PrivateType> sub : subs) {
                    mContactsAdapter.toggleSelected(sub.user);
                    String name = null;
                    Bitmap avatar = null;
                    if (sub.pub != null) {
                        name = sub.pub.fn;
                        avatar = sub.pub.avatar == null ? null : sub.pub.avatar.getBitmap();
                    }
                    members.add(new MembersAdapter.Member(
                            sub.user,
                            name,
                            UiUtils.avatarDrawable(activity, avatar, name, sub.user),
                            !tinode.isMe(sub.user) && manager));
                }
            }
        }

        mSelectedAdapter = new MembersAdapter(members, new MembersAdapter.ClickListener() {
            @Override
            public void onClick(String unique) {
                // onClick is called after removing the item.
                mContactsAdapter.toggleSelected(unique);
            }
        }, cancelable);
        rv.setAdapter(mSelectedAdapter);

        // This button updates the group members.
        view.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateContacts(activity);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        restartLoader();
    }

    @Override
    public void onPause() {
        super.onPause();

        mImageLoader.setPauseWork(false);
    }

    // Restarts the loader. This triggers onCreateLoader(), which builds the
    // necessary content Uri from mSearchTerm.
    private void restartLoader() {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS)) {
            LoaderManager.getInstance(activity).restartLoader(LOADER_ID, null, mContactsLoaderCallback);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS},
                    UiUtils.CONTACTS_PERMISSION_ID);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == UiUtils.CONTACTS_PERMISSION_ID) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Sync p2p topics to Contacts.
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                UiUtils.onContactsPermissionsGranted(activity);
                // Permission is granted
                restartLoader();
            }
        }
    }
}
