package co.tinode.tindroid;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by gsokolov on 2/4/16.
 */
public class ContactsListAdapter extends BaseAdapter {

    private Context mContext;
    private List<Contact> mContactItems;

    public ContactsListAdapter(Context context, List<Contact> items) {
        mContext = context;
        mContactItems = items;
    }

    @Override
    public int getCount() {
        return mContactItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mContactItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        Contact c = mContactItems.get(position);

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.contact, null);
        }

        ((TextView) convertView.findViewById(R.id.contactName)).setText(c.pub.fn);
        ((TextView) convertView.findViewById(R.id.contactPriv)).setText(c.priv);

        int unread = c.seq - c.read;
        TextView unreadBadge = (TextView) convertView.findViewById(R.id.unreadCount);
        if (unread > 0) {
            unreadBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
            unreadBadge.setVisibility(View.VISIBLE);
        } else {
            unreadBadge.setVisibility(View.INVISIBLE);
        }

        // TODO(gene): set avatar

        ((ImageView) convertView.findViewById(R.id.online))
                .setColorFilter(c.online ?
                                Color.argb(255, 64, 192, 64) :
                                Color.argb(255, 192, 192, 192));

        return convertView;
    }
}
