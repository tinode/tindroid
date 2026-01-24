package co.tinode.tindroid;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.TheCard;

/**
 * Fragment for previewing a vCard attachment before sending.
 */
public class VCardPreviewFragment extends Fragment {
    private static final String TAG = "VCardPreviewFragment";

    private ImageView mAvatarView;
    private TextView mFullNameView;
    private TextView mOrganizationView;
    private LinearLayout mNoteSection;
    private TextView mNoteView;
    private LinearLayout mContactsSection;
    private LinearLayout mContactsList;
    private ImageButton mSendButton;

    private TheCard mCard;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_vcard_preview, container, false);

        mAvatarView = view.findViewById(R.id.avatar);
        mFullNameView = view.findViewById(R.id.fullName);
        mOrganizationView = view.findViewById(R.id.organization);
        mNoteSection = view.findViewById(R.id.noteSection);
        mNoteView = view.findViewById(R.id.note);
        mContactsSection = view.findViewById(R.id.contactsSection);
        mContactsList = view.findViewById(R.id.contactsList);

        // Send message on button click.
        mSendButton = view.findViewById(R.id.chatSendButton);
        mSendButton.setOnClickListener(v -> sendVCard());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");
        Activity activity = requireActivity();
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.contact_card);
            toolbar.setSubtitle(null);
            toolbar.setLogo(null);
        }

        Uri uri = args.getParcelable(AttachmentHandler.ARG_LOCAL_URI);
        if (uri != null) {
            loadVCard(activity, uri);
        } else {
            mSendButton.setEnabled(false);
        }
    }

    private void loadVCard(@NonNull Activity activity, @NonNull Uri uri) {
        try {
            // Read vCard file content
            InputStream inputStream = activity.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open vCard file");
                mSendButton.setEnabled(false);
                return;
            }

            // Read all bytes from input stream
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            inputStream.close();

            String vCardContent = buffer.toString(StandardCharsets.UTF_8);
            mCard = TheCard.importVCard(vCardContent);

            if (mCard == null) {
                Log.e(TAG, "Failed to parse vCard");
                mSendButton.setEnabled(false);
                return;
            }

            displayCard(activity);
            mSendButton.setEnabled(true);

        } catch (Exception e) {
            Log.e(TAG, "Error loading vCard", e);
            mSendButton.setEnabled(false);
        }
    }

    private void displayCard(@NonNull Activity activity) {
        if (mCard == null) {
            return;
        }

        // Display full name
        String fullName = mCard.getFn();
        if (TextUtils.isEmpty(fullName)) {
            fullName = getString(R.string.unknown);
        }
        mFullNameView.setText(fullName);

        // Display organization
        String orgName = null;
        if (mCard.org != null && !TextUtils.isEmpty(mCard.org.fn)) {
            orgName = mCard.org.fn;
            if (!TextUtils.isEmpty(mCard.org.title)) {
                orgName += ", " + mCard.org.title;
            }
        }
        if (!TextUtils.isEmpty(orgName)) {
            mOrganizationView.setText(orgName);
            mOrganizationView.setVisibility(View.VISIBLE);
        } else {
            mOrganizationView.setVisibility(View.GONE);
        }

        // Display avatar
        displayAvatar(activity, fullName);

        // Display note
        if (!TextUtils.isEmpty(mCard.note)) {
            mNoteView.setText(mCard.note);
            mNoteSection.setVisibility(View.VISIBLE);
        } else {
            mNoteSection.setVisibility(View.GONE);
        }

        // Display contacts
        displayContacts(activity);
    }

    private void displayAvatar(@NonNull Activity activity, String fullName) {
        // Try to get photo from the card
        byte[] photoBits = mCard.getPhotoBits();
        String photoRef = mCard.getPhotoRef();

        if (photoBits != null && photoBits.length > 0 && !Tinode.isNull(photoBits)) {
            // Display avatar from inline data
            Bitmap bmp = BitmapFactory.decodeByteArray(photoBits, 0, photoBits.length);
            if (bmp != null) {
                RoundImageDrawable drawable = new RoundImageDrawable(activity.getResources(), bmp);
                mAvatarView.setImageDrawable(drawable);
                return;
            }
        }

        if (!TextUtils.isEmpty(photoRef) && !Tinode.isNull(photoRef)) {
            // Load avatar from URL
            try {
                URL url = Cache.getTinode().toAbsoluteURL(photoRef);
                if (url != null) {
                    // Create placeholder while loading
                    LetterTileDrawable placeholder = new LetterTileDrawable(activity);
                    placeholder.setContactTypeAndColor(LetterTileDrawable.ContactType.PERSON, false)
                            .setLetterAndColor(fullName, fullName, false)
                            .setIsCircular(true);

                    co.tinode.tindroid.widgets.RemoteRoundImageDrawable remoteAvatar =
                            new co.tinode.tindroid.widgets.RemoteRoundImageDrawable(
                                    activity, mAvatarView,
                                    (int) (72 * activity.getResources().getDisplayMetrics().density),
                                    placeholder);
                    remoteAvatar.load(url);
                    mAvatarView.setImageDrawable(remoteAvatar);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load remote avatar", e);
            }
        }

        // Fallback to letter tile
        LetterTileDrawable letterTile = new LetterTileDrawable(activity);
        letterTile.setContactTypeAndColor(LetterTileDrawable.ContactType.PERSON, false)
                .setLetterAndColor(fullName, fullName, false)
                .setIsCircular(true);
        mAvatarView.setImageDrawable(letterTile);
    }

    private void displayContacts(@NonNull Activity activity) {
        mContactsList.removeAllViews();
        boolean hasContacts = false;

        LayoutInflater inflater = LayoutInflater.from(activity);

        // Get communication methods
        List<TheCard.CommEntry> emails = mCard.getComm(TheCard.CommProto.EMAIL);
        List<TheCard.CommEntry> phones = mCard.getComm(TheCard.CommProto.TEL);
        List<TheCard.CommEntry> tinodeIds = mCard.getComm(TheCard.CommProto.TINODE);
        List<TheCard.CommEntry> urls = mCard.getComm(TheCard.CommProto.HTTP);

        // Add emails
        for (TheCard.CommEntry email : emails) {
            if (!TextUtils.isEmpty(email.value)) {
                addContactEntry(inflater, email.value, email.des, "✉");
                hasContacts = true;
            }
        }

        // Add phones
        for (TheCard.CommEntry phone : phones) {
            if (!TextUtils.isEmpty(phone.value)) {
                addContactEntry(inflater, phone.value, phone.des, "☎");
                hasContacts = true;
            }
        }

        // Add Tinode IDs
        for (TheCard.CommEntry tinode : tinodeIds) {
            if (!TextUtils.isEmpty(tinode.value)) {
                addContactEntry(inflater, tinode.value, tinode.des, "💬");
                hasContacts = true;
            }
        }

        // Add URLs
        for (TheCard.CommEntry url : urls) {
            if (!TextUtils.isEmpty(url.value)) {
                addContactEntry(inflater, url.value, url.des, "🔗");
                hasContacts = true;
            }
        }

        mContactsSection.setVisibility(hasContacts ? View.VISIBLE : View.GONE);
    }

    private void addContactEntry(LayoutInflater inflater, String value, TheCard.CommDes[] des, String icon) {
        // Create a simple layout for each contact entry
        LinearLayout entryLayout = new LinearLayout(requireContext());
        entryLayout.setOrientation(LinearLayout.HORIZONTAL);
        entryLayout.setPadding(0, 8, 0, 8);

        // Icon
        TextView iconView = new TextView(requireContext());
        iconView.setText(icon);
        iconView.setPadding(0, 0, 16, 0);
        entryLayout.addView(iconView);

        // Value and types
        LinearLayout textLayout = new LinearLayout(requireContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextAppearance(android.R.style.TextAppearance_Medium);
        textLayout.addView(valueView);

        // Display types if available
        if (des != null && des.length > 0) {
            StringBuilder types = new StringBuilder();
            for (int i = 0; i < des.length; i++) {
                if (i > 0) types.append(", ");
                types.append(des[i].toValue());
            }
            TextView typesView = new TextView(requireContext());
            typesView.setText(types.toString());
            typesView.setTextAppearance(android.R.style.TextAppearance_Small);
            typesView.setTextColor(requireContext().getResources().getColor(R.color.colorGray, null));
            textLayout.addView(typesView);
        }

        entryLayout.addView(textLayout);
        mContactsList.addView(entryLayout);
    }

    private void sendVCard() {
        final MessageActivity activity = (MessageActivity) requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final Bundle args = getArguments();
        if (args == null) {
            return;
        }

        // Send as vCard attachment
        AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity, AttachmentHandler.ARG_OPERATION_FILE, args);

        activity.getSupportFragmentManager().popBackStack();
    }
}

