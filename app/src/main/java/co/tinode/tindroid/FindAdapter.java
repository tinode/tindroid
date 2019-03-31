package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Handling 'fnd' results.
 */
public class FindAdapter extends RecyclerView.Adapter<FindAdapter.ViewHolder> {

    @SuppressWarnings("unused")
    private static final String TAG = "FindAdapter";

    private List<Subscription<VxCard,String[]>> mFound;

    private SelectionTracker<String> mSelectionTracker;
    private ClickListener mClickListener;

    FindAdapter(ClickListener clickListener) {
        super();

        mClickListener = clickListener;

        setHasStableIds(true);
    }

    void resetContent(Activity activity) {
        Collection c = Cache.getTinode().getFndTopic().getSubscriptions();
        if (c == null) {
            mFound = new LinkedList<>();
        } else {
            // noinspection unchecked
            mFound = new LinkedList<>(c);
        }

        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE);
        return new ViewHolder(
                inflater.inflate(viewType, parent, false), mClickListener, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.viewType == R.layout.contact) {
            Subscription<VxCard, String[]> sub = mFound.get(position);
            holder.bind(position, sub, mSelectionTracker != null &&
                    mSelectionTracker.isSelected(sub.getUnique()));
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (getActualItemCount() == 0) {
            return R.layout.contact_empty;
        }

        return R.layout.contact;
    }

    @Override
    public long getItemId(int position) {
        if (getActualItemCount() == 0) {
            return "empty".hashCode();
        }
        // Content is transient. Use hashes.
        return mFound.get(position).getUnique().hashCode();
    }

    private int getActualItemCount() {
        return mFound.size();
    }

    @Override
    public int getItemCount() {
        int count = getActualItemCount();
        return count == 0 ? 1 : count;
    }

    private Subscription<VxCard,String[]> getItemAt(int pos) {
        if (getActualItemCount() == 0) {
            return null;
        }
        return mFound.get(pos);
    }

    void setSelectionTracker(SelectionTracker<String> selectionTracker) {
        mSelectionTracker = selectionTracker;
    }

    static class ContactDetails extends ItemDetailsLookup.ItemDetails<String> {
        int pos;
        String name;

        ContactDetails() {
        }

        @Override
        public int getPosition() {
            return pos;
        }

        @Nullable
        @Override
        public String getSelectionKey() {
            return name;
        }
    }

    static class ContactItemKeyProvider extends ItemKeyProvider<String> {
        private FindAdapter mAdapter;
        private final Map<String,Integer> mKeyToPosition;

        ContactItemKeyProvider(FindAdapter adapter) {
            super(SCOPE_CACHED);

            mAdapter = adapter;

            mKeyToPosition = new HashMap<>(mAdapter.getItemCount());

            for (int i = 0; i < mAdapter.getItemCount(); i++) {
                Subscription sub = mAdapter.getItemAt(i);
                if (sub != null) {
                    mKeyToPosition.put(sub.getUnique(), i);
                }
            }
        }

        @Nullable
        @Override
        public String getKey(int i) {
            return mAdapter.getItemAt(i).getUnique();
        }

        @Override
        public int getPosition(@NonNull String s) {
            Integer pos = mKeyToPosition.get(s);
            return pos == null ? -1 : pos;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        int viewType;
        TextView name;
        TextView contactPriv;
        AppCompatImageView icon;

        ContactDetails details;
        ClickListener clickListener;

        ViewHolder(@NonNull View item, ClickListener cl, int viewType) {
            super(item);

            this.viewType = viewType;
            if (viewType == R.layout.contact) {
                name = item.findViewById(R.id.contactName);
                contactPriv = item.findViewById(R.id.contactPriv);
                icon = item.findViewById(R.id.avatar);

                item.findViewById(R.id.online).setVisibility(View.GONE);
                item.findViewById(R.id.unreadCount).setVisibility(View.GONE);

                details = new ContactDetails();
                clickListener = cl;
            } else {
                details = null;
            }

        }

        ContactDetails getItemDetails(@SuppressWarnings("unused") MotionEvent motion) {
            return details;
        }

        void bind(int position, final Subscription<VxCard,String[]> sub, boolean selected) {
            final Context context = itemView.getContext();
            final String unique = sub.getUnique();

            details.pos = position;
            details.name = sub.getUnique();

            VxCard pub = sub.pub;
            if (pub != null) {
                name.setText(pub.fn);
                name.setTypeface(null, Typeface.NORMAL);
            } else {
                name.setText(R.string.placeholder_contact_title);
                name.setTypeface(null, Typeface.ITALIC);
            }
            if (sub.priv != null) {
                contactPriv.setText(TextUtils.join(", ", sub.priv));
            } else {
                contactPriv.setText("");
            }

            icon.setImageDrawable(
                    UiUtils.avatarDrawable(context,
                    pub != null ? pub.getBitmap() : null,
                    pub != null ? pub.fn : null,
                    unique));

            if (selected) {
                itemView.setBackgroundResource(R.drawable.contact_background);
                itemView.setOnClickListener(null);
            } else {

                TypedArray typedArray = context.obtainStyledAttributes(
                        new int[]{android.R.attr.selectableItemBackground});
                itemView.setBackgroundResource(typedArray.getResourceId(0, 0));
                typedArray.recycle();

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clickListener.onCLick(unique);
                    }
                });
            }

            itemView.setActivated(selected);
        }
    }

    interface ClickListener {
        void onCLick(String topicName);
    }
}
