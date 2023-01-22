package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.PhoneEdit;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Fragment for editing current user details.
 */
public class AccPersonalFragment extends Fragment
        implements ChatsActivity.FormUpdatable, UiUtils.AvatarPreviewer, MenuProvider {

    private final ActivityResultLauncher<Intent> mAvatarPickerLauncher =
            UiUtils.avatarPickerLauncher(this, this);

    private final ActivityResultLauncher<String[]> mRequestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String, Boolean> e : result.entrySet()) {
                    // Check if all required permissions are granted.
                    if (!e.getValue()) {
                        return;
                    }
                }

                FragmentActivity activity = requireActivity();
                // Try to open the image selector again.
                Intent launcher = UiUtils.avatarSelectorIntent(activity, null);
                if (launcher != null) {
                    mAvatarPickerLauncher.launch(launcher);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();
        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_acc_personal, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.general);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((MenuHost) requireActivity()).addMenuProvider(this,
                getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        final FragmentActivity activity = getActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();

        if (me == null || activity == null) {
            return;
        }

        // Attach listeners to editable form fields.

        activity.findViewById(R.id.uploadAvatar).setOnClickListener(v -> {
            Intent launcher = UiUtils.avatarSelectorIntent(activity, mRequestPermissionsLauncher);
            if (launcher != null) {
                mAvatarPickerLauncher.launch(launcher);
            }
        });

        activity.findViewById(R.id.buttonManageTags).setOnClickListener(view -> showEditTags());

        // Assign initial form values.
        updateFormValues(activity, me);

        super.onResume();
    }

    @Override
    public void updateFormValues(@NonNull final FragmentActivity activity, final MeTopic<VxCard> me) {
        String fn = null;
        String description = null;
        if (me != null) {
            Credential[] creds = me.getCreds();
            if (creds != null) {
                // We only support two emails and two phone numbers at a time.
                Credential email = null, email2 = null;
                Credential phone = null, phone2 = null;
                Bundle argsEmail = new Bundle(), argsPhone = new Bundle();
                argsEmail.putString("method", "email");
                argsPhone.putString("method", "tel");
                for (Credential cred : creds) {
                    if ("email".equals(cred.meth)) {
                        // If more than one credential of the same type then just use the last one.
                        if (!cred.isDone() || email != null) {
                            email2 = cred;
                        } else {
                            email = cred;
                        }
                    } else if ("tel".equals(cred.meth)) {
                        if (!cred.isDone() || phone != null) {
                            phone2 = cred;
                        } else {
                            phone = cred;
                        }
                    }
                }

                // Old (current) email.
                if (email == null) {
                    activity.findViewById(R.id.emailWrapper).setVisibility(View.GONE);
                } else {
                    activity.findViewById(R.id.emailWrapper).setVisibility(View.VISIBLE);
                    TextView emailField = activity.findViewById(R.id.email);
                    emailField.setText(email.val);
                    if (email2 != null && email2.isDone()) {
                        // Two confirmed credentials of the same method.
                        // Make the credential unclickable: can't modify email if two emails are already given.
                        emailField.setBackground(null);
                        // Allow deletion of any one, including the first.
                        AppCompatImageButton delete = activity.findViewById(R.id.emailDelete);
                        delete.setVisibility(View.VISIBLE);
                        delete.setTag(email);
                        delete.setOnClickListener(this::showDeleteCredential);
                    } else {
                        // Second email is either not present or unconfirmed.
                        activity.findViewById(R.id.emailDelete).setVisibility(View.INVISIBLE);
                        argsEmail.putString("oldValue", email.val);
                        if (email2 == null) {
                            emailField.setOnClickListener(this::showEditCredential);
                            emailField.setBackgroundResource(R.drawable.dotted_line);
                        } else {
                            emailField.setBackground(null);
                        }
                    }
                    emailField.setTag(argsEmail);
                }

                // New (unconfirmed) email, or a second confirmed email if something failed.
                if (email2 == null) {
                    activity.findViewById(R.id.emailNewWrapper).setVisibility(View.GONE);
                } else {
                    activity.findViewById(R.id.emailNewWrapper).setVisibility(View.VISIBLE);
                    TextView emailField2 = activity.findViewById(R.id.emailNew);
                    emailField2.setText(email2.val);
                    // Unconfirmed? Allow confirming.
                    if (!email2.isDone()) {
                        argsEmail.putString("newValue", email2.val);
                        activity.findViewById(R.id.unconfirmedEmail).setVisibility(View.VISIBLE);
                        emailField2.setOnClickListener(this::showEditCredential);
                        emailField2.setBackgroundResource(R.drawable.dotted_line);
                    } else {
                        // Confirmed: make it unclickable.
                        activity.findViewById(R.id.unconfirmedEmail).setVisibility(View.INVISIBLE);
                        emailField2.setBackground(null);
                    }
                    emailField2.setTag(argsEmail);

                    // Second credential can always be deleted.
                    AppCompatImageButton delete = activity.findViewById(R.id.emailNewDelete);
                    delete.setVisibility(View.VISIBLE);
                    delete.setTag(email2);
                    delete.setOnClickListener(this::showDeleteCredential);
                }

                // Old (current) phone.
                if (phone == null) {
                    activity.findViewById(R.id.phoneWrapper).setVisibility(View.GONE);
                } else {
                    activity.findViewById(R.id.phoneWrapper).setVisibility(View.VISIBLE);
                    TextView phoneField = activity.findViewById(R.id.phone);
                    phoneField.setText(PhoneEdit.formatIntl(phone.val));
                    if (phone2 != null && phone2.isDone()) {
                        // Two confirmed credentials of the same method.
                        // Make the credential unclickable: can't modify phone if two phones are already given.
                        phoneField.setBackground(null);
                        // Allow deletion of any one, including the first.
                        AppCompatImageButton delete = activity.findViewById(R.id.phoneDelete);
                        delete.setVisibility(View.VISIBLE);
                        delete.setTag(phone);
                        delete.setOnClickListener(this::showDeleteCredential);
                    } else {
                        // Second phone is either not present or unconfirmed.
                        activity.findViewById(R.id.phoneDelete).setVisibility(View.INVISIBLE);
                        argsPhone.putString("oldValue", phone.val);
                        if (phone2 == null) {
                            phoneField.setOnClickListener(this::showEditCredential);
                            phoneField.setBackgroundResource(R.drawable.dotted_line);
                        } else {
                            phoneField.setBackground(null);
                        }
                    }
                    phoneField.setTag(argsPhone);
                }

                // New (unconfirmed) phone, or a second confirmed phone if something failed.
                if (phone2 == null) {
                    activity.findViewById(R.id.phoneNewWrapper).setVisibility(View.GONE);
                } else {
                    activity.findViewById(R.id.phoneNewWrapper).setVisibility(View.VISIBLE);
                    TextView phoneField2 = activity.findViewById(R.id.phoneNew);
                    phoneField2.setText(PhoneEdit.formatIntl(phone2.val));
                    // Unconfirmed? Allow confirming.
                    if (!phone2.isDone()) {
                        argsPhone.putString("newValue", phone2.val);
                        activity.findViewById(R.id.unconfirmedPhone).setVisibility(View.VISIBLE);
                        phoneField2.setOnClickListener(this::showEditCredential);
                        phoneField2.setBackgroundResource(R.drawable.dotted_line);
                    } else {
                        // Confirmed: make it unclickable.
                        activity.findViewById(R.id.unconfirmedPhone).setVisibility(View.INVISIBLE);
                        phoneField2.setBackground(null);
                    }
                    phoneField2.setTag(argsPhone);

                    // Second credential can always be deleted.
                    AppCompatImageButton delete = activity.findViewById(R.id.phoneNewDelete);
                    delete.setVisibility(View.VISIBLE);
                    delete.setTag(phone2);
                    delete.setOnClickListener(this::showDeleteCredential);
                }
            }

            VxCard pub = me.getPub();
            UiUtils.setAvatar(activity.findViewById(R.id.imageAvatar), pub, Cache.getTinode().getMyId(), false);
            if (pub != null) {
                fn = pub.fn;
                description = pub.note;
            }

            FlexboxLayout tagsView = activity.findViewById(R.id.tagList);
            tagsView.removeAllViews();

            String[] tags = me.getTags();
            if (tags != null) {
                LayoutInflater inflater = activity.getLayoutInflater();
                for (String tag : tags) {
                    TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                    label.setText(tag);
                    tagsView.addView(label);
                    label.requestLayout();
                }
            }
            tagsView.requestLayout();
        }

        ((TextView) activity.findViewById(R.id.topicTitle)).setText(fn);
        ((TextView) activity.findViewById(R.id.topicDescription)).setText(description);
    }

    // Dialog for editing tags.
    private void showEditTags() {
        final Activity activity = requireActivity();

        final MeTopic me = Cache.getTinode().getMeTopic();
        String[] tagArray = me.getTags();
        String tags = tagArray != null ? TextUtils.join(", ", tagArray) : "";

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_edit_tags, null);
        builder.setView(editor).setTitle(R.string.tags_management);

        final EditText tagsEditor = editor.findViewById(R.id.editTags);
        tagsEditor.setText(tags);
        builder
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String[] tags1 = UiUtils.parseTags(tagsEditor.getText().toString());
                    // noinspection unchecked
                    me.setMeta(new MsgSetMeta.Builder().with(tags1).build())
                            .thenCatch(new UiUtils.ToastFailureListener(activity));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditCredential(View view) {
        final ChatsActivity activity = (ChatsActivity) requireActivity();
        activity.showFragment(ChatsActivity.FRAGMENT_ACC_CREDENTIALS, (Bundle) view.getTag());
    }

    // Show dialog for deleting credential
    private void showDeleteCredential(View view) {
        final Activity activity = requireActivity();
        Credential cred = (Credential) view.getTag();

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setNegativeButton(android.R.string.cancel, null)
                .setTitle(R.string.delete_credential_title)
                .setMessage(getString(R.string.delete_credential_confirmation, cred.meth, cred.val))
                .setCancelable(true)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    final MeTopic me = Cache.getTinode().getMeTopic();
                    // noinspection unchecked
                    me.delCredential(cred.meth, cred.val)
                            .thenCatch(new UiUtils.ToastFailureListener(activity));
                })
                .show();
    }

    @Override
    public void showAvatarPreview(final Bundle args) {
        final Activity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_AVATAR_PREVIEW, args);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_save, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            FragmentActivity activity = requireActivity();
            if (activity.isFinishing() || activity.isDestroyed()) {
                return false;
            }
            final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
            String title = ((TextView) activity.findViewById(R.id.topicTitle)).getText().toString().trim();
            String description = ((TextView) activity.findViewById(R.id.topicDescription)).getText().toString().trim();
            UiUtils.updateTopicDesc(me, title, null, description)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage unused) {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                activity.runOnUiThread(() -> activity.getSupportFragmentManager().popBackStack());
                            }
                            return null;
                        }
                    })
                    .thenCatch(new UiUtils.ToastFailureListener(activity));
            return true;
        }
        return false;
    }
}
