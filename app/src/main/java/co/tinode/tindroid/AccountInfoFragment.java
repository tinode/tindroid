package co.tinode.tindroid;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for editing current user details.
 */
public class AccountInfoFragment extends Fragment {

    private static final String TAG = "AccountInfoFragment";

    public AccountInfoFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "AccountInfoFragment.onCreateView");

        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return  null;
        }
        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_account_info, container, false);
        Toolbar toolbar = fragment.findViewById(R.id.toolbar);
        activity.setSupportActionBar(toolbar);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setTitle(R.string.account_settings);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fm = getFragmentManager();
                if (fm != null) {
                    fm.popBackStack();
                }
            }
        });
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();

        MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
        if (me != null) {
            final AppCompatActivity activity = (AppCompatActivity) getActivity();

            final AppCompatImageView avatar = activity.findViewById(R.id.imageAvatar);
            final TextView title = activity.findViewById(R.id.topicTitle);
            title.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditAccountTitle();
                }
            });

            activity.findViewById(R.id.uploadAvatar).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiUtils.requestAvatar(AccountInfoFragment.this);
                }
            });

            VxCard pub = me.getPub();
            if (pub != null) {
                if (!TextUtils.isEmpty(pub.fn)) {
                    title.setText(pub.fn);
                    title.setTypeface(null, Typeface.NORMAL);
                    title.setTextIsSelectable(true);
                } else {
                    title.setText(R.string.placeholder_contact_title);
                    title.setTypeface(null, Typeface.ITALIC);
                    title.setTextIsSelectable(false);
                }
                final Bitmap bmp = pub.getBitmap();
                if (bmp != null) {
                    avatar.setImageDrawable(new RoundImageDrawable(bmp));
                }
            }
            final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);

            final Switch readrcpt = activity.findViewById(R.id.switchReadReceipts);
            readrcpt.setChecked(pref.getBoolean(UiUtils.PREF_READ_RCPT, true));
            readrcpt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    pref.edit().putBoolean(UiUtils.PREF_READ_RCPT, isChecked).apply();
                }
            });

            final Switch typing = activity.findViewById(R.id.switchTypingNotifications);
            typing.setChecked(pref.getBoolean(UiUtils.PREF_TYPING_NOTIF, true));
            typing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    pref.edit().putBoolean(UiUtils.PREF_TYPING_NOTIF, isChecked).apply();
                }
            });

            ((TextView) activity.findViewById(R.id.topicAddress)).setText(Cache.getTinode().getMyId());

            activity.findViewById(R.id.buttonChangePassword).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder
                            .setTitle(R.string.change_password)
                            .setView(R.layout.dialog_password)
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    TextView editor = (TextView)
                                            ((AlertDialog) dialog).findViewById(R.id.enterPassword);
                                    if (editor != null) {
                                        String password = editor.getText().toString();
                                        if (!TextUtils.isEmpty(password)) {
                                            changePassword(pref.getString(LoginActivity.PREFS_LAST_LOGIN, null),
                                                    password);
                                        } else {
                                            Toast.makeText(activity, R.string.failed_empty_password,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            });
            activity.findViewById(R.id.buttonLogout).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    logout();
                }
            });

            final TextView auth = activity.findViewById(R.id.authPermissions);
            auth.setText(me.getAuthAcsStr());
            auth.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            });
            final TextView anon = activity.findViewById(R.id.anonPermissions);
            anon.setText(me.getAnonAcsStr());
            anon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            });
        }
    }

    // Dialog for editing pub.fn and priv
    private void showEditAccountTitle() {
        final MeTopic<VxCard> me = (MeTopic<VxCard>) Cache.getTinode().getMeTopic();
        VxCard pub = me.getPub();
        final String title = pub == null ? null : pub.fn;
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_edit_account, null);
        builder.setView(editor).setTitle(R.string.edit_account);

        final EditText titleEditor = editor.findViewById(R.id.editTitle);
        titleEditor.setText(title);

        builder
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UiUtils.updateTitle(getActivity(), me, titleEditor.getText().toString(), null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void changePassword(String login, String password) {
        Log.d(TAG, "Change password: " + login + ", " + password);

        Activity activity = getActivity();
        try {
            // TODO: update stored record on success
            Cache.getTinode().updateAccountBasic(null, login, password).thenApply(
                    null, new UiUtils.ToastFailureListener(activity)
            );
        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void logout() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setNegativeButton(android.R.string.cancel, null)
                .setMessage(R.string.confirm_logout)
                .setCancelable(true)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BaseDb.getInstance().logout();
                        Cache.invalidate();
                        startActivity(new Intent(activity, LoginActivity.class));
                        activity.finish();
                    }
                })
                .show();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        final MeTopic me = Cache.getTinode().getMeTopic();
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.updateAvatar(getActivity(), me, data);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
    }
}
