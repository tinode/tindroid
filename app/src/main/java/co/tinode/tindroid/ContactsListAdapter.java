package co.tinode.tindroid;

import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import co.tinode.tinodesdk.model.Subscription;

/**
 * Created by gsokolov on 2/4/16.
 */
public class ContactsListAdapter extends BaseAdapter {

    private Context mContext;
    private List<String> mContactItems;

    public ContactsListAdapter(Context context, List<String> items) {
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

        @SuppressWarnings("unchecked")
        Subscription<VCard,String> s = (Subscription) InmemoryCache.getTinode().getMeTopic()
                .getSubscription(mContactItems.get(position));

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.contact, null);
        }

        ((TextView) convertView.findViewById(R.id.contactName)).setText(s.pub.fn);
        ((TextView) convertView.findViewById(R.id.contactPriv)).setText(s.priv);

        int unread = s.seq - s.read;
        TextView unreadBadge = (TextView) convertView.findViewById(R.id.unreadCount);
        if (unread > 0) {
            unreadBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
            unreadBadge.setVisibility(View.VISIBLE);
        } else {
            unreadBadge.setVisibility(View.INVISIBLE);
        }
        Bitmap bmp = s.pub != null ? s.pub.getBitmap() : null;
        if (bmp != null) {
            ImageView avatar = (ImageView) convertView.findViewById(R.id.avatar);
            avatar.setImageDrawable(new RoundedImage(bmp));
        }

        ((ImageView) convertView.findViewById(R.id.online))
                .setColorFilter(s.online ?
                        Color.argb(255, 64, 192, 64) :
                        Color.argb(255, 192, 192, 192));

        return convertView;
    }
}
