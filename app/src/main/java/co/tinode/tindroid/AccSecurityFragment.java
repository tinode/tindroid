package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;

/**
 * Fragment for editing current user details.
 */
public class AccSecurityFragment extends Fragment implements ChatsActivity.FormUpdatable {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();


        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_acc_security, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.security);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        return fragment;
    }

    @Override
    public void onResume() {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();

        if (me == null) {
            return;
        }

        // Show or hide [Banned Contacts] button.

        final AtomicInteger countBanned = new AtomicInteger(0);
        Cache.getTinode().getFilteredTopics(t -> {
            if (t.isUserType() && !t.isJoiner()) {
                countBanned.addAndGet(1);
            }
            return false;
        });

        if (countBanned.get() > 0) {
            activity.findViewById(R.id.bannedUsersPanel).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.buttonBlockedUsers).setOnClickListener(v -> {
                // Start ChatsActivity + Chats Fragment with Bundle "banned" = true.
                Intent intent = new Intent(activity, ChatsActivity.class);
                intent.putExtra(ChatsActivity.TAG_FRAGMENT_NAME, ChatsActivity.FRAGMENT_BANNED);
                activity.startActivity(intent);
            });
        } else {
            activity.findViewById(R.id.bannedUsersPanel).setVisibility(View.GONE);
        }

        // Attach listeners to editable form fields.

        activity.findViewById(R.id.buttonChangePassword).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder
                    .setTitle(R.string.change_password)
                    .setView(R.layout.dialog_password)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        TextView editor = ((AlertDialog) dialog).findViewById(R.id.enterPassword);
                        if (editor != null) {
                            String password = editor.getText().toString();
                            if (!TextUtils.isEmpty(password)) {
                                final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
                                changePassword(pref.getString(LoginActivity.PREFS_LAST_LOGIN, null),
                                        password);
                            } else {
                                Toast.makeText(activity, R.string.failed_empty_password,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        activity.findViewById(R.id.buttonLogout).setOnClickListener(v -> logout());

        activity.findViewById(R.id.buttonDeleteAccount).setOnClickListener(v -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setNegativeButton(android.R.string.cancel, null)
                    .setTitle(R.string.delete_account)
                    .setMessage(R.string.confirm_delete_account)
                    .setCancelable(true)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        Cache.getTinode().delCurrentUser(true);
                        activity.finish();
                    })
                    .show();
        });

        activity.findViewById(R.id.authPermissions)
                .setOnClickListener(v -> UiUtils.showEditPermissions(activity, me, me.getAuthAcsStr(), null,
                        Const.ACTION_UPDATE_AUTH, "O"));
        activity.findViewById(R.id.anonPermissions)
                .setOnClickListener(v -> UiUtils.showEditPermissions(activity, me, me.getAnonAcsStr(), null,
                        Const.ACTION_UPDATE_ANON, "O"));

        // Assign initial form values.
        updateFormValues(activity, me);

        super.onResume();
    }

    @Override
    public void updateFormValues(final FragmentActivity activity, final MeTopic<VxCard> me) {
        if (activity == null) {
            return;
        }

        if (me != null) {
            ((TextView) activity.findViewById(R.id.authPermissions)).setText(me.getAuthAcsStr());
            ((TextView) activity.findViewById(R.id.anonPermissions)).setText(me.getAnonAcsStr());
        }
    }

    private void changePassword(String login, String password) {
        final Activity activity = requireActivity();
        try {
            // TODO: update stored record on success
            Cache.getTinode().updateAccountBasic((String) null, login, password).thenApply(
                    null, new UiUtils.ToastFailureListener(activity)
            );
        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void logout() {
        final Activity activity = requireActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setNegativeButton(android.R.string.cancel, null)
                .setTitle(R.string.logout)
                .setMessage(R.string.confirm_logout)
                .setCancelable(true)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    UiUtils.doLogout(activity);
                    activity.finish();
                })
                .show();
    }
}
