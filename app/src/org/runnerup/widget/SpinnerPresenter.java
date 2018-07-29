package org.runnerup.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SpinnerAdapter;
import android.widget.TimePicker;

import org.runnerup.R;
import org.runnerup.util.SafeParse;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static android.widget.LinearLayout.VERTICAL;

/**
 * @author Miroslav Mazel
 */
public class SpinnerPresenter {

    private enum Type {
        TS_SPINNER,
        TS_SPINNER_TXT,
        TS_EDITTEXT,
        TS_DATEPICKER,
        TS_TIMEPICKER,
        TS_DURATIONPICKER,
        TS_DISTANCEPICKER,
        TS_NUMBERPICKER
    }

    private Context mContext;
    private String mKey = null;
    private SpinnerInterface mSpin;
    private int mInputType = 0;
    private SpinnerInterface.OnSetValueListener mSetValueListener = null;
    private Type mType;
    private boolean mFirstSetValue = true;
    private int values[] = null;
    private long mCurrValue = -1;
    private CharSequence mLabel;

    SpinnerPresenter(Context context, AttributeSet attrs, SpinnerInterface spinnerInterface) { //todo duration + other pickers don't appear to work
        mContext = context;
        mSpin = spinnerInterface;

        mSpin.setViewOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                SpinnerPresenter.this.onItemSelected(arg2);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });


        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.TitleSpinner);
        mLabel = arr.getString(R.styleable.TitleSpinner_android_text);
        if (mLabel != null) {
            spinnerInterface.setViewLabel(mLabel);
        }
        //Note: R.styleable.TitleSpinner_android_prompt is not used

        CharSequence type = arr.getString(R.styleable.TitleSpinner_type);
        CharSequence defaultValue = arr.getString(R.styleable.TitleSpinner_android_defaultValue);

        if (type == null || "spinner".contentEquals(type)) {
            mType = Type.TS_SPINNER;
            setupSpinner(context, arr);
        } else if ("spinner_txt".contentEquals(type)) {
            mType = Type.TS_SPINNER_TXT;
            setupSpinner(context, arr);
        } else if ("edittext".contentEquals(type)) {
            mType = Type.TS_EDITTEXT;
            setupEditText(context, attrs, arr, defaultValue);
        } else if ("datepicker".contentEquals(type)) {
            mType = Type.TS_DATEPICKER;
            setupDatePicker(context, attrs, defaultValue);
        } else if ("timepicker".contentEquals(type)) {
            mType = Type.TS_TIMEPICKER;
            setupTimePicker(context, attrs, defaultValue);
        } else if ("durationpicker".contentEquals(type)) {
            mType = Type.TS_DURATIONPICKER;
            setupDurationPicker(context, attrs, defaultValue);
        } else if ("distancepicker".contentEquals(type)) {
            mType = Type.TS_DISTANCEPICKER;
            setupDistancePicker(context, attrs, defaultValue);
        } else if ("numberpicker".contentEquals(type)) {
            mType = Type.TS_NUMBERPICKER;
            setupNumberPicker(context, attrs, defaultValue);
        } else {
            throw new IllegalArgumentException("unknown type");
        }

        CharSequence key = arr.getString(R.styleable.TitleSpinner_android_key);
        if (key != null) {
            mKey = key.toString();
            loadValue(defaultValue != null ? defaultValue.toString() : null);
        }

        arr.recycle(); // Do this when done.
    }

    private void setupEditText(final Context context, final AttributeSet attrs, TypedArray arr, CharSequence defaultValue) {
        mInputType = arr.getInt(R.styleable.TitleSpinner_android_inputType,
                EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_FLAG_DECIMAL);
        setValueWithoutSave(defaultValue);

        final EditText edit = new EditText(context, attrs);
        mSpin.setViewOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mLabel);

                edit.setText(mSpin.getViewValueText());
                edit.setInputType(mInputType);
                final LinearLayout layout = createLayout(context);
                layout.addView(edit);
                alert.setView(layout);
                alert.setPositiveButton(context.getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(edit.getText().toString());
                        dialog.dismiss();
                        layout.removeView(edit);
                        onClose(true);
                    }
                });
                alert.setNegativeButton(context.getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(edit);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupSpinner(Context context, TypedArray arr) {
        CharSequence defaultValue = arr.getString(R.styleable.TitleSpinner_android_defaultValue);
        int entriesId = arr.getResourceId(R.styleable.TitleSpinner_android_entries, 0);
        int valuesId = arr.getResourceId(R.styleable.TitleSpinner_values, 0);
        if (valuesId != 0) {
            values = context.getResources().getIntArray(valuesId);
        }
        if (entriesId != 0) {
            DisabledEntriesAdapter adapter = new DisabledEntriesAdapter(context, entriesId);
            mSpin.setViewAdapter(adapter);
            int value = 0;
            if (defaultValue != null) {
                value = SafeParse.parseInt(defaultValue.toString(), 0);
            }
            setValue(value);
//            if (value >= 0 && value < entries.length) {
//                mValueInt = value;
//                mSpin.setViewValue(entries[value]);
//            }
        }
        mSpin.setOnClickSpinnerOpen();
        mSpin.setViewPrompt(mLabel);
    }

    private void onItemSelected(int item) {
        if (mType == SpinnerPresenter.Type.TS_SPINNER_TXT) {
            if (mSpin.getViewAdapter() != null) {
                setValue(mSpin.getViewAdapter().getItem(item).toString());
            }
        } else {
            setValue(getRealValue(item));
        }
        if (!mFirstSetValue) {
            onClose(true);
        }
        mFirstSetValue = false;
    }

    private static LinearLayout createLayout(Context context) {
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        layout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        return layout;
    }

    private void setupDatePicker(final Context context, AttributeSet attrs, CharSequence defaultValue) {
        if (defaultValue != null && "today".contentEquals(defaultValue)) {
            DateFormat df = android.text.format.DateFormat.getDateFormat(context);
            defaultValue = df.format(new Date());
        }
        setValueWithoutSave(defaultValue);

        final DatePicker datePicker = new DatePicker(context, attrs);

        mSpin.setViewOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mLabel);

                final LinearLayout layout = createLayout(context);
                layout.addView(datePicker);
                alert.setView(layout);
                alert.setPositiveButton(context.getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(datePicker));
                        dialog.dismiss();
                        layout.removeView(datePicker);
                        onClose(true);
                    }

                    private String getValue(DatePicker dp) {
                        Calendar c = Calendar.getInstance();
                        c.set(dp.getYear(), dp.getMonth(), dp.getDayOfMonth());
                        DateFormat df = android.text.format.DateFormat.getDateFormat(context);
                        return df.format(c.getTime());
                    }
                });
                alert.setNegativeButton(context.getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(datePicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupTimePicker(final Context context, AttributeSet attrs, CharSequence defaultValue) {
        if (defaultValue != null && "now".contentEquals(defaultValue)) {
            DateFormat df = android.text.format.DateFormat.getTimeFormat(context);
            defaultValue = df.format(new Date());
        }
        setValueWithoutSave(defaultValue);

        final TimePicker timePicker = new TimePicker(context, attrs);

        mSpin.setViewOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mLabel);

                timePicker.setIs24HourView(true);
                final LinearLayout layout = createLayout(context);
                layout.addView(timePicker);
                alert.setView(layout);
                alert.setPositiveButton(context.getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(timePicker));
                        dialog.dismiss();
                        layout.removeView(timePicker);
                        onClose(true);
                    }

                    private String getValue(TimePicker dp) {
                        Calendar c = Calendar.getInstance();
                        c.set(Calendar.HOUR, dp.getCurrentHour());
                        c.set(Calendar.MINUTE, dp.getCurrentMinute());
                        DateFormat df = android.text.format.DateFormat.getTimeFormat(context);
                        return df.format(c.getTime());
                    }
                });
                alert.setNegativeButton(context.getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(timePicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupDurationPicker(final Context context, final AttributeSet attrs,
                                     CharSequence defaultValue) {
        setValueWithoutSave(defaultValue);

        mSpin.setViewOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mLabel);

                final DurationPicker picker = new DurationPicker(context, attrs);
                picker.setEpochTime(mCurrValue);
                final LinearLayout layout = createLayout(context);
                layout.addView(picker);
                alert.setView(layout);
                alert.setPositiveButton(context.getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getPickerValue());
                        dialog.dismiss();
                        layout.removeView(picker);
                        onClose(true);
                    }

                    private String getPickerValue() {
                        return DateUtils.formatElapsedTime(picker.getEpochTime());
                    }
                });
                alert.setNegativeButton(context.getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(picker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupDistancePicker(final Context context, AttributeSet attrs,
                                     CharSequence defaultValue) {
        setValueWithoutSave(defaultValue);

        final DistancePicker distancePicker = new DistancePicker(context, attrs);

        mSpin.setViewOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mLabel);

                distancePicker.setDistance(mCurrValue);

                final LinearLayout layout = createLayout(context);
                layout.addView(distancePicker);
                alert.setView(layout);
                alert.setPositiveButton(context.getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(distancePicker));
                        dialog.dismiss();
                        layout.removeView(distancePicker);
                        onClose(true);
                    }

                    private String getValue(DistancePicker dp) {
                        return Long.toString(dp.getDistance());
                    }
                });
                alert.setNegativeButton(context.getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(distancePicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    private void setupNumberPicker(final Context context, AttributeSet attrs, CharSequence defaultValue) {
        setValueWithoutSave(defaultValue);

        final NumberPicker numberPicker = new NumberPicker(context, attrs);
        numberPicker.setOrientation(VERTICAL);

        mSpin.setViewOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(context);

                alert.setTitle(mLabel);

                numberPicker.setValue((int) mCurrValue);

                final LinearLayout layout = createLayout(context);
                layout.addView(numberPicker);
                alert.setView(layout);
                alert.setPositiveButton(context.getResources().getString(R.string.OK), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setValue(getValue(numberPicker));
                        dialog.dismiss();
                        layout.removeView(numberPicker);
                        onClose(true);
                    }

                    private String getValue(NumberPicker dp) {
                        return Integer.toString(dp.getValue());
                    }
                });
                alert.setNegativeButton(context.getResources().getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                        layout.removeView(numberPicker);
                        onClose(false);
                    }
                });
                AlertDialog dialog = alert.create();
                dialog.show();
            }
        });
    }

    void setOnSetValueListener(SpinnerInterface.OnSetValueListener listener) {
        this.mSetValueListener = listener;
    }

    private void onClose(boolean b) {
        if (mCloseDialogListener != null) {
            mSpin.viewOnClose(mCloseDialogListener, b);
        }
    }

    void loadValue(String defaultValue) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (pref == null)
            return;
        switch (mType) {
            case TS_SPINNER:
                int def = 0;
                if (defaultValue != null) {
                    def = SafeParse.parseInt(defaultValue, 0);
                }
                setValue(pref.getInt(mKey, def));
                break;
            case TS_SPINNER_TXT:
            case TS_EDITTEXT:
            case TS_DURATIONPICKER:
            case TS_DISTANCEPICKER:
            case TS_NUMBERPICKER:
            case TS_DATEPICKER:
            case TS_TIMEPICKER:
                final String val = pref.getString(mKey, defaultValue == null ? "" : defaultValue);
                setValue(val);
                break;
        }
    }

    void setValue(int value) {
        if (mSetValueListener != null) {
            try {
                value = mSetValueListener.preSetValue(value);
            } catch (java.lang.IllegalArgumentException ex) {
                if ((int) mCurrValue != -1) {
                    mSpin.setViewSelection((int) mCurrValue);
                }
                return;
            }
        }
        mCurrValue = value;
        int selectionValue = getSelectionValue(value);
        mSpin.setViewSelection(selectionValue);
        if (mSpin.getViewAdapter() != null) {
            Object val = mSpin.getViewAdapter().getItem(selectionValue);
            if (val != null)
                mSpin.setViewValue(val.toString());
            else
                mSpin.setViewValue("");
        }
        if (mKey == null)
            return;
        Editor pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        pref.putInt(mKey, value);
        pref.commit();
    }

    void setValue(String value) {
        setValue(value, true);
    }

    private void setValueWithoutSave(CharSequence value) {
        String str = value == null ? "" : value.toString();
        setValue(str, false);
    }

    private void setValue(String value, Boolean savePreferences) {
        if (mSetValueListener != null) {
            try {
                value = mSetValueListener.preSetValue(value);
            } catch (java.lang.IllegalArgumentException ex) {
                if (mSpin.getViewAdapter() != null) {
                    mSpin.setViewSelection((int) mCurrValue);
                }
                return;
            }
        }

        //Store the value - could be separate for distance vs time
        if (value == null) {
            mCurrValue = 0;
        } else if (mType == Type.TS_DURATIONPICKER) {
            mCurrValue = SafeParse.parseSeconds(value, 0);
        } else {
            mCurrValue = (long) SafeParse.parseDouble(value, 0);
        }
        if (mType == Type.TS_DISTANCEPICKER && !TextUtils.isEmpty(value)) {
            mSpin.setViewValue(String.format("%s %s", value, mContext.getResources().getString(R.string.metrics_distance_m)));
        } else {
            mSpin.setViewValue(value);
        }
        if (mType == Type.TS_SPINNER_TXT) {
            if (mSpin.getViewAdapter() != null) {
                int intVal = find(mSpin.getViewAdapter(), value);
                mCurrValue = intVal; // here because onclicklistener doesn't react to changing to the same value twice
                mSpin.setViewSelection(intVal);
            }
        }

        if (mKey == null || !savePreferences)
            return;
        Editor pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        pref.putString(mKey, value);
        pref.commit();
    }

    private int find(SpinnerAdapter adapter, String value) {
        for (int i = 0; i < adapter.getCount(); i++) {
            if (value.contentEquals(adapter.getItem(i).toString())) {
                return i;
            }
        }
        return 0;
    }

    int getSelectionValue(int value) {
        if (values == null)
            return value;
        int p = 0;
        for (int v : values) {
            if (v == value)
                return p;
            p++;
        }

        /* not found, hmm...what to do... */
        return 0;
    }

    private int getRealValue(int value) {
        if (values == null)
            return value;
        if (value >= 0 && value < values.length)
            return values[value];

        /* invalid value, hmm...what to do... */
        return values[0];
    }

    CharSequence getValue() {
        switch (mType) {
            case TS_SPINNER_TXT:
            case TS_EDITTEXT:
            case TS_DATEPICKER:
            case TS_TIMEPICKER:
                return mSpin.getViewValueText();
            case TS_DURATIONPICKER:
            case TS_DISTANCEPICKER:
            case TS_NUMBERPICKER:
            case TS_SPINNER:
                break;
        }
        return String.format(Locale.getDefault(), "%d", mCurrValue);
    }

    int getValueInt() {
        return (int) mCurrValue;
    }

    void clear() {
        if (mKey != null) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().remove(mKey).commit();
        }
    }

    private SpinnerInterface.OnCloseDialogListener mCloseDialogListener = null;

    void setOnCloseDialogListener(SpinnerInterface.OnCloseDialogListener listener) {
        this.mCloseDialogListener = listener;
    }
}