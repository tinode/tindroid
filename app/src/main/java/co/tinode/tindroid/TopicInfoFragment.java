package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.widget.AppCompatImageView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.Collection;

import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Topic Info fragment.
 */
public class TopicInfoFragment extends ListFragment {

    private static final String TAG = "TopicInfoFragment";

    Topic<VCard,String,String> mTopic;
    private MembersAdapter mAdapter;

    public TopicInfoFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_info, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mAdapter = new MembersAdapter(getActivity());
        setListAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        String name = bundle.getString("topic");
        mTopic = Cache.getTinode().getTopic(name);
        
        mTopic.setListener(new Topic.Listener<VCard, String, String>() {
            @Override
            public void onSubsUpdated() {
                mAdapter.resetContent();
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
        Activity activity = getActivity();

        VCard pub = mTopic.getPub();
        if (pub != null) {
            if (!TextUtils.isEmpty(pub.fn)) {
                TextView title = (TextView) activity.findViewById(R.id.topicTitle);
                title.setText(pub.fn);
            }

            Bitmap bmp = pub.getBitmap();
            if (bmp != null) {
                AppCompatImageView avatar = (AppCompatImageView) activity.findViewById(R.id.imageAvatar);
                avatar.setImageBitmap(bmp);
            }
        }
        String priv = mTopic.getPriv();
        if (!TextUtils.isEmpty(priv)) {
            TextView subtitle = (TextView) activity.findViewById(R.id.topicSubtitle);
            subtitle.setText(priv);
        }

        mAdapter.resetContent();
    }

    @Override
    public void onPause() {
        super.onPause();

        mTopic.setListener(null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        Log.d(TAG, "onActivityCreated");

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Log.d(TAG, "Click, pos=" + position + ", id=" + id);
            }
        });
    }

    private class MembersAdapter extends BaseAdapter {

        Subscription<VCard,String>[] mItems;

        int mItemCount;
        Context mContext;

        MembersAdapter(Activity context) {
            mContext = context;
            mItems = new Subscription[8];
        }

        void resetContent() {
            Collection<Subscription<VCard,String>> c = mTopic.getSubscriptions();
            if (c != null) {
                mItemCount = c.size();
                mItems = c.toArray(mItems);
            } else {
                mItemCount = 0;
            }
            Log.d(TAG, "resetContent got " + mItemCount + " items");
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            return mItemCount;
        }

        @Override
        public Object getItem(int i) {
            return mItems[i];
        }

        @Override
        public long getItemId(int i) {
            return StoredSubscription.getId(mItems[i]);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View item = convertView;
            ViewHolder holder;
            if (item == null) {
                LayoutInflater inflater = (LayoutInflater) mContext
                        .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

                item = inflater.inflate(R.layout.group_member, parent, false);
                holder = new ViewHolder();
                holder.name = (TextView) item.findViewById(android.R.id.text1);
                holder.contactPriv = (TextView) item.findViewById(android.R.id.text2);
                holder.status = (TextView) item.findViewById(R.id.status);
                holder.icon = (AppCompatImageView) item.findViewById(android.R.id.icon);

                item.setTag(holder);
            } else {
                holder = (ViewHolder) item.getTag();
            }

            bindView(position, holder);

            return item;
        }

        void bindView(int position, ViewHolder holder) {
            final Subscription<VCard, String> sub = mItems[position];

            VCard pub = sub.pub;
            Bitmap bmp = null;
            if (pub != null) {
                holder.name.setText(pub.fn);
                bmp = pub.getBitmap();
            }

            holder.contactPriv.setText(sub.priv);
            holder.status.setText(sub.mode);

            if (bmp != null) {
                holder.icon.setImageDrawable(new RoundImageDrawable(bmp));
            } else {
                Drawable drw;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    drw = mContext.getResources().getDrawable(R.drawable.ic_person_circle, mContext.getTheme());
                } else {
                    drw = mContext.getResources().getDrawable(R.drawable.ic_person_circle);
                }
                if (drw != null) {
                    holder.icon.setImageDrawable(drw);
                }
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_topic_info, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private class ViewHolder {
        TextView name;
        TextView contactPriv;
        TextView status;
        AppCompatImageView icon;
    }
}
