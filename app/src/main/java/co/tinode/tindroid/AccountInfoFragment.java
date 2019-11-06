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

import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
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
import co.tinode.tinodesdk.model.MsgSetMeta;

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

        // Make policy links clickable.
        MovementMethod movementInstance = LinkMovementMethod.getInstance();
        TextView link = fragment.findViewById(R.id.contactUs);
        link.setText(Html.fromHtml(getString(R.string.contact_us)));
        link.setMovementMethod(movementInstance);
        link = fragment.findViewById(R.id.termsOfUse);
        link.setText(Html.fromHtml(getString(R.string.terms_of_use)));
        link.setMovementMethod(movementInstance);
        link = fragment.findViewById(R.id.privacyPolicy);
        link.setText(Html.fromHtml(getString(R.string.privacy_policy)));
        link.setMovementMethod(movementInstance);

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
                showEditTags();
            }
        });

        activity.findViewById(R.id.buttonAddContact).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddCredential();
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

            FlexboxLayout tagsView = activity.findViewById(R.id.tagList);
            tagsView.removeAllViews();

            String[] tags = me.getTags();
            if (tags != null) {
                for (String tag : tags) {
                    TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                    label.setText(tag);
                    tagsView.addView(label);
                }
            }

            LinearLayout credList = activity.findViewById(R.id.credList);
            while (credList.getChildCount() > 2) {
                View v = credList.getChildAt(1);
                if (v instanceof LinearLayout) {
                    credList.removeViewAt(1);
                } else {
                    break;
                }
            }

            Credential[] creds = me.getCreds();
            if (creds != null) {
                for (Credential cred : creds) {
                    View container = inflater.inflate(R.layout.credential, credList, false);
                    ((TextView) container.findViewById(R.id.method)).setText(cred.meth);
                    ((TextView) container.findViewById(R.id.value)).setText(cred.val);
                    Button btn = container.findViewById(R.id.buttonConfirm);
                    if (cred.isDone()) {
                        btn.setVisibility(View.GONE);
                    } else {
                        btn.setVisibility(View.VISIBLE);
                        btn.setTag(cred);
                    }
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Credential cred = (Credential) view.getTag();
                            showConfirmCredential(cred.meth, cred.val);
                        }
                    });

                    ImageButton ibtn = container.findViewById(R.id.buttonDelete);
                    ibtn.setTag(cred);
                    ibtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Credential cred = (Credential) view.getTag();
                            showDeleteCredential(cred.meth, cred.val);
                        }
                    });
                    credList.addView(container, 1);
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
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
        VxCard pub = me.getPub();
        final String title = pub == null ? null : pub.fn;

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_edit_account, null);
        builder.setView(editor).setTitle(R.string.edit_account);

        final EditText titleEditor = editor.findViewById(R.id.editTitle);
        titleEditor.setText(title);

        builder
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UiUtils.updateTitle(activity, me, titleEditor.getText().toString().trim(), null,
                                new UiUtils.TitleUpdateCallbackInterface() {
                                    @Override
                                    public void onTitleUpdated() {
                                        activity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                final MeTopic me = Cache.getTinode().getMeTopic();
                                                updateFormValues(activity, me);
                                            }
                                        });
                                    }
                                });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Dialog for editing tags.
    private void showEditTags() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final MeTopic me = Cache.getTinode().getMeTopic();
        String[] tagArray = me.getTags();
        String tags = tagArray != null ? TextUtils.join(", ", tagArray) : "";

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_edit_tags, null);
        builder.setView(editor).setTitle(R.string.tags_management);

        final EditText tagsEditor = editor.findViewById(R.id.editTags);
        tagsEditor.setText(tags);
        builder
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String[] tags = UiUtils.parseTags(tagsEditor.getText().toString());
                        // noinspection unchecked
                        me.setMeta(new MsgSetMeta(tags))
                                .thenCatch(new UiUtils.ToastFailureListener(activity));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void changePassword(String login, String password) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

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

    // Dialog for confirming a credential.
    private void showConfirmCredential(final String meth, final String val) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_validate, null);
        builder.setView(editor).setTitle(R.string.validate_cred_title)
                .setMessage(getString(R.string.validate_cred, meth))
                // FIXME: check for empty input and refuse to dismiss the dialog.
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String response = ((EditText) editor.findViewById(R.id.response)).getText().toString();
                        if (TextUtils.isEmpty(response)) {
                            return;
                        }

                        final MeTopic me = Cache.getTinode().getMeTopic();
                        // noinspection unchecked
                        me.setMeta(new MsgSetMeta(new Credential(meth, null, response, null)))
                                .thenCatch(new UiUtils.ToastFailureListener(activity));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Show dialog for deleting credential
    private void showDeleteCredential(final String meth, final String val) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setNegativeButton(android.R.string.cancel, null)
                .setTitle(R.string.delete_credential_title)
                .setMessage(getString(R.string.delete_credential_confirmation, meth, val))
                .setCancelable(true)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final MeTopic me = Cache.getTinode().getMeTopic();
                        // noinspection unchecked
                        me.delCredential(meth, val)
                                .thenCatch(new UiUtils.ToastFailureListener(activity));
                    }
                })
                .show();
    }

    private void showAddCredential() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final View editor = LayoutInflater.from(activity).inflate(R.layout.dialog_add_credential, null);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(editor).setTitle(R.string.add_credential_title)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View button) {
                        EditText credEditor = editor.findViewById(R.id.editCredential);
                        String cred = credEditor.getText().toString().trim().toLowerCase();
                        if (TextUtils.isEmpty(cred)) {
                            return;
                        }
                        Credential parsed = UiUtils.parseCredential(cred);
                        if (parsed != null) {
                            final MeTopic me = Cache.getTinode().getMeTopic();
                            // noinspection unchecked
                            me.setMeta(new MsgSetMeta(parsed))
                                    .thenCatch(new UiUtils.ToastFailureListener(activity));

                            // Dismiss once everything is OK.
                            dialog.dismiss();
                        } else {
                            credEditor.setError(activity.getString(R.string.unrecognized_credential));
                        }
                    }
                });
            }
        });

        dialog.show();
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
        if (requestCode == UiUtils.ACTIVITY_RESULT_SELECT_PICTURE && resultCode == RESULT_OK) {
            // noinspection unchecked
            UiUtils.updateAvatar(activity, me, data);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
    }
}
