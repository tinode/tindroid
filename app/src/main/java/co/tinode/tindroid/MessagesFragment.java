package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tindroid.db.MessageDb;
import co.tinode.tindroid.db.StoredMessage;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Fragment handling message display and message sending.
 */
public class MessagesFragment extends Fragment {
    private static final String TAG = "MessageFragment";

    private static final int MESSAGES_TO_LOAD = 20;
    private static final int MESSAGES_QUERY_ID = 100;

    // Delay before sending out a RECEIVED notification to be sure we are not sending too many.
    // private static final int RECV_DELAY = 500;
    private static final int READ_DELAY = 1000;

    private MessagesListAdapter mMessagesAdapter;
    private RecyclerView mMessageList;
    private MessageLoaderCallbacks mLoaderCallbacks;

    private String mTopicName = null;
    protected Topic<VCard, String, String> mTopic;

    private Timer mNoteTimer = null;
    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    public MessagesFragment() {
        mFailureListener = new PromisedReply.FailureListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onFailure(final Exception err) throws Exception {
                final Activity activity = getActivity();
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (err instanceof NotConnectedException) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                return null;
            }
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        final MessageActivity activity = (MessageActivity) getActivity();

        LinearLayoutManager lm = new LinearLayoutManager(activity);
        //lm.setReverseLayout(true);
        lm.setStackFromEnd(true);

        mMessageList = (RecyclerView) activity.findViewById(R.id.messages_container);
        mMessageList.setLayoutManager(lm);

        mMessagesAdapter = new MessagesListAdapter(activity);
        mMessageList.setAdapter(mMessagesAdapter);

        mLoaderCallbacks = new MessageLoaderCallbacks();

        // Send message on button click
        getActivity().findViewById(R.id.chatSendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
                runLoader();
            }
        });

        EditText editor = (EditText) activity.findViewById(R.id.editMessage);
        // Send message on Enter
        editor.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                sendMessage();
                return true;
            }
        });

        // Send notification on key presses
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @SuppressWarnings("unchecked")
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (count > 0 || before > 0) {
                    activity.sendKeyPress();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        mTopicName = bundle.getString("topic");
        String messageToSend = bundle.getString("messageText");

        Log.d(TAG, "Resumed with topic=" + mTopicName);

        mTopic = Cache.getTinode().getTopic(mTopicName);

        ((TextView) getActivity().findViewById(R.id.editMessage))
                .setText(TextUtils.isEmpty(messageToSend) ? "" : messageToSend);

        // Check periodically if all messages were read;
        mNoteTimer = new Timer();
        mNoteTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendReadNotification();
            }
        }, READ_DELAY, READ_DELAY);

        runLoader();
        scrollTo(0);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop reporting read messages
        mNoteTimer.cancel();
        mNoteTimer = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_topic, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_attach: {
                // TODO: implement
                return true;
            }
            case R.id.action_delete: {
                // TODO: implement
                return true;
            }
            case R.id.action_mute: {
                // TODO: implement
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void scrollTo(int position) {
        position = position == -1 ? mMessagesAdapter.getItemCount() - 1 : position;
        mMessageList.scrollToPosition(position);
    }

    public void notifyDataSetChanged() {
        mMessagesAdapter.notifyDataSetChanged();
    }

    public void sendMessage() {
        if (mTopic != null) {
            final Activity activity = getActivity();
            final TextView inputField = (TextView) activity.findViewById(R.id.editMessage);
            String message = inputField.getText().toString().trim();
            // notifyDataSetChanged();
            if (!message.equals("")) {
                try {
                    Log.d(TAG, "sendMessage -- sending...");
                    PromisedReply<ServerMessage> reply = mTopic.publish(message);
                    runLoader(); // Shows pending message
                    reply.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            // Updates message list.
                            runLoader();
                            return null;
                        }
                    }, mFailureListener);
                } catch (NotConnectedException ignored) {
                    Log.d(TAG, "sendMessage -- NotConnectedException");
                } catch (Exception ignored) {
                    Log.d(TAG, "sendMessage -- Exception");
                    Toast.makeText(activity, R.string.failed_to_send_message, Toast.LENGTH_SHORT).show();
                    return;
                }

                // Message is successfully queued, clear text from the input field and redraw the list.
                Log.d(TAG, "sendMessage -- clearing text and notifying");
                inputField.setText("");
            }
        }
    }

    public void sendReadNotification() {
        if (mTopic != null) {
            mTopic.noteRead();
        }
    }

    void runLoader() {
        LoaderManager lm = getActivity().getSupportLoaderManager();
        final Loader<Cursor> loader = lm.getLoader(MESSAGES_QUERY_ID);
        if (loader != null && !loader.isReset()) {
            lm.restartLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks);
        } else {
            lm.initLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks);
        }
    }

    private class MessageLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (id == MESSAGES_QUERY_ID) {
                return new MessageDb.Loader(getActivity(), mTopicName, -1, -1, MESSAGES_TO_LOAD);
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader,
                                   Cursor cursor) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                Log.d(TAG, "Got cursor with itemcount=" + cursor.getCount());
                swapCursor(mTopicName, cursor);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                swapCursor(null, null);
            }
        }

        private void swapCursor(final String topicName, final Cursor cursor) {
            Log.d(TAG, "MessagesListAdapter.swapCursor, topic=" + topicName);
            mMessagesAdapter.swapCursor(topicName, cursor);

            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyDataSetChanged();
                        // -1 means scroll to the bottom
                        scrollTo(-1);
                    }
                });
            }
        }
    }
}
