package co.tinode.tindroid;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.flexbox.FlexboxLayout;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.model.Credential;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for editing current user details.
 */
public class AccountInfoFragment extends Fragment {

    private static final String TAG = "AccountInfoFragment";

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
            return  null;
        }
        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_account_info, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
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
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();

        if (me == null || activity == null) {
            return;
        }

        // Attach listeners to editable form fields.

        activity.findViewById(R.id.topicTitle).setOnClickListener(new View.OnClickListener() {
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

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        ((Switch) activity.findViewById(R.id.switchReadReceipts))
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        pref.edit().putBoolean(UiUtils.PREF_READ_RCPT, isChecked).apply();
                    }
                });

        ((Switch) activity.findViewById(R.id.switchTypingNotifications))
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            pref.edit().putBoolean(UiUtils.PREF_TYPING_NOTIF, isChecked).apply();
                    }
                });

        activity.findViewById(R.id.buttonManageTags).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        activity.findViewById(R.id.buttonAddContact).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

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
                                TextView editor = ((AlertDialog) dialog).findViewById(R.id.enterPassword);
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

        activity.findViewById(R.id.authPermissions)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        UiUtils.showEditPermissions(activity, me, me.getAuthAcsStr(), null,
                                UiUtils.ACTION_UPDATE_AUTH, "O");

                    }
                });
        activity.findViewById(R.id.anonPermissions)
                .setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                        UiUtils.showEditPermissions(activity, me, me.getAnonAcsStr(), null,
                                UiUtils.ACTION_UPDATE_ANON, "O");

                    }
                });

        // Assign initial form values.
        updateFormValues(activity, me);
    }

    void updateFormValues(final Activity activity, final MeTopic<VxCard> me) {
        if (activity== null) {
            return;
        }

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);

        ((Switch)activity.findViewById(R.id.switchReadReceipts))
                .setChecked(pref.getBoolean(UiUtils.PREF_READ_RCPT, true));

        ((Switch) activity.findViewById(R.id.switchTypingNotifications))
                .setChecked(pref.getBoolean(UiUtils.PREF_TYPING_NOTIF, true));

        ((TextView) activity.findViewById(R.id.topicAddress)).setText(Cache.getTinode().getMyId());


        String fn = null;
        if (me != null) {
            ((TextView) activity.findViewById(R.id.authPermissions)).setText(me.getAuthAcsStr());
            ((TextView) activity.findViewById(R.id.anonPermissions)).setText(me.getAnonAcsStr());

            LayoutInflater inflater = LayoutInflater.from(activity);

            String[] tags = me.getTags();
            if (tags != null) {
                FlexboxLayout tagsView = activity.findViewById(R.id.tagList);
                tagsView.removeAllViews();
                for (String tag : tags) {
                    TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                    label.setText(tag);
                    tagsView.addView(label);
                }
            }

            Credential[] creds = me.getCreds();
            if (creds != null) {
                LinearLayout credList = activity.findViewById(R.id.credList);
                for (Credential cred : creds) {
                    View container = inflater.inflate(R.layout.credential, credList, false);
                    ((TextView) container.findViewById(R.id.method)).setText(cred.meth);
                    ((TextView) container.findViewById(R.id.value)).setText(cred.val);
                    container.findViewById(R.id.buttonConfirm).setVisibility(cred.done ? View.GONE : View.VISIBLE);
                    container.findViewById(R.id.buttonDelete).setOnClickListener(null);
                    credList.addView(container, 0);
                }
            }

            VxCard pub = me.getPub();
            if (pub != null) {
                fn = pub.fn;
                final Bitmap bmp = pub.getBitmap();
                if (bmp != null) {
                    ((AppCompatImageView) activity.findViewById(R.id.imageAvatar))
                            .setImageDrawable(new RoundImageDrawable(getResources(), bmp));
                }
            }
        }

        final TextView title = activity.findViewById(R.id.topicTitle);
        if (!TextUtils.isEmpty(fn)) {
            title.setText(fn);
            title.setTypeface(null, Typeface.NORMAL);
            title.setTextIsSelectable(true);
        } else {
            title.setText(R.string.placeholder_contact_title);
            title.setTypeface(null, Typeface.ITALIC);
            title.setTextIsSelectable(false);
        }
    }

    // Dialog for editing pub.fn and priv
    private void showEditAccountTitle() {
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
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
                        UiUtils.updateTitle(activity, me, titleEditor.getText().toString(), null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void changePassword(String login, String password) {
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
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final MeTopic me = Cache.getTinode().getMeTopic();
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.updateAvatar(activity, me, data);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
    }
}
