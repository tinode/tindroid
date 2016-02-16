package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * Created by gsokolov on 2/5/16.
 */
public class MessagesListAdapter extends BaseAdapter {

    // Additional padding on the left of reply (left) bubbles
    private static final int LEFT_PADDING = 20;

    private static final int[] sMaterialColors = {

    };

    private Context mContext;
    private Topic mTopic;

    public MessagesListAdapter(Context context, String topicName) {
        mContext = context;
        mTopic = InmemoryCache.getTinode().getTopic(topicName);
    }

    @Override
    public int getCount() {
        return mTopic.getMessageCount();
    }

    @Override
    public Object getItem(int position) {
        return mTopic.getMessageAt(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        @SuppressWarnings("unchecked")
        MsgServerData<String> m = mTopic.getMessageAt(position);

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.message, null);
        }

        ((TextView) convertView.findViewById(R.id.messageText)).setText(m.content);
        ((TextView) convertView.findViewById(R.id.messageMeta)).setText(m.isMine ? "Mine" : "Not");
        ((LinearLayout) convertView.findViewById(R.id.container))
                .setGravity(m.isMine ? Gravity.RIGHT : Gravity.LEFT);
        View bubble = convertView.findViewById(R.id.messageBubble);
        bubble.
            setBackgroundResource(m.isMine ? R.drawable.bubble_right : R.drawable.bubble_left);
        if (!m.isMine) {
            bubble.setPadding(LEFT_PADDING, bubble.getPaddingTop(),
                    bubble.getPaddingRight(), bubble.getPaddingBottom());
        }
        int[] colors = {0xff00ffc0, 0xff00c0ff, 0xffc000ff};
        bubble.getBackground().mutate()
                .setColorFilter(colors[position % 3], PorterDuff.Mode.MULTIPLY);

        return convertView;
    }
}
