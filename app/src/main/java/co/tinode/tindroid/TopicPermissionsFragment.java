package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
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

import java.util.Collection;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.account.ContactsManager;
import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

import static android.app.Activity.RESULT_OK;

/**
 * Topic permissions fragment: p2p or a group topic.
 */
public class TopicPermissionsFragment extends Fragment {

    private static final String TAG = "TopicPermissionsFragment";

    private ComTopic<VxCard> mTopic;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tpc_tags_perm, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Set up listeners

        view.findViewById(R.id.permissionsSingle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.showEditPermissions(activity, mTopic, mTopic.getAccessMode().getWant(), null,
                        UiUtils.ACTION_UPDATE_SELF_SUB, "O");
            }
        });

        view.findViewById(R.id.authPermissions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.showEditPermissions(activity, mTopic, mTopic.getAuthAcsStr(), null,
                        UiUtils.ACTION_UPDATE_AUTH, "O");
            }
        });

        view.findViewById(R.id.anonPermissions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.showEditPermissions(activity, mTopic, mTopic.getAnonAcsStr(), null,
                        UiUtils.ACTION_UPDATE_ANON, "O");
            }
        });

        view.findViewById(R.id.userOne).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.showEditPermissions(activity, mTopic,
                        mTopic.getAccessMode().getWant(), null,
                        UiUtils.ACTION_UPDATE_SELF_SUB, "ASDO");
            }
        });

        view.findViewById(R.id.userTwo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.showEditPermissions(activity, mTopic,
                        mTopic.getSubscription(mTopic.getName()).acs.getGiven(),
                        mTopic.getName(),
                        UiUtils.ACTION_UPDATE_SUB, "ASDO");
            }
        });
    }

        @Override
    @SuppressWarnings("unchecked")
    // onResume sets up the form with values and views which do not change + sets up listeners.
    public void onResume() {
        super.onResume();

        final Activity activity = getActivity();
        final Bundle args = getArguments();

        if (activity == null || args == null) {
            return;
        }

        String name = args.getString("topic");
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);
        if (mTopic == null) {
            Log.d(TAG, "TopicPermissions resumed with null topic.");
            activity.finish();
            return;
        }

        final View defaultPermissions = activity.findViewById(R.id.defaultPermissionsWrapper);
        final View tagManager = activity.findViewById(R.id.tagsManagerWrapper);

        if (mTopic.isGrpType()) {
            // Group topic

            activity.findViewById(R.id.singleUserPermissions).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.p2pPermissions).setVisibility(View.GONE);

            if (mTopic.isOwner()) {
                tagManager.setVisibility(View.VISIBLE);
                tagManager.findViewById(R.id.buttonManageTags)
                        .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showEditTags();
                    }
                });

                LayoutInflater inflater = LayoutInflater.from(activity);
                FlexboxLayout tagsView = activity.findViewById(R.id.tagList);
                tagsView.removeAllViews();

                String[] tags = mTopic.getTags();
                if (tags != null) {
                    for (String tag : tags) {
                        TextView label = (TextView) inflater.inflate(R.layout.tag, tagsView, false);
                        label.setText(tag);
                        tagsView.addView(label);
                    }
                }

            } else {
                tagManager.setVisibility(View.GONE);
            }

            defaultPermissions.setVisibility(mTopic.isManager() ? View.VISIBLE : View.GONE);

        } else {
            // P2P topic
            tagManager.setVisibility(View.GONE);

            activity.findViewById(R.id.singleUserPermissions).setVisibility(View.GONE);
            activity.findViewById(R.id.p2pPermissions).setVisibility(View.VISIBLE);

            VxCard two = mTopic.getPub();
            ((TextView) activity.findViewById(R.id.userTwoLabel)).setText(two != null && two.fn != null ?
                    two.fn : mTopic.getName());

            defaultPermissions.setVisibility(View.GONE);
        }

        notifyContentChanged();
        notifyDataSetChanged();
    }

    // Dialog for editing tags.
    private void showEditTags() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        String[] tagArray = mTopic.getTags();
        String tags = tagArray != null ? TextUtils.join(", ", mTopic.getTags()) : "";

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        @SuppressLint("InflateParams")
        final View editor = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_edit_tags, null);
        builder.setView(editor).setTitle(R.string.tags_management);

        final EditText tagsEditor = editor.findViewById(R.id.editTags);
        tagsEditor.setText(tags);
        tagsEditor.setSelection(tags.length());
        builder
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String[] tags = UiUtils.parseTags(tagsEditor.getText().toString());
                        // noinspection unchecked
                        mTopic.setMeta(new MsgSetMeta(tags))
                                .thenCatch(new UiUtils.ToastFailureListener(activity));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void notifyDataSetChanged() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (!mTopic.isGrpType()) {
            Acs acs = mTopic.getAccessMode();
            if (acs != null) {
                ((TextView) activity.findViewById(R.id.userOne)).setText(acs.getWant());
            }
            Subscription sub = mTopic.getSubscription(mTopic.getName());
            if (sub != null && sub.acs != null) {
                ((TextView) activity.findViewById(R.id.userTwo))
                        .setText(sub.acs.getGiven());
            }
        }
    }

    // Called when topic description is changed.
    private void notifyContentChanged() {

        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Acs acs = mTopic.getAccessMode();
        ((TextView) activity.findViewById(R.id.permissionsSingle)).setText(acs == null ? "" : acs.getMode());

        ((TextView) activity.findViewById(R.id.authPermissions)).setText(mTopic.getAuthAcsStr());
        ((TextView) activity.findViewById(R.id.anonPermissions)).setText(mTopic.getAnonAcsStr());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // inflater.inflate(R.menu.menu_topic_info, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
}
