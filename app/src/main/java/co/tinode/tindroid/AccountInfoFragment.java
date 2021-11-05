package co.tinode.tindroid;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.MeTopic;

/**
 * Fragment for editing current user details.
 */
public class AccountInfoFragment extends Fragment implements ChatsActivity.FormUpdatable {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return null;
        }
        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_account_info, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.account_settings);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        fragment.findViewById(R.id.notifications).setOnClickListener(v ->
                ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_NOTIFICATIONS, null));
        fragment.findViewById(R.id.security).setOnClickListener(v ->
                ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_SECURITY, null));
        fragment.findViewById(R.id.help).setOnClickListener(v ->
                ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_HELP, null));

        return fragment;
    }

    @Override
    public void onResume() {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();

        if (me == null || activity == null) {
            return;
        }

        // Assign initial form values.
        updateFormValues(activity, me);

        super.onResume();
    }

    @Override
    public void updateFormValues(final FragmentActivity activity, final MeTopic<VxCard> me) {
        if (activity == null) {
            return;
        }

        String myID = Cache.getTinode().getMyId();
        ((TextView) activity.findViewById(R.id.topicAddress)).setText(myID);

        String fn = null;
        String note = null;
        if (me != null) {
            VxCard pub = me.getPub();
            if (pub != null) {
                fn = pub.fn;
                note = pub.note;
            }
            UiUtils.setAvatar(activity.findViewById(R.id.imageAvatar), pub, myID);

            activity.findViewById(R.id.verified).setVisibility(me.isTrustedVerified() ? View.VISIBLE : View.GONE);
            activity.findViewById(R.id.staff).setVisibility(me.isTrustedStaff() ? View.VISIBLE : View.GONE);
            activity.findViewById(R.id.danger).setVisibility(me.isTrustedDanger() ? View.VISIBLE : View.GONE);
        }

        final TextView title = activity.findViewById(R.id.topicTitle);
        if (!TextUtils.isEmpty(fn)) {
            title.setText(fn);
            title.setTypeface(null, Typeface.NORMAL);
        } else {
            title.setText(R.string.placeholder_contact_title);
            title.setTypeface(null, Typeface.ITALIC);
        }

        if (!TextUtils.isEmpty(note)) {
            ((TextView) activity.findViewById(R.id.topicDescription)).setText(note);
            activity.findViewById(R.id.topicDescriptionWrapper).setVisibility(View.VISIBLE);
        } else {
            activity.findViewById(R.id.topicDescriptionWrapper).setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_edit, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            FragmentActivity activity = getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }

            ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_PERSONAL, null);
            return true;
        }
        return false;
    }
}
