package co.tinode.tindroid;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
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
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.media.VCard;
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
    private SwipeRefreshLayout mRefresher;
    private MessageLoaderCallbacks mLoaderCallbacks;
    private int mPagesToLoad;

    private String mTopicName = null;
    protected Topic<VCard, String> mTopic;

    private Timer mNoteTimer = null;
    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    public MessagesFragment() {
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
        // lm.setReverseLayout(true);
        lm.setStackFromEnd(true);

        mMessageList = (RecyclerView) activity.findViewById(R.id.messages_container);
        mMessageList.setLayoutManager(lm);

        mMessagesAdapter = new MessagesListAdapter(activity);
        mMessageList.setAdapter(mMessagesAdapter);

        mRefresher = (SwipeRefreshLayout) activity.findViewById(R.id.swipe_refresher);
        mRefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (mMessagesAdapter.getItemCount() == mPagesToLoad * MESSAGES_TO_LOAD) {
                    mPagesToLoad++;
                    runLoader();
                } else if (!StoredTopic.isAllDataLoaded(mTopic)) {
                    Log.d(TAG, "Calling server for more data");
                    mTopic.getMeta(mTopic.getMetaGetBuilder().withGetEarlierData(MESSAGES_TO_LOAD).build());
                } else {
                    mRefresher.setRefreshing(false);
                }
            }
        });

        mLoaderCallbacks = new MessageLoaderCallbacks();

        mFailureListener = new UiUtils.ToastFailureListener(getActivity());

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
        String oldTopicName = mTopicName;
        mTopicName = bundle.getString("topic");
        String messageToSend = bundle.getString("messageText");

        if (mTopicName != null && !mTopicName.equals(oldTopicName)) {
            mMessagesAdapter.swapCursor(mTopicName, null);
        }

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

        mPagesToLoad = 1;
        mRefresher.setRefreshing(false);

        runLoader();
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
                // TODO: implement attaching media to message
                return true;
            }
            case R.id.action_clear: {
                // TODO: implement Topic.deleteMessages
                return true;
            }
            case R.id.action_mute: {
                // TODO: implement setting notifications to off
                return true;
            }
            case R.id.action_delete: {
                showDeleteTopicConfirmationDialog();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Confirmation dialog "Do you really want to do X?"
    private void showDeleteTopicConfirmationDialog() {
        final Activity activity = getActivity();
        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.cancel, null);
        confirmBuilder.setMessage(R.string.confirm_delete_topic);

        confirmBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    mTopic.delete().thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            Intent intent = new Intent(getActivity(), ContactsActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                            getActivity().finish();
                            return null;
                        }
                    }, mFailureListener);
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        confirmBuilder.show();
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
                    Log.d(TAG, "sendMessage -- NotConnectedException", ignored);
                } catch (Exception ignored) {
                    Log.d(TAG, "sendMessage -- Exception", ignored);
                    Toast.makeText(activity, R.string.failed_to_send_message, Toast.LENGTH_SHORT).show();
                    return;
                }

                // Message is successfully queued, clear text from the input field and redraw the list.
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
                return new MessageDb.Loader(getActivity(), mTopicName, mPagesToLoad, MESSAGES_TO_LOAD);
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
            mMessagesAdapter.swapCursor(topicName, cursor);
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRefresher.setRefreshing(false);
                        notifyDataSetChanged();
                        if (cursor != null)
                        mMessageList.scrollToPosition(cursor.getCount() - 1);
                    }
                });
            }
        }
    }
}
