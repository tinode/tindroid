package co.tinode.tindroid;

import android.graphics.Bitmap;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * This ViewModel holds avatar before it's sent to the server.
 * Used by LoginActivity when creating the account, by StartChatActivity when creating a new topic.
 */
public class AvatarViewModel extends ViewModel {
    private final MutableLiveData<Bitmap> avatar = new MutableLiveData<>();

    public void clear() {
        avatar.postValue(null);
    }

    public void setAvatar(Bitmap bmp) {
        avatar.postValue(bmp);
    }

    public LiveData<Bitmap> getAvatar() {
        return avatar;
    }
}
