package com.byteshaft.namaztime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Helpers extends ContextWrapper {

    private StringBuilder stringBuilder = null;
    private String mData = null;
    private final String SELECTED_CITY_POSITION = "cityPosition";
    private final String SELECTED_CITY_NAME = "cityName";
    private String mPresentDate;
    static boolean setData;

    Helpers(Context context) {
        super(context);
    }

    Helpers(Activity activityContext) {
        super(activityContext);
    }

    boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    void showInternetNotAvailableDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("No Internet");
        alert.setMessage("Please connect to the internet and try again");
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                MainActivity.getInstance().finish();
            }
        });
        alert.show();
    }

    private Calendar getCalenderInstance() {
        return Calendar.getInstance();
    }

    private SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("yyyy-M-d");
    }

    String getDate() {
        return getDateFormat().format(getCalenderInstance().getTime());
    }

    String getAmPm() {
        return getTimeFormat().format(getCalenderInstance().getTime());
    }

    SimpleDateFormat getTimeFormat() {
        return new SimpleDateFormat("h:mm aa");
    }

    void setTimesFromDatabase(boolean runningFromActivity) {
        setData = true;
        String date = getDate();
        String output = getPrayerTimesForDate(date, runningFromActivity);
        try {
            JSONObject jsonObject = new JSONObject(output);
            setPrayerTime(jsonObject, runningFromActivity);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setPrayerTime(JSONObject day, boolean runningFromActivity) throws JSONException {
        saveTimeForNamaz("fajr", getPrayerTime(day, "fajr"));
        saveTimeForNamaz("dhuhr", getPrayerTime(day, "dhuhr"));
        saveTimeForNamaz("asr", getPrayerTime(day, "asr"));
        saveTimeForNamaz("maghrib", getPrayerTime(day, "maghrib"));
        saveTimeForNamaz("isha", getPrayerTime(day, "isha"));
        if (runningFromActivity) {
            displayData();
        }
    }

    private String getPrayerTime(JSONObject jsonObject, String namaz) throws JSONException {
        return jsonObject.get(namaz).toString();
    }

    void displayData() {
        String currentCity = getPreviouslySelectedCityName();
        UiUpdateHelpers uiUpdateHelpers = new UiUpdateHelpers(MainActivity.getInstance());
        uiUpdateHelpers.setDate(getDate());
        uiUpdateHelpers.setCurrentCity(toTheUpperCaseSingle(currentCity));
        uiUpdateHelpers.displayDate(getAmPm());
        uiUpdateHelpers.setNamazNames("Fajr" + "\n" + "\n"
                + "Dhuhr" + "\n" + "\n" + "Asar"
                + "\n" + "\n" + "Maghrib" + "\n" + "\n"
                + "Isha");
        uiUpdateHelpers.setNamazTimesLabel(
                retrieveTimeForNamaz("fajr") + "\n" + "\n" +
                        retrieveTimeForNamaz("dhuhr") + "\n" + "\n" +
                        retrieveTimeForNamaz("asr") + "\n" + "\n" +
                        retrieveTimeForNamaz("maghrib") + "\n" + "\n" +
                        retrieveTimeForNamaz("isha"));
        mPresentDate = getDate();
    }

    String getDataFromFileAsString() {
        FileInputStream fileInputStream;
        try {
            fileInputStream = openFileInput(MainActivity.sFileName);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
            stringBuilder = new StringBuilder();
            while (bufferedInputStream.available() != 0) {
                char characters = (char) bufferedInputStream.read();
                stringBuilder.append(characters);
            }
            bufferedInputStream.close();
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    private String getPrayerTimesForDate(String request, boolean runningFromActivity) {
        try {
            mData = null;
            String data = getDataFromFileAsString();
            JSONArray readingData = new JSONArray(data);
            for (int i = 0; i < readingData.length(); i++) {
                mData = readingData.getJSONObject(i).toString();
                if (mData.contains(request)) {
                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (runningFromActivity) {
            if (!mData.contains(request) && isNetworkAvailable()) {
                new NamazTimesDownloadTask(this).execute();
            } else if (isNetworkAvailable() && !mData.contains(request)) {
                showInternetNotAvailableDialog();
            }
        }
        return mData;
    }

    String getDiskLocationForFile(String file) {
        return getFilesDir().getAbsoluteFile().getAbsolutePath() + "/" + file;
    }

    SharedPreferences getPreferenceManager() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    String getPreviouslySelectedCityName() {
        SharedPreferences preferences = getPreferenceManager();
        return preferences.getString(SELECTED_CITY_NAME, "Karachi");
    }

    int getPreviouslySelectedCityIndex() {
        SharedPreferences preferences = getPreferenceManager();
        return preferences.getInt(SELECTED_CITY_POSITION, 0);
    }

    void saveSelectedCity(String cityName, int positionInSpinner) {
        SharedPreferences preferences = getPreferenceManager();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(SELECTED_CITY_NAME, cityName);
        editor.putInt(SELECTED_CITY_POSITION, positionInSpinner);
        editor.apply();
    }

    void writeDataToFile(String file, String data) {
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = openFileOutput(file, Context.MODE_PRIVATE);
            fileOutputStream.write(data.getBytes());
            fileOutputStream.close();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    String[] getNamazTimesArray() {
        return new String[]{
                retrieveTimeForNamaz("fajr"),
                retrieveTimeForNamaz("dhuhr"),
                retrieveTimeForNamaz("asr"),
                retrieveTimeForNamaz("maghrib"),
                retrieveTimeForNamaz("isha")
        };
    }

    void refreshNamazTimeIfDateChange() {
        if (!mPresentDate.equals(getDate())) {
            Log.i("refreshNamazTime", "working");
            setTimesFromDatabase(true);
        }
    }

    String toTheUpperCaseSingle(String givenString) {
        String example = givenString;

        example = example.substring(0, 1).toUpperCase()
                + example.substring(1, example.length());
        return example;
    }

    private void saveTimeForNamaz(String namaz, String time) {
        SharedPreferences preference = getPreferenceManager();
        preference.edit().putString(namaz, time).apply();
        preference.edit().putString("date", getDate()).apply();
    }

    private String retrieveTimeForNamaz(String namaz) {
        SharedPreferences preference = getPreferenceManager();
        return preference.getString(namaz, null);
    }
}

