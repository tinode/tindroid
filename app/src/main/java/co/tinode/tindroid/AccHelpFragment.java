package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

/**
 * Fragment for editing current user details.
 */
public class AccHelpFragment extends Fragment {

    private static final String TAG = "AccountHelpFragment";

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
        View fragment = inflater.inflate(R.layout.fragment_acc_help, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.help);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.getSupportFragmentManager().popBackStack();
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

        fragment.findViewById(R.id.aboutTheApp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ChatsActivity)activity).showFragment(ChatsActivity.FRAGMENT_ACC_ABOUT);
            }
        });

        fragment.findViewById(R.id.ossLicenses).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startActivity(new Intent(activity, OssLicensesMenuActivity.class));
                OssLicensesMenuActivity.setActivityTitle(getString(R.string.licenses));
            }
        });

        return fragment;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
    }
}
