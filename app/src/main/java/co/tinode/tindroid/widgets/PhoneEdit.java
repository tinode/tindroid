package co.tinode.tindroid.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.JsonReader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import co.tinode.tindroid.R;

/**
 * Widget for editing phone numbers
 */
public class PhoneEdit  extends LinearLayout {
    private final static String TAG = "PhoneEdit";

    private final PhoneNumberUtil mPhoneNumberUtil = PhoneNumberUtil.getInstance();

    private Spinner mSpinner;
    private AppCompatEditText mTextEdit;
    private ArrayAdapter<CountryCode> mAdapter;

    public PhoneEdit(@NonNull Context context) {
        super(context);
    }

    public PhoneEdit(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PhoneEdit(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        inflate(context, R.layout.phone_editor, this);
        mSpinner = findViewById(R.id.country_selector);
        mTextEdit = findViewById(R.id.phone_edit_text);

        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long rowId) {
                String code = ((CountryCode) mSpinner.getSelectedItem()).isoCode;
                Log.d(TAG, "onItemSelected: " + code);
                mTextEdit.setHint(mPhoneNumberUtil.format(mPhoneNumberUtil.getExampleNumberForType(code,
                        PhoneNumberUtil.PhoneNumberType.MOBILE), PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                mTextEdit.setText(mTextEdit.getText());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        mTextEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Get current locale.
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        List<CountryCode> countryList = null;
        try {
            countryList = readCountryList(locale);
        } catch (IOException ex) {
            Log.w(TAG, "Unable to load country names", ex);
        }

        if (countryList != null) {
            mAdapter = new PhoneNumberAdapter(context, countryList);
            mSpinner.setAdapter(mAdapter);
        }

        setCountry(locale.getCountry());
    }

    private List<CountryCode> readCountryList(Locale locale) throws IOException {
        InputStream isCountryList;
        try {
            isCountryList = getResources().getAssets().open(locale.toString(), AssetManager.ACCESS_BUFFER);
        } catch (FileNotFoundException ignored) {
            try {
                Log.w(TAG, "Unable to load country names for language '" + locale + "', retrying " +
                        locale.getCountry());
                isCountryList = getResources().getAssets().open(locale.getCountry(), AssetManager.ACCESS_BUFFER);
            } catch (FileNotFoundException ignored2) {
                Log.w(TAG, "Unable to load country names for '" + locale.getCountry() + "', retrying EN");
                isCountryList = getResources().getAssets().open("en", AssetManager.ACCESS_BUFFER);
            }
        }

        List<CountryCode>  countryList = readJsonStream(isCountryList);
        isCountryList.close();

        return countryList;
    }

    public @NonNull String getPhoneNumber() {
        Editable text = mTextEdit.getText();
        if (TextUtils.isEmpty(text)) {
            return "";
        }

        try {
            Phonenumber.PhoneNumber number = mPhoneNumberUtil.parse(text,
                    ((CountryCode) mSpinner.getSelectedItem()).isoCode);
            return mPhoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            Log.w(TAG, "Invalid number entered " + text, ex);
            return "";
        }
    }

    public void setCountry(String code) {
        int pos = mAdapter.getPosition(new CountryCode(code));
        if (pos >= 0) {
            mSpinner.setSelection(pos);
        }
    }

    private List<CountryCode> readJsonStream(@NonNull InputStream in) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return readArray(reader);
        }
    }

    public List<CountryCode> readArray(JsonReader reader) throws IOException {
        List<CountryCode> records = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            records.add(readRecord(reader));
        }
        reader.endArray();
        return records;
    }

    public CountryCode readRecord(JsonReader reader) throws IOException {
        String countryName = null;
        String code = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                countryName = reader.nextString();
            } else if (name.equals("code")) {
                code = reader.nextString();
                reader.skipValue();
            }
        }
        reader.endObject();
        return new CountryCode(code, countryName);
    }

    public static class CountryCode {
        String isoCode;
        String name;

        CountryCode(String code) {
            this.isoCode = code;
        }

        CountryCode(String code, String name) {
            this.isoCode = code;
            this.name = name;
        }

        String getFlag() {
            int firstLetter = Character.codePointAt(isoCode, 0) - 0x41 + 0x1F1E6;
            int secondLetter = Character.codePointAt(isoCode, 1) - 0x41 + 0x1F1E6;
            return new String(Character.toChars(firstLetter)) + new String(Character.toChars(secondLetter));
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CountryCode) {
                return isoCode.equals(((CountryCode) other).isoCode);
            }
            return false;
        }
    }

    public static class PhoneNumberAdapter extends ArrayAdapter<CountryCode> {
        private final PhoneNumberUtil mPhoneUtils;

        public PhoneNumberAdapter(Context context, List<CountryCode> countries) {
            super(context, R.layout.phone_full, countries);

            mPhoneUtils = PhoneNumberUtil.getInstance();
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null || !"phone_selected".equals(convertView.getTag())) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.phone_selected, parent, false);
                convertView.setTag("phone_selected");
            }

            return getCustomView(position, convertView, false);
        }

        @Override
        @NonNull
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null || !"phone_full".equals(convertView.getTag())) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.phone_full, parent, false);
                convertView.setTag("phone_full");
            }

            return getCustomView(position, convertView, true);
        }

        @SuppressLint("SetTextI18n")
        public View getCustomView(int position, @NonNull View item, Boolean selected) {
            CountryCode country = getItem(position);
            if (country == null) {
                return item;
            }

            ((AppCompatTextView) item.findViewById(R.id.country_flag)).setText(country.getFlag());
            if (!selected) {
                ((TextView) item.findViewById(R.id.country_name)).setText(country.name);
            }
            ((TextView) item.findViewById(R.id.country_dialcode))
                    .setText("+" + mPhoneUtils.getExampleNumber(country.isoCode).getCountryCode());

            return item;
        }
    }
}
