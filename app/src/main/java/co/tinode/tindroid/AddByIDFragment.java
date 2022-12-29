package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class AddByIDFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_by_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

        view.findViewById(R.id.confirm).setOnClickListener(view1 -> {
            TextView editor = activity.findViewById(R.id.editId);
            if (editor != null) {
                String id = editor.getText().toString();
                if (TextUtils.isEmpty(id)) {
                    editor.setError(getString(R.string.id_required));
                } else {
                    Intent it = new Intent(activity, MessageActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    it.putExtra(Const.INTENT_EXTRA_TOPIC, id);
                    startActivity(it);
                }
            }
        });
    }
}
