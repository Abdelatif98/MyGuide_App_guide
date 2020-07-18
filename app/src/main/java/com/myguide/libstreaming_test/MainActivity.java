package com.myguide.libstreaming_test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;
import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import androidmads.library.qrgenearator.QRGContents;
import androidmads.library.qrgenearator.QRGEncoder;
import android.graphics.Bitmap;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.tooltip.Tooltip;

import com.google.zxing.WriterException;

public class MainActivity extends Activity implements
        OnClickListener,
        RtspClient.Callback,
        Session.Callback,
        SurfaceHolder.Callback{

    public final static String TAG = "MainActivity";


    private ImageButton mButtonStart;
    private Switch mSwitch;
    private SurfaceView mSurfaceView;
    private TextView mTextBitrate;
    private Session mSession;
    private RtspClient mClient;
    private ImageView mimageView;
    int count_click = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        //ask for permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },1);
            }
        }

        //get by id
        mimageView= (ImageView) findViewById(R.id.img_qr);
        mButtonStart = (ImageButton) findViewById(R.id.start);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mTextBitrate = (TextView) findViewById(R.id.bitrate);
        mSwitch= (Switch) findViewById(R.id.swShareLocation);

        mButtonStart.setOnClickListener(this);

        //location tracking enable/desable
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    //start sharing position
                    startService(new Intent(getApplicationContext(), LastLocationService.class));
                } else {
                    // The toggle is disabled
                    //stop sharing position
                    stopService(new Intent(getApplicationContext(), LastLocationService.class));
                }
            }
        });

        //SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        // Configures the SessionBuilder
        mSession = SessionBuilder.getInstance()
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(8000,16000))
                .setVideoEncoder(SessionBuilder.VIDEO_NONE)
                .setSurfaceView(mSurfaceView)
                .setCallback(this)
                .build();

        // Configures the RTSP client
        mClient = new RtspClient();
        mClient.setSession(mSession);
        mClient.setCallback(this);

        mSurfaceView.getHolder().addCallback(this);


    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start:
                toggleStream();
                break;
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mClient.release();
        mSession.release();
        mSurfaceView.getHolder().removeCallback(this);
    }


    // Connects/disconnects to the RTSP server and starts/stops the stream
    public void toggleStream() {
        if (!mClient.isStreaming()) {
            if(count_click<1){
                count_click++;
                Toast.makeText(MainActivity.this,"Click again to start streaming",Toast.LENGTH_LONG).show();
                // start the RTSP server
                this.startService(new Intent(this, RtspServerService.class));
            }
            else{
                count_click=0;
                String ip,port,path;
                // We save the content user inputs in Shared Preferences
                SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                Editor editor = mPrefs.edit();
                editor.putString("uri", "rtsp://127.0.0.1:9999/test");
                editor.putString("password", "");
                editor.putString("username", "");
                editor.commit();
                // We parse the URI written in the Editext
                Pattern uri = Pattern.compile("rtsp://(.+):(\\d*)/(.+)");
                Matcher m = uri.matcher("rtsp://127.0.0.1:9999/test"); m.find();
                ip = m.group(1);
                port = m.group(2);
                path = m.group(3);

                mClient.setServerAddress(ip, Integer.parseInt(port));
                mClient.setStreamPath("/"+path);
                mClient.startStream();
                mButtonStart.setImageResource(R.drawable.icon_audio_active);
                //generating qr
                String url= URL();
                putQR(url);
                //Toast.makeText(MainActivity.this,url,Toast.LENGTH_LONG).show();
            }

        } else {
            // Stops the stream and disconnects from the RTSP server
            mClient.stopStream();
            // stop the RTSP server
            this.stopService(new Intent(this, RtspServerService.class));
            mimageView.setImageResource(R.drawable.qr_bef_str);
            mButtonStart.setImageResource(R.drawable.icon_audio);

        }
    }

    private void logError(final String msg) {
        final String error = (msg == null) ? "Error unknown" : msg;
        // Displays a popup to report the eror to the user
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(msg).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {}
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onBitrateUpdate(long bitrate) {
        mTextBitrate.setText(""+bitrate/1000+" kbps");
    }

    @Override
    public void onPreviewStarted() {

    }

    @Override
    public void onSessionConfigured() {

    }

    @Override
    public void onSessionStarted() {

    }

    @Override
    public void onSessionStopped() {

    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        switch (reason) {
            case Session.ERROR_CAMERA_ALREADY_IN_USE:
                break;
            case Session.ERROR_INVALID_SURFACE:
                break;
            case Session.ERROR_STORAGE_NOT_READY:
                break;
            case Session.ERROR_CONFIGURATION_NOT_SUPPORTED:
                logError("The following settings are not supported on this phone: "+
                        "("+e.getMessage()+")");
                e.printStackTrace();
                return;
            case Session.ERROR_OTHER:
                break;
        }

        if (e != null) {
            logError(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onRtspUpdate(int message, Exception e) {

    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        //mSession.startPreview();
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //mClient.stopStream();
    }

    public String URL(){
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
        String URL= "rtsp://"+ipAddress+":1234/test";
        return URL;
    }
    public void putQR(String URL){
        QRGEncoder  qrgEncoder = new QRGEncoder(URL, null, QRGContents.Type.TEXT, 300);
        try {
            // Getting QR-Code as Bitmap
            Bitmap bitmap = qrgEncoder.encodeAsBitmap();
            // Setting Bitmap to ImageView
            mimageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }
}
