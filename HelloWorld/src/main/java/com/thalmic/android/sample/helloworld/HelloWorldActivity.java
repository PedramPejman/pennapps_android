/*
 * Copyright (C) 2014 Thalmic Labs Inc.
 * Distributed under the Myo SDK license agreement. See LICENSE.txt for details.
 */

package com.thalmic.android.sample.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

import org.json.JSONArray;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;


public class HelloWorldActivity extends Activity {

    private TextView mLockStateView;
    private TextView mTextView;
    private Button initialize;
    private boolean initialized, toInitialize, recording, recordingMotion = false;
    float base_yaw, base_pitch, base_roll, mYaw=0, mPitch =0, mRoll=0;
    int mOrientation;
    private MediaRecorder myAudioRecorder;
    private String audioOutputFile = null, motionFile=null;
    FileOutputStream fileout;
    long begin_time;
    OutputStreamWriter outputWriter;
    ImageView sleepPos;

    String server = "http://sleapeasy.cloudapp.net/submit/";


    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private DeviceListener mListener = new AbstractDeviceListener() {

        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            // Set the text color of the text view to cyan when a Myo connects.
            mTextView.setTextColor(Color.CYAN);
            initialize.setEnabled(true);
        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            // Set the text color of the text view to red when a Myo disconnects.
            mTextView.setTextColor(Color.RED);
        }

        // onArmSync() is called whenever Myo has recognized a Sync Gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmSync(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            mTextView.setText(myo.getArm() == Arm.LEFT ? R.string.arm_left : R.string.arm_right);
        }

        // onArmUnsync() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmUnsync(Myo myo, long timestamp) {
            mTextView.setText(R.string.hello_world);
        }

        // onUnlock() is called whenever a synced Myo has been unlocked. Under the standard locking
        // policy, that means poses will now be delivered to the listener.
        @Override
        public void onUnlock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.unlocked);
        }

        // onLock() is called whenever a synced Myo has been locked. Under the standard locking
        // policy, that means poses will no longer be delivered to the listener.
        @Override
        public void onLock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.locked);
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = Math.round((float) Math.toDegrees(Quaternion.roll(rotation)));
            float pitch = Math.round((float) Math.toDegrees(Quaternion.pitch(rotation)));
            float yaw = Math.round((float) Math.toDegrees(Quaternion.yaw(rotation)));
            if (mPitch == 0 && mRoll == 0 && mYaw ==0) {
                mPitch = pitch;
                mRoll = roll;
                mYaw = yaw;
                mOrientation = getOrientation(mPitch, mRoll, mYaw);
            }
            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (myo.getXDirection() == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }

            if (recordingMotion) recordMotion(pitch, roll, yaw);

            if (toInitialize && !initialized) {
                base_pitch = pitch;
                base_roll = roll;
                base_yaw = yaw;
                Toast.makeText(getApplicationContext(), pitch + " " + roll + " " + yaw, Toast.LENGTH_SHORT).show();
                initialized = true;
                initialize.setText("Start Recording");
            }

            // Next, we apply a rotation to the text view using the roll, pitch, and yaw.
            /*mTextView.setRotation(roll);
            mTextView.setRotationX(pitch);
            mTextView.setRotationY(yaw);*/
            mTextView.setText(pitch + " " + roll + " " + yaw);
        }

        private void recordMotion( float pitch, float roll, float yaw) {
            if (orientationChanged(pitch, roll, yaw))
            try {
                outputWriter.write( System.currentTimeMillis() + ", " + mOrientation + "\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private boolean orientationChanged(float pitch, float roll, float yaw) {
            int orientation = getOrientation(pitch, roll, yaw);
            if (orientation != mOrientation) {
                changePicture(orientation);
                Log.d("OR", ""+orientation);
                mOrientation = orientation;
                return true;
            }
            return false;
        }

        private int getOrientation(float pitch, float roll, float yaw) {
            pitch = pitch - base_pitch;
            roll = roll - base_roll;
            yaw = yaw - base_yaw;

            if (distance(roll, 0) > 150) {
                return 2;
            }
            if (distance(roll,0) > 70) {
                if (distance(pitch, 0) + distance(yaw, 0) > 50) {
                    return 3;
                }
                else return 1;
            }
            return 0;
        }

        private float distance(float a, float b) {
            //range : (-180,180)
            if (a < b) {
                float c = a;
                a = b;
                b = c;
            }
            return Math.min(a-b, b+360-a);
        }

        private void changePicture(int index) {
            Resources resources = getResources();
            Drawable drawable = null;
            if (index == 0) drawable = resources.getDrawable(R.drawable.pos0);
            if (index == 1) drawable = resources.getDrawable(R.drawable.pos1);
            if (index == 2) drawable = resources.getDrawable(R.drawable.pos2);
            if (index == 3) drawable = resources.getDrawable(R.drawable.pos3);
            sleepPos.setImageDrawable(drawable);
        }

        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Handle the cases of the Pose enumeration, and change the text of the text view
            // based on the pose we receive.
            switch (pose) {
                case UNKNOWN:
                    mTextView.setText(getString(R.string.hello_world));
                    break;
                case REST:
                case DOUBLE_TAP:
                    int restTextId = R.string.hello_world;
                    switch (myo.getArm()) {
                        case LEFT:
                            restTextId = R.string.arm_left;
                            break;
                        case RIGHT:
                            restTextId = R.string.arm_right;
                            break;
                    }
                    mTextView.setText(getString(restTextId));
                    break;
                case FIST:
                    mTextView.setText(getString(R.string.pose_fist));
                    break;
                case WAVE_IN:
                    mTextView.setText(getString(R.string.pose_wavein));
                    break;
                case WAVE_OUT:
                    mTextView.setText(getString(R.string.pose_waveout));
                    break;
                case FINGERS_SPREAD:
                    mTextView.setText(getString(R.string.pose_fingersspread));
                    break;
            }

            if (pose != Pose.UNKNOWN && pose != Pose.REST) {
                // Tell the Myo to stay unlocked until told otherwise. We do that here so you can
                // hold the poses without the Myo becoming locked.
                myo.unlock(Myo.UnlockType.HOLD);

                // Notify the Myo that the pose has resulted in an action, in this case changing
                // the text on the screen. The Myo will vibrate.
                myo.notifyUserAction();
            } else {
                // Tell the Myo to stay unlocked only for a short period. This allows the Myo to
                // stay unlocked while poses are being performed, but lock after inactivity.
                myo.unlock(Myo.UnlockType.TIMED);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_world);

        mLockStateView = (TextView) findViewById(R.id.lock_state);
        mTextView = (TextView) findViewById(R.id.text);
        initialize = (Button) findViewById(R.id.initialize);
        sleepPos = (ImageView) findViewById(R.id.sleepPos);
        sleepPos.setImageDrawable(getResources().getDrawable(R.drawable.pos0));
        initialized = false;
        toInitialize = false;
        recording = false;

        initialize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!initialized) {
                    toInitialize = true;
                    ((TextView)v).setText("Initializing");
                }
                else if (!recording) {
                    startRecording();
                    recordingMotion = true;
                    ((TextView)v).setText("Stop Recording");
                }

                else {
                    stopRecording();
                    sendData();
                }
            }
        });

        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        if (isFinishing()) {
            // The Activity is finishing, so shutdown the Hub. This will disconnect from the Myo.
            Hub.getInstance().shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (R.id.action_scan == id) {
            onScanActionSelected();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onScanActionSelected() {
        // Launch the ScanActivity to scan for Myos to connect to.
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    private void startRecording() {

        audioOutputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recording.3gp";;
        myAudioRecorder = new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        myAudioRecorder.setOutputFile(audioOutputFile);
        myAudioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            myAudioRecorder.prepare();
        }

        catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Could not prepare microphone", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        myAudioRecorder.start();
        Toast.makeText(getApplicationContext(), "Recording started", Toast.LENGTH_SHORT).show();
        recording = true;
        begin_time = System.currentTimeMillis();
        Log.d("BEGIN", " " + begin_time);
        //MOTION STUFF
        try {
            motionFile = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/motiondata.txt";
            File file = new File(motionFile);
            //fileout = openFileOutput("motion.txt", MODE_PRIVATE);
            fileout = new FileOutputStream(file);
            outputWriter = new OutputStreamWriter(fileout);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        myAudioRecorder.stop();
        myAudioRecorder.release();
        myAudioRecorder  = null;
        recording = false;
        try {
            outputWriter.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        //display file saved message
        Toast.makeText(getBaseContext(), "Data is being sent...",
                Toast.LENGTH_SHORT).show();

    }

    private void sendData() {
        //new AsyncHttpPostTask(server).execute(new File(motionFile));
        new PostPicture(server).execute(audioOutputFile);
        new PostPicture(server).execute(motionFile);
    }
}

class PostPicture extends AsyncTask<String, Void, JSONArray> {
    AlertDialog alertDialog;
    String server;

    public PostPicture(String server) {
        this.server = server;
    }

    @Override
    protected JSONArray doInBackground(String... paths) {
        Log.d("POST", "about to post");
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;
        String pathToOurFile = paths[0];
        //String password = custom_hash();
        //String urlServer = AppBrain.CRUNCHTIME_ROOT_FOLDER_URL + AppBrain.IMAGE_UPLOAD_RELATIVE_URL + "?password=" + password;
        String urlServer = this.server;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary =  "*****";


        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1*1024*1024;

        try
        {
            Log.d("POST", "inside Try");
            FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile) );


            URL url = new URL(urlServer);
            connection = (HttpURLConnection) url.openConnection();


            // Allow Inputs &amp; Outputs.
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            // Set HTTP method to POST.
            connection.setRequestMethod("POST");

            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

            outputStream = new DataOutputStream( connection.getOutputStream() );
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + pathToOurFile +"\"" + lineEnd);
            outputStream.writeBytes(lineEnd);

            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // Read file
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            while (bytesRead > 0)
            {
                outputStream.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }

            outputStream.writeBytes(lineEnd);
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Responses from the server (code and message)
            int serverResponseCode = connection.getResponseCode();
            String serverResponseMessage = connection.getResponseMessage();
            Log.d("POST", "responseCode: " + serverResponseCode);
            Log.d("POST", "responseMessage: " + serverResponseMessage);
            fileInputStream.close();
            outputStream.flush();
            outputStream.close();
        }
        catch (Exception ex)
        {
            Log.d("POST", "error during writing");
        }
        return null;
    }

    @Override
    protected void onPostExecute(JSONArray jArray) {
        //forward.setText(getString(R.string.ask_forward));
        //Toast.makeText(getActivity(), "Image is uploaded", Toast.LENGTH_SHORT).show();
        //sendButton.setVisibility(View.INVISIBLE);
        Log.d("POST","DONE");
    }
}
