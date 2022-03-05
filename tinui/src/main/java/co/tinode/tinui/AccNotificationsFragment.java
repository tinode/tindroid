package co.tinode.tinui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import co.tinode.tinui.media.VxCard;
import co.tinode.tinsdk.MeTopic;
import co.tinode.tinsdk.NotConnectedException;
import co.tinode.tinsdk.PromisedReply;
import co.tinode.tinsdk.model.ServerMessage;

/**
 * Fragment for editing current user details.
 */
public class AccNotificationsFragment extends Fragment implements ChatsActivity.FormUpdatable {

    private static final String TAG = "AccNotificationsFrag";

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
        toolbar.setNavigationOnClickListener(v -> activity.getSupportFragmentManager().popBackStack());

        return fragment;
    }

    @Override
    public void onResume() {
        final FragmentActivity activity = getActivity();
        final MeTopic<VxCard> me = Cache.getTinode().getMeTopic();
        if (activity == null || me == null) {
            return;
        }

        // Incognito mode
        final SwitchCompat incognito = activity.findViewById(R.id.switchIncognitoMode);
        incognito.setOnCheckedChangeListener((buttonView, isChecked) ->
                me.updateMode(isChecked ? "-P" : "+P")
                        .thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
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
                        activity.runOnUiThread(() -> incognito.setChecked(me.isMuted()));
                    }
                }));

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);

        // Read receipts
        SwitchCompat ctrl = activity.findViewById(R.id.switchReadReceipts);
        ctrl.setOnCheckedChangeListener((buttonView, isChecked) ->
                pref.edit().putBoolean(UiUtils.PREF_READ_RCPT, isChecked).apply());
        ctrl.setChecked(pref.getBoolean(UiUtils.PREF_READ_RCPT, true));

        // Typing notifications.
        ctrl = activity.findViewById(R.id.switchTypingNotifications);
        ctrl.setOnCheckedChangeListener((buttonView, isChecked) ->
                pref.edit().putBoolean(UiUtils.PREF_TYPING_NOTIF, isChecked).apply());
        ctrl.setChecked(pref.getBoolean(UiUtils.PREF_TYPING_NOTIF, true));

        updateFormValues(activity, me);

        super.onResume();
    }

    @Override
    public void updateFormValues(final FragmentActivity activity, final MeTopic<VxCard> me) {
        if (activity == null || me == null) {
            return;
        }

        // Incognito mode
        SwitchCompat ctrl = activity.findViewById(R.id.switchIncognitoMode);
        ctrl.setChecked(me.isMuted());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
    }
}
