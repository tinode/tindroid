package co.tinode.tindroid;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
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
        mMethod = args.getString("method");
        mOldValue = args.getString("oldValue");
        mNewValue = args.getString("newValue");

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle("email".equals(mMethod) ? R.string.change_email :
                "tel".equals(mMethod) ? R.string.change_phone_num : R.string.change_credential);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

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
        if ("email".equals(mMethod)) {
            ((TextView) activity.findViewById(R.id.current_email)).setText(mOldValue);
            activity.findViewById(R.id.emailBlockWrapper).setVisibility(View.VISIBLE);
            ((TextView) activity.findViewById(R.id.email)).setText(mNewValue);
            disableId = R.id.email;
        } else if ("tel".equals(mMethod)) {
            ((TextView) activity.findViewById(R.id.current_phone)).setText(mOldValue);
            activity.findViewById(R.id.phoneBlockWrapper).setVisibility(View.VISIBLE);
            ((TextView) activity.findViewById(R.id.phone)).setText(mNewValue);
            disableId = R.id.phone;
        }

        if (!TextUtils.isEmpty(mNewValue)) {
            activity.findViewById(disableId).setEnabled(false);
            activity.findViewById(R.id.codeWrapper).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.requestCode).setVisibility(View.GONE);
            activity.findViewById(R.id.confirm).setVisibility(View.VISIBLE);
        }
    }
}
