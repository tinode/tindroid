package co.tinode.tindroid;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public class ChatsViewModel extends AndroidViewModel {
    private ChatListAdapter mAdapter = null;
    private Boolean mIsArchive;

    public ChatsViewModel(@NonNull Application application) {
        super(application);
    }
}
