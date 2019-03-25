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
    private ArrayList<Member> mInitialMembers;
    private ArrayList<Member> mCurrentMembers;

    // This means initial items can be removed too.
    // Newly added members can always be removed.
    private boolean mCancelable;
    private ClickListener mOnCancel;

    MembersAdapter(@Nullable ArrayList<Member> users, @Nullable ClickListener onCancel,
                   boolean cancelable) {
        mCancelable = cancelable;
        mOnCancel = onCancel;

        mInitialMembers = new ArrayList<>();
        mCurrentMembers = new ArrayList<>();

        if (users != null) {
            for (Member user : users) {
                mInitialMembers.add(user);
                mCurrentMembers.add(user);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.group_member_chip, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return mCurrentMembers.size();
    }

    boolean append(Member user) {
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
                if (unique.equals(mInitialMembers.get(i).unique)) {
                    return false;
                }
            }
        }

        for (int i = 0; i < mCurrentMembers.size(); i++) {
            if (unique.equals(mCurrentMembers.get(i).unique)) {
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
        for (Member user : mInitialMembers) {
            initial.put(user.unique, "");
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

        for (Member user : mInitialMembers) {
            if (!current.containsKey(user.unique)) {
                removed.add(user.unique);
            }
        }
        return removed.toArray(new String[]{});
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        AppCompatImageButton close;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            icon = itemView.findViewById(android.R.id.icon);
            title = itemView.findViewById(android.R.id.text1);
            close = itemView.findViewById(android.R.id.closeButton);
        }

        void bind(int pos) {
            Member user = mCurrentMembers.get(pos);
            if (user.icon != null) {
                icon.setImageDrawable(user.icon);
            }
            title.setText(user.name);

            if (mCancelable) {
                close.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int pos = getAdapterPosition();
                        Member user = mCurrentMembers.remove(pos);
                        notifyItemRemoved(pos);
                        mOnCancel.onClick(user.unique);
                    }
                });
            }
        }
    }

    static class Member {
        String unique;
        String name;
        Drawable icon;
    }

    interface ClickListener {
        void onClick(String unique);
    }
}
