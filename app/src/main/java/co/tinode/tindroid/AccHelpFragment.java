package co.tinode.tindroid;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

/**
 * Fragment for editing current user details.
 */
public class AccHelpFragment extends Fragment {
    private static final String TAG = "AccHelpFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_acc_help, container, false);
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.help);
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        BrandingConfig config = BrandingConfig.getConfig(activity);

        // Make policy links clickable.
        makeViewClickable(activity, fragment.findViewById(R.id.contactUs), R.string.contact_us,
                config != null && !TextUtils.isEmpty(config.contact_us_uri) ?
                        config.contact_us_uri : getString(R.string.contact_us_uri));
        makeViewClickable(activity, fragment.findViewById(R.id.termsOfUse), R.string.terms_of_use,
                config != null && !TextUtils.isEmpty(config.tos_uri) ?
                        config.tos_uri : getString(R.string.terms_of_use_uri));
        makeViewClickable(activity, fragment.findViewById(R.id.privacyPolicy), R.string.privacy_policy,
                config != null && !TextUtils.isEmpty(config.privacy_uri) ?
                        config.privacy_uri : getString(R.string.privacy_policy_uri));

        fragment.findViewById(R.id.aboutTheApp).setOnClickListener(v ->
                ((ChatsActivity) activity).showFragment(ChatsActivity.FRAGMENT_ACC_ABOUT, null));

        fragment.findViewById(R.id.ossLicenses).setOnClickListener(v -> {
            activity.startActivity(new Intent(activity, OssLicensesMenuActivity.class));
            OssLicensesMenuActivity.setActivityTitle(getString(R.string.licenses));
        });

        return fragment;
    }

    private void makeViewClickable(AppCompatActivity activity, TextView link,
                                   @StringRes int string_id, String uriString) {
        final Uri uri = Uri.parse(uriString);
        if (uri == null) {
            return;
        }

        Resources res = getResources();
        SpannableStringBuilder text = new SpannableStringBuilder(res.getString(string_id));
        text.setSpan(new ForegroundColorSpan(res.getColor(R.color.colorAccent, activity.getTheme())),
                0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new UnderlineSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        link.setText(text);
        link.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                Log.w(TAG, "No application can open the URL");
            }
        });
    }
}
