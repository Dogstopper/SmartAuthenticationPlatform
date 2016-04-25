/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package src.com.hoho.android.usbserial.examples;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import com.kairos.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends Activity {

    private final String TAG = DashboardActivity.class.getSimpleName();

    private static final int ENROLL_RESPONSE_CODE = 100;
    private static final int RECOGNIZE_RESPONSE_CODE = 101;
    private static final int ENROLL_SPEECH_CODE = 102;
    private static final int RECOGNIZE_SPEECH_CODE = 103;

    private static final int FEATURE_FACE = 0x01;
    private static final int FEATURE_SPEECH = 0x02;

    private static final byte[] SUCCESS_RESPONSE = new byte[] { (byte) 0x55 };
    private static final byte[] FAILURE_RESPONSE = new byte[] { (byte) 0x8A };

    private static final String galleryId = "friends";
    private static final String PREFS_NAME = "MyPrefsFile";

    private static UsbSerialDevice serial = null;
    private static UsbDevice mDevice = null;

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;

    private volatile boolean started = false;
    private Intent intent;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(final byte[] data) {
            if (!started) {
                DashboardActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        parseSerial(data);
                    }
                });
            }
        }
    };

    private void writeFailure() {
        serial.write(FAILURE_RESPONSE);
        Log.d("SERIAL WRITE", "Failure");
        Toast.makeText(this, "FAILURE", Toast.LENGTH_LONG).show();
    }

    private void writeSuccess() {
        serial.write(SUCCESS_RESPONSE);
        Log.d("SERIAL WRITE", "Success");
        Toast.makeText(this, "Success", Toast.LENGTH_LONG).show();
    }

    private void parseSerial(byte[] data) {
        if (data != null && data.length > 0 && !started) {
            // Check for enrollment
            if (data[0] == 0x01) {
                StringBuffer buff = new StringBuffer();
                for (int i = 0; i < data[2]; i++) {
                    buff.append(data[4 + i] + "");
                }
                userId = buff.toString();

                // Check the feature
                int feature = data[4 + data[2]];
                if (feature == FEATURE_FACE) {
                    started = true;
                    startKairosEnrollmentForResult();
                }
                else if (feature == FEATURE_SPEECH) {
                    started = true;
                    startRecordedSpeech(ENROLL_SPEECH_CODE);
                }
            }

            // Check for Authentication
            if (data[0] == 0x02) {
                StringBuffer buff = new StringBuffer();
                for (int i = 0; i < data[2]; i++) {
                    buff.append(data[4 + i] + "");
                }
                userId = buff.toString();

                // Check the feature
                int feature = data[4 + data[2]];
                if (feature == FEATURE_FACE) {
                    started = true;
                    startKairosRecognitionForResult();
                }
                else if (feature == FEATURE_SPEECH) {
                    started = true;
                    startRecordedSpeech(RECOGNIZE_SPEECH_CODE);
                }
            }

//            // Check Delete feature
//            if (data[0] == 0x03) {
//                char[] userIdArr = new char[data[2]];
//                for (int i = 0; i < userIdArr.length; i++) {
//                    userIdArr[i] = (char) data[4 + i];
//                }
//                userId = new String(userIdArr);
//
//                // Check the feature
//                int feature = data[4 + data[2]];
//                if (feature == FEATURE_FACE) {
//                    started = true;
//                    kairosDeleteUser();
//                }
//                else if (feature == FEATURE_SPEECH) {
//
//                }
//            }
//
//            // Check Delete User
//            if (data[0] == 0x04) {
//                char[] userIdArr = new char[data[2]];
//                for (int i = 0; i < userIdArr.length; i++) {
//                    userIdArr[i] = (char) data[4 + i];
//                }
//                userId = new String(userIdArr);
//                kairosDeleteUser();
//            }

            // Check Delete All
            if (data[0] == 0x05) {
                clearAllUsers();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);

        /* * * set authentication * * */
        String app_id = "24a7b953";
        String api_key = "75704dbd16a8c25b5e4c3638f9b57399";
        myKairos.setAuthentication(this, app_id, api_key);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serial != null) {
            serial.close();
            serial = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + serial);
        if (serial == null) {
            mTitleTextView.setText("No serial device.");
            if (!started) startRecordedSpeech(RECOGNIZE_SPEECH_CODE);
            started = true;
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(mDevice);
            if (connection == null) {
                mTitleTextView.setText("Opening device failed");
                return;
            }

            serial = UsbSerialDevice.createUsbSerialDevice(mDevice, connection);
            serial.open();
            serial.setBaudRate(9600);
            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
            serial.setParity(UsbSerialInterface.PARITY_NONE);
            serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            serial.read(mCallback);

            mTitleTextView.setText("Serial device: " + serial.getClass().getSimpleName());
        }
    }

    static void show(Context context, UsbSerialDevice port, UsbDevice device) {
        serial = port;
        mDevice = device;
        final Intent intent = new Intent(context, DashboardActivity.class);
        context.startActivity(intent);
    }

    // AUDIO RECOGNITION STUFF
    private void startRecordedSpeech(int code) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Voice recognition Demo...");
        startActivityForResult(intent, code);
    }

    // KAIROS STUFF

    private void startKairosEnrollmentForResult() {
        intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        this.startActivityForResult(intent, ENROLL_RESPONSE_CODE);
    }

    private void startKairosRecognitionForResult() {
        intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        this.startActivityForResult(intent, RECOGNIZE_RESPONSE_CODE);
    }

//    private void kairosDeleteUser() {
//        try {
//            myKairos.deleteSubject(userId, galleryId, deleteListener);
//        }  catch (JSONException e) {
//            e.printStackTrace();
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }
//    }

    private void clearAllUsers() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.commit();
        try {
            myKairos.deleteGallery(galleryId, deleteListener);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private Kairos myKairos = new Kairos();
    private String userId;

    KairosListener deleteListener = new KairosListener() {
        @Override
        public void onSuccess(String response) {
            try {
                Log.d("KAIROS DEMO", response);
                JSONObject obj = new JSONObject(response);
                String status = obj.getString("status");
                if (status.equalsIgnoreCase("Complete")) {
                    writeSuccess();
                }
                else {
                    writeFailure();
                }
                started = false;
            } catch (JSONException jsonException) {
                onFail(response);
            }
        }

        @Override
        public void onFail(String response) {
            Log.d("KAIROS DEMO", response);
            started = false;
            writeFailure();
        }
    };

    KairosListener enrollmentListener = new KairosListener() {

        @Override
        public void onSuccess(String response) {
            try {
                Log.d("KAIROS DEMO", response);
                JSONObject obj = new JSONObject(response);
                JSONArray imagesArray = obj.getJSONArray("images");
                for (int i = 0; i < imagesArray.length(); i++) {
                    JSONObject transaction = imagesArray.getJSONObject(i).getJSONObject("transaction");
                    String userId = transaction.getString("status");
                    if (userId.equalsIgnoreCase("success")) {
                        writeSuccess();
                    } else {
                        writeFailure();
                    }
                    started = false;
                }
            } catch (JSONException jsonException) {
                onFail(response);
            }
        }

        @Override
        public void onFail(String response) {
            Log.d("KAIROS DEMO", response);
            started = false;
            writeFailure();
        }
    };

    KairosListener recognitionListener = new KairosListener() {

        @Override
        public void onSuccess(String response) {
            try {
                Log.d("KAIROS DEMO", response);
                JSONObject obj = new JSONObject(response);
                JSONArray imagesArray = obj.getJSONArray("images");
                for (int i = 0; i < imagesArray.length(); i++) {
                    JSONObject transaction = imagesArray.getJSONObject(i).getJSONObject("transaction");
                    String userId = transaction.getString("subject");
                    if (userId.equalsIgnoreCase(DashboardActivity.this.userId)) {
                        writeSuccess();
                    } else {
                        writeFailure();
                    }
                    started = false;
                }
            } catch (JSONException jsonException) {
                onFail(response);
            }
        }

        @Override
        public void onFail(String response) {
            Log.d("KAIROS DEMO", response);
            started = false;
            writeFailure();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (requestCode == ENROLL_SPEECH_CODE) {
            ArrayList<String> matches = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(userId, matches.get(0).toLowerCase());

            // Commit the edits!
            editor.commit();
            writeSuccess();
            return;
        }

        if (requestCode == RECOGNIZE_SPEECH_CODE) {
            // Get the saved string
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            String toMatch = settings.getString(userId, "");

            if (toMatch.equalsIgnoreCase("")) {
                writeFailure();
                return;
            }
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            for (String match: matches) {
                if (match.equalsIgnoreCase(toMatch)) {
                    writeSuccess();
                    return;
                }
            }
            writeFailure();
            return;
        }

        if (requestCode == ENROLL_RESPONSE_CODE || requestCode == RECOGNIZE_RESPONSE_CODE) {
            Bundle extras = intent.getExtras();
            Bitmap image = Bitmap.createBitmap((Bitmap) extras.get("data"));

            try {
                //myKairos.deleteSubject("Yash-yee", "friends", listener);
                if (requestCode == DashboardActivity.ENROLL_RESPONSE_CODE) {
                    myKairos.enroll(image, userId, galleryId, null, null, null, enrollmentListener);
                }
                if (requestCode == DashboardActivity.RECOGNIZE_RESPONSE_CODE) {
                    myKairos.recognize(image, galleryId, null, null, null, null, recognitionListener);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

}
