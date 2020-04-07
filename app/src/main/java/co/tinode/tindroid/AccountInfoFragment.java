package co.tinode.tindroid;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.MeTopic;

/**
 * Fragment for editing current user details.
 */
public class AccountInfoFragment extends Fragment implements ChatsActivity.FormUpdatable {

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
                activity.getSupportFragmentManager().popBackStack();
            }
        });

        fragment.findViewById(R.id.personal).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ChatsActivity)activity).showFragment(ChatsActivity.FRAGMENT_ACC_PERSONAL);
            }
        });

        fragment.findViewById(R.id.notifications).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ChatsActivity)activity).showFragment(ChatsActivity.FRAGMENT_ACC_NOTIFICATIONS);
            }
        });
        fragment.findViewById(R.id.security).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ChatsActivity)activity).showFragment(ChatsActivity.FRAGMENT_ACC_SECURITY);
            }
        });
        fragment.findViewById(R.id.help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ChatsActivity)activity).showFragment(ChatsActivity.FRAGMENT_ACC_HELP);
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

        // Assign initial form values.
        updateFormValues(activity, me);
    }

    @Override
    public void updateFormValues(final AppCompatActivity activity, final MeTopic<VxCard> me) {
        if (activity == null) {
            return;
        }

        ((TextView) activity.findViewById(R.id.topicAddress)).setText(Cache.getTinode().getMyId());

        String fn = null;
        if (me != null) {
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

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
    }
}
