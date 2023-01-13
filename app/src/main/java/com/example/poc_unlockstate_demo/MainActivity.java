package com.example.poc_unlockstate_demo;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.devicedetect.UsbHelperListener;
import com.example.devicedetect.UsbSerialCommunicationHelper1;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity {

    String TAG = getClass().getSimpleName();

    TextView deviceStatusTv, receivedTextTv, outputTv;
    EditText cmdEt, inputEt;
    Button convertBtn, sendBtn, clearBtn, startBtn, stopBtn, releaseBtn;

    UsbSerialCommunicationHelper1 helper;
    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialization();

        convertBtn.setOnClickListener(v -> {
            String input = inputEt.getText().toString().trim();
            try {
                outputTv.setText(toHexString(getSHA(input + "SPDN")));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        });
        sendBtn.setOnClickListener(v -> sendCommand());
        clearBtn.setOnClickListener(v -> {
            receivedTextTv.setText("");
            cmdEt.setText("");
        });

        startBtn.setOnClickListener(v -> helper.onStartTransmission());
        stopBtn.setOnClickListener(v -> helper.onStartTransmission());
        releaseBtn.setOnClickListener(v -> {
            try {
                helper.removeInstance();
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendCommand() {
        String cmd = cmdEt.getText().toString().trim();
        if (!TextUtils.isEmpty(cmd)) {
            receivedTextTv.append("----------------------\nSend : " + cmd + "\n");
            helper.onSendCommand(cmd);
        } else {
            Toast.makeText(this, "Please enter command...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        useModule("onResume");
    }

    private void useModule(String message) {
        progressDialog.setMessage("Connecting...");
        //progressDialog.show();
        Log.d(TAG, "Activity Module Call : " + message);

        helper.setCommunication(new UsbHelperListener() {
            @Override
            public void onDeviceConnect() {
                progressDialog.setMessage("Connecting...");

                Log.d(TAG, "Activity : Device Connected...");
                /*runOnUiThread(() -> {
                    receivedTextTv.append("Device Connected....\n");
                    deviceStatusTv.setText("Device Connected...");
                    deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.verify_color)));
                });*/
                receivedTextTv.append("Device Connected....\n");
                deviceStatusTv.setText("Device Connected...");
                deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.verify_color)));
            }

            @Override
            public void onDeviceVerified(boolean isVerified) {
                progressDialog.setMessage("Verifying...");
                progressDialog.dismiss();
                Log.d(TAG, "Activity : Device Verified...");

                /*runOnUiThread(() -> {
                    receivedTextTv.append("Received : Device ready for communication\n");
                    deviceStatusTv.setText("Device Verified...");
                    deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.start_color)));

                });*/
                receivedTextTv.append("Received : Device ready for communication\n");
                deviceStatusTv.setText("Device Verified...");
                deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.start_color)));
            }

            @Override
            public void onTransmission(String data) {
                if (progressDialog.isShowing())
                    progressDialog.dismiss();
                Log.w(TAG, "Activity : " + data);
                runOnUiThread(() -> receivedTextTv.append("Received : " + data + "\n"));
                //runOnUiThread(() -> receivedTextTv.setText("Received : " + data + "\n"));
                //receivedTextTv.append("Received : " + data + "\n");
            }

            @Override
            public void onDeviceDisconnect() {
                progressDialog.dismiss();
                Log.d(TAG, "Activity : Device Disconnected");
                receivedTextTv.append("Device disconnected or transmission stopped.....\n");
                deviceStatusTv.setText("Device Disconnected...");
                deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.stop_color)));
            }

            @Override
            public void onConnectionError(String errorMessage) {
                progressDialog.dismiss();
                Log.e(TAG, "onConnectionError: " + errorMessage);
                deviceStatusTv.setText("Device Error...");
                deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.stop_color)));
                receivedTextTv.append(errorMessage + "\n");
            }
        });
        startBtn.setOnClickListener(v -> helper.onStartTransmission());
        stopBtn.setOnClickListener(v -> helper.onStopTransmission());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            useModule("onNewIntent");
        }
    }

    public static byte[] getSHA(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }


    public static String toHexString(byte[] hash) {
        BigInteger number = new BigInteger(1, hash);
        StringBuilder hexString = new StringBuilder(number.toString(16));
        while (hexString.length() < 64) {
            hexString.insert(0, '0');
        }
        return hexString.toString();
    }

    private void initialization() {
        deviceStatusTv = findViewById(R.id.deviceStatusTv);
        receivedTextTv = findViewById(R.id.receivedTextTv);
        receivedTextTv.setMovementMethod(new ScrollingMovementMethod());

        cmdEt = findViewById(R.id.cmdEt);
        inputEt = findViewById(R.id.inputEt);
        outputTv = findViewById(R.id.outputTv);
        convertBtn = findViewById(R.id.convertBtn);
        sendBtn = findViewById(R.id.sendBtn);
        clearBtn = findViewById(R.id.clearBtn);
        startBtn = findViewById(R.id.startBtn);
        stopBtn = findViewById(R.id.stopBtn);
        releaseBtn = findViewById(R.id.releaseBtn);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false);

        helper = UsbSerialCommunicationHelper1.getInstance(this);
    }

}