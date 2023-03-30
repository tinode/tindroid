package co.tinode.tindroid;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.PhoneEdit;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.MsgSetMeta;

/**
 * Fragment for editing current user details.
 */
public class AccCredFragment extends Fragment implements ChatsActivity.FormUpdatable {
    private static final String TAG = "AccCredFragment";

    private String mMethod;
    private String mOldValue;
    private String mNewValue;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_acc_credential, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Bundle args = getArguments();
        if (args == null) {
            throw new IllegalArgumentException("AccCredFragment instantiated with no arguments");
        }

        mMethod = args.getString("method");
        mOldValue = args.getString("oldValue");
        mNewValue = args.getString("newValue");

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle("email".equals(mMethod) ? R.string.change_email :
                "tel".equals(mMethod) ? R.string.change_phone_num : R.string.change_credential);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        fragment.findViewById(R.id.requestCode).setOnClickListener(button -> handleAddCredential(activity, button));
        fragment.findViewById(R.id.confirm).setOnClickListener(button -> handleConfirmCredential(activity, button));

        return fragment;
    }

    @Override
    public void onResume() {
        updateFormValues(requireActivity(), null);

        super.onResume();
    }

    @Override
    public void updateFormValues(@NonNull final FragmentActivity activity, final MeTopic<VxCard> me) {
        int disableId = -1;
        int willSend = -1;
        if ("email".equals(mMethod)) {
            ((TextView) activity.findViewById(R.id.current_email)).setText(mOldValue);
            activity.findViewById(R.id.emailBlockWrapper).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.phoneBlockWrapper).setVisibility(View.GONE);
            ((TextView) activity.findViewById(R.id.email)).setText(mNewValue);
            disableId = R.id.email;
            willSend = R.id.will_send_email;
        } else if ("tel".equals(mMethod)) {
            ((TextView) activity.findViewById(R.id.current_phone)).setText(PhoneEdit.formatIntl(mOldValue));
            activity.findViewById(R.id.phoneBlockWrapper).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.emailBlockWrapper).setVisibility(View.GONE);
            ((PhoneEdit) activity.findViewById(R.id.phone)).setText(mNewValue);
            disableId = R.id.phone;
            willSend = R.id.will_send_sms;
        }

        if (!TextUtils.isEmpty(mNewValue)) {
            activity.findViewById(disableId).setEnabled(false);
            activity.findViewById(R.id.codeWrapper).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.requestCode).setVisibility(View.GONE);
            activity.findViewById(R.id.confirm).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.code_sent).setVisibility(View.VISIBLE);
            activity.findViewById(willSend).setVisibility(View.GONE);
        } else {
            activity.findViewById(disableId).setEnabled(true);
            activity.findViewById(R.id.codeWrapper).setVisibility(View.INVISIBLE);
            activity.findViewById(R.id.requestCode).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.confirm).setVisibility(View.GONE);
            activity.findViewById(R.id.code_sent).setVisibility(View.GONE);
            activity.findViewById(willSend).setVisibility(View.VISIBLE);
        }
    }

    // Dialog for confirming a credential.
    private void handleConfirmCredential(@NonNull FragmentActivity activity, View button) {
        EditText editor = activity.findViewById(R.id.confirmationCode);
        String response = editor.getText().toString();
        if (TextUtils.isEmpty(response)) {
            editor.setError(activity.getString(R.string.invalid_confirmation_code));
            return;
        }

        button.setEnabled(false);
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
        //noinspection unchecked
        me.confirmCred(mMethod, response)
                .thenApply(new PromisedReply.SuccessListener() {
                    @Override
                    public PromisedReply onSuccess(Object result) {
                        // Delete old credential. Ignore failure here.
                        me.delCredential(mMethod, mOldValue);
                        activity.runOnUiThread(() -> {
                            activity.getSupportFragmentManager().popBackStack();
                            button.setEnabled(true);
                        });
                        return null;
                    }
                })
                .thenCatch(new UiUtils.ToastFailureListener(activity));
    }

    private void handleAddCredential(@NonNull FragmentActivity activity, View button) {
        final Credential cred;
        if (mMethod.equals("email")) {
            EditText editor = activity.findViewById(R.id.email);
            String raw = editor.getText().toString().trim().toLowerCase();
            cred = UiUtils.parseCredential(raw);
            if (cred == null) {
                editor.setError(activity.getString(R.string.email_required));
                return;
            }
        } else if (mMethod.equals("tel")) {
            PhoneEdit editor = activity.findViewById(R.id.phone);
            String raw = editor.getPhoneNumberE164();
            cred = UiUtils.parseCredential(raw);
            if (cred == null) {
                editor.setError(activity.getString(R.string.phone_number_required));
                return;
            }
        } else {
            Log.w(TAG, "Unknown cred method" + mMethod);
            return;
        }

        button.setEnabled(false);
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
        // noinspection unchecked
        me.setMeta(new MsgSetMeta.Builder().with(cred).build())
                .thenApply(new PromisedReply.SuccessListener() {
                    @Override
                    public PromisedReply onSuccess(Object result) {
                        activity.runOnUiThread(() -> {
                            button.setEnabled(true);
                            mNewValue = cred.val;
                            updateFormValues(activity, me);
                        });
                        return null;
                    }
                })
                .thenCatch(new UiUtils.ToastFailureListener(activity));
    }
}
