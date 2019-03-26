package co.tinode.tindroid;

import android.app.Activity;
import android.database.Cursor;
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
import co.tinode.tinodesdk.Topic;
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

    private MembersAdapter mSelectedAdapter;
    private ContactsAdapter mContactsAdapter;

    private ImageLoader mImageLoader;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstance) {
        setHasOptionsMenu(true);

        mImageLoader = UiUtils.getImageLoaderInstance(this);

        return inflater.inflate(R.layout.fragment_edit_members, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {

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
                            holder.icon.getDrawable(), true);
                    mContactsAdapter.toggleSelected(unique);
                } else {
                    if (mSelectedAdapter.remove(unique)) {
                        mContactsAdapter.toggleSelected(unique);
                    }
                }
            }
        });
        rv.setAdapter(mContactsAdapter);

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
            Collection<Subscription<VxCard, PrivateType>> subs = mTopic.getSubscriptions();
            for (Subscription<VxCard, PrivateType> sub : subs) {
                mContactsAdapter.toggleSelected(sub.user);
                String name = null;
                Bitmap avatar = null;
                if (sub.pub != null) {
                    name = sub.pub.fn;
                    avatar = sub.pub.avatar.getBitmap();
                }
                members.add(new MembersAdapter.Member(
                        sub.user,
                        name,
                        UiUtils.avatarDrawable(activity, avatar, name, sub.user),
                        !tinode.isMe(sub.user)));
            }
            cancelable = mTopic.isAdmin();
        }

        mSelectedAdapter = new MembersAdapter(members, new MembersAdapter.ClickListener() {
            @Override
            public void onClick(String unique) {
                // onClick is called after removing the item.
                mContactsAdapter.toggleSelected(unique);
            }
        }, cancelable);
        rv.setAdapter(mSelectedAdapter);

        // This button creates the new group.
        view.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateContacts(activity);
            }
        });

        LoaderManager.getInstance(activity).initLoader(0, null,
                new UiUtils.ContactsLoaderCallback(getActivity(), this));
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

        mImageLoader.setPauseWork(false);
    }

    @Override
    public void receiveResult(int id, Cursor data) {
        mContactsAdapter.resetContent(data, null);
    }

    private void updateContacts(final Activity activity) {
        try {
            for (String key : mSelectedAdapter.getAdded()) {
                mTopic.invite(key, null /* use default */).thenCatch(mFailureListener);
            }
            for (String key : mSelectedAdapter.getRemoved()) {
                mTopic.eject(key, false).thenCatch(mFailureListener);
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
