package co.tinode.tindroid;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.EditText;

import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Created by gsokolov on 2/10/16.
 */
public class NetworkService extends Service {
    private static final String TAG = "NetworkService";

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private Tinode mTinode;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        NetworkService getService() {
            // Return this instance of LocalService so clients can call public methods
            return NetworkService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "service bound");
        mTinode = new Tinode("Tindroid", "api.tinode.co", "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K");
        return mBinder;
    }

    public Tinode getTinode() {
        return mTinode;
    }
}
