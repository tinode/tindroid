package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.PhoneEdit;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Credential;

/**
 * Fragment for editing current user details.
 */
public class AccountInfoFragment extends Fragment implements ChatsActivity.FormUpdatable, MenuProvider {
    private final static String TAG = "AccountInfoFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_account_info, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Tinode tinode = Cache.getTinode();

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.account_settings);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        fragment.findViewById(R.id.buttonCopyID).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("account ID", tinode.getMyId()));
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        });

        fragment.findViewById(R.id.notifications).setOnClickListener(v ->
                ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_NOTIFICATIONS, null));
        fragment.findViewById(R.id.security).setOnClickListener(v ->
                ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_SECURITY, null));
        fragment.findViewById(R.id.help).setOnClickListener(v ->
                ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_HELP, null));

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this,
                getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();

        if (me == null) {
            return;
        }

        // Assign initial form values.
        updateFormValues(activity, me);

        super.onResume();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void updateFormValues(@NonNull final FragmentActivity activity, final MeTopic<VxCard> me) {
        String myID = Cache.getTinode().getMyId();
        View fragmentView = getView();
        if (fragmentView == null) {
            return;
        }

        ((TextView) fragmentView.findViewById(R.id.topicAddress)).setText(myID);

        String fn = null;
        String note = null;
        if (me != null) {
            VxCard pub = me.getPub();
            if (pub != null) {
                fn = pub.fn;
                note = pub.note;
            }
            UiUtils.setAvatar(fragmentView.findViewById(R.id.imageAvatar), pub, myID, false);

            fragmentView.findViewById(R.id.verified).setVisibility(me.isTrustedVerified() ? View.VISIBLE : View.GONE);
            fragmentView.findViewById(R.id.staff).setVisibility(me.isTrustedStaff() ? View.VISIBLE : View.GONE);
            fragmentView.findViewById(R.id.danger).setVisibility(me.isTrustedDanger() ? View.VISIBLE : View.GONE);

            Credential[] creds = me.getCreds();
            if (creds != null) {
                for (Credential cred : creds) {
                    if ("email".equals(cred.meth)) {
                        fragmentView.findViewById(R.id.emailWrapper).setVisibility(View.VISIBLE);
                        ((TextView) fragmentView.findViewById(R.id.email)).setText(cred.val);
                    } else if ("tel".equals(cred.meth)) {
                        fragmentView.findViewById(R.id.phoneWrapper).setVisibility(View.VISIBLE);
                        ((TextView) fragmentView.findViewById(R.id.phone)).setText(PhoneEdit.formatIntl(cred.val));
                    } else {
                        // TODO: create generic field for displaying credential as text.
                        Log.w(TAG, "Unknown credential method " + cred.meth);
                    }
                }
            }

            String alias = me.alias();
            if (TextUtils.isEmpty(alias)) {
                fragmentView.findViewById(R.id.aliasWrapper).setVisibility(View.GONE);
            } else {
                fragmentView.findViewById(R.id.aliasWrapper).setVisibility(View.VISIBLE);
                ((TextView) fragmentView.findViewById(R.id.alias)).setText("@" + alias);
            }
        }

        final TextView title = fragmentView.findViewById(R.id.topicTitle);
        if (!TextUtils.isEmpty(fn)) {
            title.setText(fn);
            title.setTypeface(null, Typeface.NORMAL);
        } else {
            title.setText(R.string.placeholder_contact_title);
            title.setTypeface(null, Typeface.ITALIC);
        }

        if (!TextUtils.isEmpty(note)) {
            ((TextView) fragmentView.findViewById(R.id.topicDescription)).setText(note);
            fragmentView.findViewById(R.id.topicDescriptionWrapper).setVisibility(View.VISIBLE);
        } else {
            fragmentView.findViewById(R.id.topicDescriptionWrapper).setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_edit, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            FragmentActivity activity = requireActivity();
            if (activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }

            ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_PERSONAL, null);
            return true;
        }
        return false;
    }
}
