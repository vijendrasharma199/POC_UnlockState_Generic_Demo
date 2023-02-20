package com.example.poc_unlockstate_demo;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.devicedetect.MainUsbSerialHelper;
import com.example.devicedetect.interfaces.UsbHelperListener;
import com.example.poc_unlockstate_demo.databinding.ActivityDeviceTestBinding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class DeviceTestActivity extends AppCompatActivity {

    private ActivityDeviceTestBinding binding;
    private static final String TAG = "DEVICE_TESTING_TAG";
    private final ArrayList<String> mainList = new ArrayList<>();
    private int[] testArray;
    private int divideBy = 100;

    //Required Variables
    private String viewFilePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        initialization();

        //handle startBtn click, to start the test
        binding.startBtn.setOnClickListener(v -> startDeviceTest());

        //handle stopBtn click, to stop the test
        binding.stopBtn.setOnClickListener(v -> stopDeviceTest());

        //handle clearBtn click, to clear the views
        binding.clearBtn.setOnClickListener(v -> clearViewsAndLists());

        useModule();
    }

    private void clearViewsAndLists() {
        //clear all views
        binding.fileNameEt.setText("");
        binding.receivedTextTv.setText("");
        binding.timerEt.setText("");
        binding.cmdEt.setText("");

        //clear all lists
        mainList.clear();
    }

    private void stopDeviceTest() {

    }

    private void startDeviceTest() {
        String fileName = binding.fileNameEt.getText().toString().trim();
        String command = binding.cmdEt.getText().toString().trim();
        String timer = binding.timerEt.getText().toString().trim();
        String partitionBy = binding.partitionByEt.getText().toString().trim();

        divideBy = Integer.parseInt(partitionBy);
        mainList.clear();

        testArray = new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 30, 30, 30, 30, 30, 60, 60, 60, 60, 60, 300, 300, 300, 300, 300};

        if (fileName.isEmpty()) {
            binding.fileNameEt.setError("Required...");
            binding.fileNameEt.requestFocus();
        } else {
            Thread thread = new Thread(() -> {

                Log.i(TAG, "startDeviceTest: Test started...");
                runOnUiThread(() -> {
                    binding.liveReceivedTextTv.setVisibility(View.VISIBLE);
                    binding.receivedTextTv.append("Test started...\n");
                });

                for (int testNo = 0; testNo < testArray.length; testNo++) {
                    performTest(testArray[testNo], testArray[testNo] + "S", fileName, testNo + 1);
                }

                runOnUiThread(() -> {
                    binding.receivedTextTv.append("\nAll test completed...\n\n");
                    binding.liveReceivedTextTv.setVisibility(View.GONE);
                });
                Log.i(TAG, "All Test completed...");

                //open file
                //openFile();
            });
            thread.start();
        }
    }

    private void openFile() {
        Log.i(TAG, "openFile: " + viewFilePath);
        if (!viewFilePath.equals("")) {
            //viewFilePath = viewFilePath.replace("/storage", "storage");

            Log.i(TAG, "openFile: " + viewFilePath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(viewFilePath)), "text/csv");
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(intent);
        }

    }

    private String getMimeType(String url) {
        String[] parts = url.split("\\.");
        String extension = parts[parts.length - 1];
        String type = null;
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension);
        }
        return type;
    }

    private void performTest(int timerInSeconds, String testName, String fileName, int i) {

        String directoryPath = "";
        try {
            //create the directory
            directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/Test Folder/" + fileName.replace(" ", "_") + "/";
            File file = new File(directoryPath);
            if (!file.exists()) {
                Log.e(TAG, "Directory does not exist, create it");
                file.mkdirs();
            }

            if (file.exists()) {
                Log.w(TAG, "Directory created");
            }
        } catch (Exception e) {
            runOnUiThread(() -> showToast(e.toString()));
        }

        String finalDirectoryPath = directoryPath;
        Thread thread = new Thread(() -> {
            //helper.startTransmission("1");
            MainUsbSerialHelper.sendCommand("1");

            runOnUiThread(() -> binding.receivedTextTv.append(Html.fromHtml(String.format("<br/><b>%d</b> test running out of <b>%d</b> with Duration of <b>%d</b> seconds.<br/>", i, testArray.length, timerInSeconds))));
            Log.i(TAG, i + " test running out of " + testArray.length + "\nDuration : " + timerInSeconds + "seconds.\n");

            try {
                Thread.sleep(timerInSeconds * 1000L);
            } catch (Exception e) {
                Log.e(TAG, "run: " + e);
                showToast(e.toString());
            }
            MainUsbSerialHelper.sendCommand("0");

            int size = mainList.size();
            runOnUiThread(() -> binding.receivedTextTv.append(Html.fromHtml(String.format("<b>%s</b> test finished<br/>Last Data : <b>%s</b><br/>Size of List : <b>%d</b><br/>", testName, mainList.get(size - 1), size))));
            Log.i(TAG, testName + " test finished.\nLast Data : " + mainList.get(size - 1) + "\nSize of List : " + size);

            //save data to file
            saveDataToFile(finalDirectoryPath, fileName.replace(" ", "_") + "_" + testName, testName);
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Log.i("PERFORM_TEST", "performTest: " + e);
            throw new RuntimeException(e);
        }
    }

    private void saveDataToFile(String directory, String fileName, String testName) {
        //write data into csv file
        if (mainList.size() > 0) {

            String fName = directory + fileName + "_" + System.currentTimeMillis() + ".csv";
            try {
                FileWriter fileWriter = new FileWriter(fName);
                fileWriter.append("S.No").append(",").append("ADC from device").append(",").append("Counter from device").append("\n");
                for (int i = 0; i < mainList.size(); i++) {
                    fileWriter.append(String.valueOf(i + 1)).append(",").append(mainList.get(i).replace(" ", ","));
                    if (i != mainList.size() - 1) fileWriter.append("\n");
                }
                fileWriter.flush();
                fileWriter.close();

                runOnUiThread(() -> binding.receivedTextTv.append(Html.fromHtml(String.format("Data saved for <b>%s</b> test<br/>", testName))));
                Log.i(TAG, "Data saved for " + testName + " test\n");

                //clear list
                mainList.clear();

                //store view file path
                viewFilePath = fName;

            } catch (IOException e) {
                Log.e(TAG, "run: File Write" + e);
                runOnUiThread(() -> showToast(e.toString()));
                throw new RuntimeException(e);
            }
        } else {
            runOnUiThread(() -> showToast("Data not available..."));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //useModule();
    }

    /*@Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) useModule();
    }*/

    private void useModule() {
        MainUsbSerialHelper.setDeviceCallback(new UsbHelperListener() {
            @Override
            public void onDeviceConnect() {
                Log.d(TAG, "Activity : Device Connected...");
                binding.receivedTextTv.append("Device Connected....\n");
                binding.deviceStatusTv.setText("Device Connected...");
                binding.deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.verify_color)));
            }

            @Override
            public void onDeviceVerified() {
                Log.d(TAG, "Activity : Device Verified...");
                binding.receivedTextTv.append("Received : Device ready for communication\n");
                binding.deviceStatusTv.setText("Device Verified...");
                binding.deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.start_color)));
            }

            @Override
            public void onReceivedData(String data) {
                Log.w(TAG, "Activity : " + data);
                mainList.add(data);
                //if (mainList.size() % divideBy == 0) {
                //  int size = mainList.size();
                //  runOnUiThread(() -> binding.receivedTextTv.append("Received : " + data + " : Size of List : " + size + "\n"));
                //}
                int size = mainList.size();
                runOnUiThread(() -> binding.liveReceivedTextTv.setText("Received : " + data + " : Size of List : " + size));
            }

            @Override
            public void onDeviceDisconnect() {
                Log.d(TAG, "Activity : Device Disconnected");
                binding.receivedTextTv.append("Device disconnected or transmission stopped.....\n");
                binding.deviceStatusTv.setText("Device Disconnected...");
                binding.deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.stop_color)));
            }

            @Override
            public void onConnectionError(String errorMessage) {
                Log.e(TAG, "onConnectionError: " + errorMessage);
                binding.deviceStatusTv.setText("Device Error...");
                binding.deviceStatusTv.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.stop_color)));
                binding.receivedTextTv.append(errorMessage + "\n");
            }
        }, this);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void initialization() {
        //set scroll event to receivedText
        binding.receivedTextTv.setMovementMethod(new ScrollingMovementMethod());

        //set divideBy = 100 by default
        binding.partitionByEt.setText("100");

        //disable stop button
        binding.stopBtn.setEnabled(false);
        binding.stopBtn.setAlpha(0.5f);
    }
}