package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by gsokolov on 2/5/16.
 */
public class MessagesListAdapter extends BaseAdapter {

    private Context mContext;
    private List<Message<String>> mMessages;

    public MessagesListAdapter(Context context, List<Message<String>> items) {
        mContext = context;
        mMessages = items;
    }

    @Override
    public int getCount() {
        return mMessages.size();
    }

    @Override
    public Object getItem(int position) {
        return mMessages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message<String> m = mMessages.get(position);

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.message, null);
        }

        ((TextView) convertView.findViewById(R.id.messageText)).setText(m.content);

        int[] colors = {0xff00ffc0, 0xff00c0ff, 0xffc000ff};
        NinePatchDrawable np = (NinePatchDrawable) convertView
                .findViewById(R.id.contentWithBackground).getBackground();
        np.mutate().setColorFilter(colors[position % 3], PorterDuff.Mode.MULTIPLY); //(new

        return convertView;
    }
}
