package co.tinode.tindroid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Fragment for editing current user details.
 */
public class AccNotificationsFragment extends Fragment implements ChatsActivity.FormUpdatable {

    private static final String TAG = "AccNotificationsFragment";

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
            return null;
        }
        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_acc_notifications, container, false);
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

        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
        if (activity == null || me == null) {
            return;
        }

        // Incognito mode
        final Switch incognito = activity.findViewById(R.id.switchIncognitoMode);
        incognito.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                me.updateMode(isChecked ? "-P" : "+P").thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public <E extends Exception> PromisedReply<ServerMessage> onFailure(E err) {
                        Log.i(TAG, "Incognito mode: " + isChecked, err);
                        if (err instanceof NotConnectedException) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    }
                }).thenFinally(new PromisedReply.FinalListener() {
                    @Override
                    public void onFinally() {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                incognito.setChecked(me.isMuted());
                            }
                        });
                    }
                });
            }
        });

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);

        // Read receipts
        Switch ctrl = activity.findViewById(R.id.switchReadReceipts);
        ctrl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                pref.edit().putBoolean(UiUtils.PREF_READ_RCPT, isChecked).apply();
            }
        });
        ctrl.setChecked(pref.getBoolean(UiUtils.PREF_READ_RCPT, true));

        // Typing notifications.
        ctrl = activity.findViewById(R.id.switchTypingNotifications);
        ctrl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                pref.edit().putBoolean(UiUtils.PREF_TYPING_NOTIF, isChecked).apply();
            }
        });
        ctrl.setChecked(pref.getBoolean(UiUtils.PREF_TYPING_NOTIF, true));

        updateFormValues(activity, me);
    }

    @Override
    public void updateFormValues(final AppCompatActivity activity, final MeTopic<VxCard> me) {
        if (activity == null || me == null) {
            return;
        }

        // Incognito mode
        Switch ctrl = activity.findViewById(R.id.switchIncognitoMode);
        ctrl.setChecked(me.isMuted());
    }

        @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
    }
}
