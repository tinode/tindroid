package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import static co.tinode.tindroid.InmemoryCache.*;

/**
 * A placeholder fragment containing a simple view.
 */
public class LoginSettingsFragment extends Fragment {

    public LoginSettingsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        return inflater.inflate(R.layout.fragment_loginsettings, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle bundle = getArguments();
        String link;
        if (bundle != null) {
            link = bundle.getString("host_name");
        } else {
            link = InmemoryCache.sHost;
        }
        setHostName(link);
    }
/*
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_cancel, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
*/
    public void setHostName(String hostName) {
        TextView view = (TextView) getView().findViewById(R.id.editHostName);
        view.setText(hostName);
    }
}
