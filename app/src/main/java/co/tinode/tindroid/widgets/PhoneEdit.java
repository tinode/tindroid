package co.tinode.tindroid.widgets;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import co.tinode.tindroid.R;

/**
 * Widget for editing phone numbers
 */
public class PhoneEdit extends LinearLayout {
    private final static String TAG = "PhoneEdit";

    private final PhoneNumberUtil mPhoneNumberUtil = PhoneNumberUtil.getInstance();
    private AsYouTypeFormatter mFormatter = null;

    private final Spinner mSpinner;
    private final AppCompatEditText mTextEdit;
    private ArrayAdapter<CountryCode> mAdapter;

    private CountryCode mSelected = null;

    public PhoneEdit(@NonNull Context context) {
        this(context, null);
    }

    public PhoneEdit(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PhoneEdit(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        inflate(context, R.layout.phone_editor, this);
        mSpinner = findViewById(R.id.country_selector);
        mTextEdit = findViewById(R.id.phone_edit_text);

        if (isInEditMode()) {
            // Allow the view to be used in AndroidStudio layout editor.
            return;
        }

        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long rowId) {
                changeSelection((CountryCode) mSpinner.getSelectedItem());
                mTextEdit.setHint(getExampleNumber());
                mTextEdit.setText(mTextEdit.getText());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);
        @ColorInt int colorControlActivated = typedValue.data;
        @ColorInt int colorControlNormal = ((TextView) findViewById(R.id.phone_number_hint))
                .getTextColors().getDefaultColor();
        OnFocusChangeListener focusListener = (v, hasFocus) ->
                ((TextView) findViewById(R.id.phone_number_hint))
                        .setTextColor(hasFocus ? colorControlActivated : colorControlNormal);

        mSpinner.setOnFocusChangeListener(focusListener);

        mTextEdit.addTextChangedListener(new TextWatcher() {
            Boolean editing = false;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable edited) {
                if (editing) {
                    return;
                }
                editing = true;
                if (edited.length() > 0) {
                    char[] source = edited.toString().toCharArray();
                    String result = "";
                    for (char ch : source) {
                        result = mFormatter.inputDigit(ch);
                    }
                    edited.clear();
                    edited.append(result);
                    mFormatter.clear();
                }
                editing = false;
            }
        });
        mTextEdit.setOnFocusChangeListener(focusListener);

        // Get current locale.
        Locale locale = Locale.getDefault();
        List<CountryCode> countryList = null;
        try {
            countryList = readCountryList(locale);
        } catch (IOException | JSONException ex) {
            Log.w(TAG, "Unable to phone data", ex);
        }

        if (countryList != null) {
            mAdapter = new PhoneNumberAdapter(context, countryList);
            mSpinner.setAdapter(mAdapter);
            setCountry(locale.getCountry());
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        mSpinner.setEnabled(enabled);
        mTextEdit.setEnabled(enabled);
        super.setEnabled(enabled);
    }

    private List<CountryCode> readCountryList(Locale locale) throws IOException, JSONException {
        AssetManager am = getResources().getAssets();
        // Read dialing codes.
        InputStream is = am.open("dcodes.json", AssetManager.ACCESS_BUFFER);
        String data = readJSONString(is);
        JSONArray array = new JSONArray(data);
        HashMap<String, String[]> dialCodes = new HashMap<>();
        for (int i = 0, n = array.length(); i < n; i++) {
            JSONObject obj = array.getJSONObject(i);
            String[] dials = obj.getString("dial").split(",");
            String code = obj.getString("code");
            if (!TextUtils.isEmpty(code) && dials.length > 0) {
                code = code.toUpperCase(Locale.ENGLISH);
                dialCodes.put(code, dials);
            } else {
                throw new JSONException("Invalid input in dcodes.json");
            }
        }
        is.close();

        try {
            // Try fully qualified locale first.
            is = am.open(locale.toString() + ".json", AssetManager.ACCESS_BUFFER);
        } catch (FileNotFoundException ignored) {
            try {
                Log.w(TAG, "Unable to load country names for language '" + locale + "', retrying " +
                        locale.getLanguage());
                is = am.open(locale.getLanguage() + ".json", AssetManager.ACCESS_BUFFER);
            } catch (FileNotFoundException ignored2) {
                Log.w(TAG, "Unable to load country names for '" + locale.getLanguage() + "', retrying EN");
                is = am.open("en.json", AssetManager.ACCESS_BUFFER);
            }
        }

        data = readJSONString(is);
        array = new JSONArray(data);
        List<CountryCode> countryList = new ArrayList<>();
        for (int i = 0, n = array.length(); i < n; i++) {
            JSONObject obj = array.getJSONObject(i);
            // Country name
            String name = obj.getString("name");
            // Country code
            String code = obj.getString("code");
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(code)) {
                throw new JSONException("Invalid input in country data");
            }

            code = code.toUpperCase(Locale.ENGLISH);
            String[] prefixes = dialCodes.get(code);
            if (prefixes == null) {
                Log.d(TAG, "Country dial code is missing for '" + code + "'");
                continue;
            }

            for (String prefix: prefixes) {
                countryList.add(new CountryCode(code, name, prefix.trim()));
            }
         }
        is.close();

        return countryList;
    }

    public boolean isNumberValid() {
        Phonenumber.PhoneNumber number;
        try {
            number = mPhoneNumberUtil.parse(getPhoneNumberE164(), mSelected.isoCode);
        } catch (NumberParseException ignored) {
            return false;
        }
        return mPhoneNumberUtil.isValidNumber(number) &&
                mPhoneNumberUtil.getNumberType(number) == PhoneNumberUtil.PhoneNumberType.MOBILE;
    }

    public @NonNull String getRawInput() {
        Editable editable = mTextEdit.getText();
        String text = editable != null ? editable.toString() : null;
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        return mSelected.prefix + text;
    }

    public @NonNull String getPhoneNumberE164() {
        String text = getRawInput();
        if (TextUtils.isEmpty(text)) {
            return text;
        }

        try {
            Phonenumber.PhoneNumber number = mPhoneNumberUtil.parse(text, mSelected.isoCode);
            return mPhoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            return "";
        }
    }

    public void setError(CharSequence error) {
        ((AppCompatEditText) findViewById(R.id.phone_edit_text)).setError(error);
    }

    public void setCountry(String code) {
        int pos = mAdapter.getPosition(new CountryCode(code));
        if (pos >= 0) {
            CountryCode country = mAdapter.getItem(pos);
            changeSelection(country);
            mSpinner.setSelection(pos);
        }
    }

    private void changeSelection(CountryCode code) {
        mSelected = code;
        mFormatter = mPhoneNumberUtil.getAsYouTypeFormatter(mSelected.isoCode);
    }

    private String getExampleNumber() {
        Phonenumber.PhoneNumber sample = mPhoneNumberUtil.getExampleNumberForType(mSelected.isoCode,
                PhoneNumberUtil.PhoneNumberType.MOBILE);
        String number = mPhoneNumberUtil.format(sample, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        number = number.substring(mSelected.prefix.length()).trim();
        if (number.startsWith("-")) {
            number = number.substring(1).trim();
        }
        return number;
    }

    public static String readJSONString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        return result.toString();
    }

    public static class CountryCode {
        String isoCode;
        String name;
        String prefix;

        CountryCode(String code) {
            this.isoCode = code;
        }

        CountryCode(String code, String name, String prefix) {
            this.isoCode = code.toUpperCase();
            this.name = name;
            this.prefix = "+" + prefix;
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
        public PhoneNumberAdapter(Context context, List<CountryCode> countries) {
            super(context, R.layout.phone_full, countries);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null || !"phone_full".equals(convertView.getTag())) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.phone_full, parent, false);
                convertView.setTag("phone_full");
            }

            return getCustomView(position, convertView, false);
        }

        @Override
        @NonNull
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null || !"phone_selected".equals(convertView.getTag())) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.phone_selected, parent, false);
                convertView.setTag("phone_selected");
            }

            return getCustomView(position, convertView, true);
        }

        @NonNull
        public View getCustomView(int position, @NonNull View item, Boolean selected) {
            CountryCode country = getItem(position);
            if (country == null) {
                return item;
            }

            ((AppCompatTextView) item.findViewById(R.id.country_flag)).setText(country.getFlag());
            if (!selected) {
                ((TextView) item.findViewById(R.id.country_name)).setText(country.name);
            }
            ((TextView) item.findViewById(R.id.country_dialcode)).setText(country.prefix);
            return item;
        }
    }
}
