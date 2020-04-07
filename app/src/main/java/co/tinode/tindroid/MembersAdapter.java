package co.tinode.tindroid;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.RecyclerView;

public class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.ViewHolder> {
    @SuppressWarnings("unused")
    private static final String TAG = "MembersAdapter";

    private ArrayList<String> mInitialMembers;
    private ArrayList<Member> mCurrentMembers;

    // mCancelable means initial items can be removed too.
    // Newly added members can always be removed.
    private boolean mCancelable;
    private ClickListener mOnCancel;

    MembersAdapter(@Nullable ArrayList<Member> users, @Nullable ClickListener onCancel,
                   boolean cancelable) {
        setHasStableIds(true);

        mCancelable = cancelable;
        mOnCancel = onCancel;

        mInitialMembers = new ArrayList<>();
        mCurrentMembers = new ArrayList<>();

        if (users != null) {
            for (Member user : users) {
                mInitialMembers.add(user.unique);
                mCurrentMembers.add(user);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false), viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.viewType == R.layout.group_member_chip) {
            holder.bind(position);
        }
    }

    private int getActualItemCount() {
        return mCurrentMembers.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (getActualItemCount() == 0) {
            return R.layout.group_member_chip_empty;
        }
        return R.layout.group_member_chip;
    }

    @Override
    public int getItemCount() {
        int count = getActualItemCount();
        return count == 0 ? 1 : count;
    }

    @Override
    public long getItemId(int pos) {
        if (getActualItemCount() == 0) {
            return "empty".hashCode();
        }
        return mCurrentMembers.get(pos).unique.hashCode();
    }

    boolean append(String unique, String name, Drawable icon, boolean removable) {
        return append(new Member(unique, name, icon, removable));
    }

    private boolean append(Member user) {
        // Ensure uniqueness.
        for (int i = 0; i < mCurrentMembers.size(); i++) {
            if (user.unique.equals(mCurrentMembers.get(i).unique)) {
                return false;
            }
        }

        mCurrentMembers.add(user);
        notifyItemInserted(getItemCount() - 1);

        return true;
    }

    boolean remove(@NonNull String unique) {
        if (!mCancelable) {
            // Check if the member is allowed to be removed.
            for (int i = 0; i < mInitialMembers.size(); i++) {
                if (unique.equals(mInitialMembers.get(i))) {
                    return false;
                }
            }
        }

        for (int i = 0; i < mCurrentMembers.size(); i++) {
            Member m = mCurrentMembers.get(i);
            if (unique.equals(m.unique) && m.removable) {
                mCurrentMembers.remove(i);
                notifyItemRemoved(i);
                return true;
            }
        }
        return false;
    }

    String[] getAdded() {
        ArrayList<String> added = new ArrayList<>();
        HashMap<String,Object> initial = new HashMap<>();
        for (String unique : mInitialMembers) {
            initial.put(unique, "");
        }
        for (Member user : mCurrentMembers) {
            if (!initial.containsKey(user.unique)) {
                added.add(user.unique);
            }
        }
        return added.toArray(new String[]{});
    }

    String[] getRemoved() {
        ArrayList<String> removed = new ArrayList<>();
        HashMap<String,Object> current = new HashMap<>();
        // Index current members by unique value.
        for (Member user : mCurrentMembers) {
            current.put(user.unique, "");
        }

        for (String unique : mInitialMembers) {
            if (!current.containsKey(unique)) {
                removed.add(unique);
            }
        }
        return removed.toArray(new String[]{});
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        int viewType;
        ImageView icon;
        TextView title;
        AppCompatImageButton close;

        ViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);

            this.viewType = viewType;
            if (viewType == R.layout.group_member_chip) {
                icon = itemView.findViewById(android.R.id.icon);
                title = itemView.findViewById(android.R.id.text1);
                close = itemView.findViewById(android.R.id.closeButton);
            }
        }

        void bind(int pos) {
            Member user = mCurrentMembers.get(pos);
            if (user.icon != null) {
                icon.setImageDrawable(user.icon);
            }
            title.setText(user.name);

            if (user.removable && (mCancelable || !mInitialMembers.contains(user.unique))) {
                close.setVisibility(View.VISIBLE);
                close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int pos = getAdapterPosition();
                        Member user = mCurrentMembers.remove(pos);
                        mOnCancel.onClick(user.unique);
                        notifyItemRemoved(pos);
                    }
                });
            } else {
                close.setVisibility(View.GONE);
            }
        }
    }

    static class Member {
        String unique;
        String name;
        Drawable icon;
        Boolean removable;

        Member(String unique, String name, Drawable icon) {
            this(unique, name, icon, false);
        }

        Member(String unique, String name, Drawable icon, boolean removable) {
            this.unique = unique;
            this.name = name != null ? name : unique;
            this.icon = icon;
            this.removable = removable;
        }
    }

    interface ClickListener {
        void onClick(String unique);
    }
}
