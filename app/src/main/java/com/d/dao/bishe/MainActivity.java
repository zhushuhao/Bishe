package com.d.dao.bishe;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.d.dao.bishe.bean.SettingInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;

public class MainActivity extends AppCompatActivity
        implements SurfaceHolder.Callback, PreviewCallback, View.OnClickListener {

    DatagramSocket socket;
    InetAddress address;

    AvcEncoder avcCodec;
    public Camera mCamera;
    SurfaceView mSurfaceView;
    SurfaceHolder mSurfaceHolder;
    //1280 720
    int width = 1280;
    int height = 720;
    int frameRate = 16;// 帧率
    int bitrate = 125000;//码率
    byte[] h264;
    private boolean mMediaRecorderRecording = false;
    private SettingInfo mSettingInfo;
    private boolean bOpening = false;
    private String REMOTE_HOST = "172.25.164.1";
    private int REMOTE_HOST_PORT = 5000;

    private SurfaceTexture surfaceTexture;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("MainActivity", "MainActivity");

        try {
            initOutData();

            findViewById(R.id.btnOpen).setOnClickListener(this);
            findViewById(R.id.btnClose).setOnClickListener(this);

            initSurfaceView();

            surfaceTexture = new SurfaceTexture(10);

            keepScreenAlwaysLight();//保持屏幕常亮

            avcCodec = new AvcEncoder(width, height, frameRate, bitrate);


            initSocket();
        } catch (Exception e) {
            Log.e("ex", e.toString());
        }


    }

    private void initOutData() {
        Intent intent = getIntent();
        mSettingInfo = (SettingInfo) intent.getSerializableExtra("info");
        Log.e("info", mSettingInfo.toString());
        if (mSettingInfo == null) {
            return;
        }
        width = mSettingInfo.getSelectSize().getWidth();
        height = mSettingInfo.getSelectSize().getHeight();
        frameRate = mSettingInfo.getFrameRate();
        bitrate = mSettingInfo.getBitRate();

        h264 = new byte[width * height * 3 / 2];
        REMOTE_HOST = mSettingInfo.getServerIp();
        REMOTE_HOST_PORT = Integer.parseInt(mSettingInfo.getServerPort());

    }

    private PowerManager.WakeLock wl;

    private void keepScreenAlwaysLight() {
        //屏幕常亮
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "net.majorkernelpanic.spydroid.wakelock");
        wl.acquire();
    }


    private void initSurfaceView() {
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceHolder = mSurfaceView.getHolder(); // 绑定SurfaceView，取得SurfaceHolder对象
        mSurfaceHolder.setFixedSize(width, height); // 预览大小設置
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        //如果不是在录制
        if (!mMediaRecorderRecording) {
            initializeVideo();    //初始化视频录制的条件　
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        //初始化摄像头
        mCamera = Camera.open();
        try {
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void initializeVideo() {
        if (mSurfaceHolder == null) {
            return;
        }
        mMediaRecorderRecording = true;
        Camera.Parameters parameters = mCamera.getParameters(); // Camera parameters to obtain
        parameters.setFlashMode(FLASH_MODE_OFF);//设置闪光灯模式
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);//设置相机照片白平衡
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);//自动对焦

        parameters.setPreviewSize(width, height);
        parameters.setPictureSize(width, height);
        parameters.setPreviewFormat(ImageFormat.YV12);//预览格式
        mCamera.setParameters(parameters);

        try {
            mCamera.setDisplayOrientation(0);
//            byte[] rawBuf = new byte[1280 * 720 * 3 / 2];
//            mCamera.addCallbackBuffer(rawBuf);
//            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.setPreviewCallback(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        mCamera.setPreviewCallback(null); //！！这个必须在前，不然退出出错
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        avcCodec.close();
        mMediaRecorderRecording = false;
    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

        Log.e("onPreviewFrame", "onPreviewFrame");
        Log.v("h264", "h264 start");
        int ret = 0;
        try {
            ret = avcCodec.offerEncoder(data, h264);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("e", e.toString());
        }
        Log.e("ret", "ret" + ret);
        if (ret > 0) {
            final int finalRet = ret;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.e("send", "发送数据");
                        DatagramPacket packet = new DatagramPacket(h264, finalRet, address, REMOTE_HOST_PORT);
                        socket.send(packet);
                    } catch (IOException e) {
                        Log.e("socket", e.toString());
                    }
                }
            });

        }
        Log.v("h264", "h264 end");
//        reset buff to camera.
        camera.addCallbackBuffer(data);


    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnOpen:
                Log.e("start", "onclick");
                startCamera();
                //play h264 file test
                //startPlayH264File();
                break;
            case R.id.btnClose:
                Log.e("start", "onclick");
                stopCamera();
                break;
        }
    }


    /**
     * 开启摄像头
     *
     * @return
     */
    private void startCamera() {
        if (bOpening) return;
        initializeVideo();

        mCamera.startPreview(); // Start Preview
//        try {
//            if (bOpening) {
//                Toast.makeText(MainActivity.this, "已经打开", Toast.LENGTH_SHORT).show();
//                return;
//            }
//            mCamera.startPreview(); // Start Preview
//            bOpening = true;
////            mCamera.unlock();
////            mMediaRecorder.prepare();
////            mMediaRecorder.start();
//        } catch (Exception e) {
//            Log.e("startcamera exception", e.toString());
//            Toast.makeText(MainActivity.this, "打开摄像机失败", Toast.LENGTH_SHORT).show();
//        }

    }

    /**
     * 关闭camera
     *
     * @return
     */
    private void stopCamera() {
        if (!bOpening) {
            Toast.makeText(MainActivity.this, "照相机还没有打开", Toast.LENGTH_SHORT).show();
            return;
        }
        mCamera.stopPreview();// stop preview
//        mMediaRecorder.stop();
        bOpening = false;
    }

    private void initSocket() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e("initSocket", "初始化socket");
                try {
                    socket = new DatagramSocket();
                    address = InetAddress.getByName(REMOTE_HOST);
                } catch (SocketException e) {
                    Toast.makeText(MainActivity.this, "socketException", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                    Log.e("SocketException", e.toString());
                } catch (UnknownHostException e) {
                    Toast.makeText(MainActivity.this, "unknownHostException", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                    Log.e("UnknownHostException", e.toString());
                }
            }
        });


    }

}

//闪光灯模式
//        FLASH_MODE_RED_EYE防红眼模式，减小或阻止图片上的人物像的红眼出现。
//        FLASH_MODE_TORCH填充模式，在正常光照下会减弱闪光强度。
//        FLASH_MODE_AUTO自动模式，有需要的时候会自动闪光。
//        FLASH_MODE_OFF 闪光模式将不会被关闭
//        FLASH_MODE_ON 快照时闪光模式将永远被关闭

//set
//03-06 15:37:13.491 21261-21261/com.d.dao.bishe E/start: onclick
//        03-06 15:37:13.751 21261-21261/? E/onPreviewFrame: onPreviewFrame
//        03-06 15:37:13.751 21261-21261/? E/offerEncoder2: offerEncoder InputBufSize: 1417216 inputSize: 1382400 bytes
//        03-06 15:37:13.761 21261-21261/? E/outputBufferIndex: -1
//        03-06 15:37:13.761 21261-21261/? E/ret: ret0
//        03-06 15:37:13.791 21261-21261/? E/onPreviewFrame: onPreviewFrame
//        03-06 15:37:13.791 21261-21261/? E/offerEncoder2: offerEncoder InputBufSize: 1417216 inputSize: 1382400 bytes
//        03-06 15:37:13.801 21261-21261/? E/outputBufferIndex: -2
//        03-06 15:37:13.801 21261-21261/? E/ret: ret0
//        03-06 15:37:13.841 21261-21261/? E/onPreviewFrame: onPreviewFrame
//        03-06 15:37:13.841 21261-21261/? E/offerEncoder2: offerEncoder InputBufSize: 1417216 inputSize: 1382400 bytes
//        03-06 15:37:13.841 21261-21261/? E/outputBufferIndex: 0
//        03-06 15:37:13.841 21261-21261/? E/offerEncoder: offerEncoder InputBufSize:1413120 outputSize:25 bytes written
//        03-06 15:37:13.841 21261-21261/? E/offerEncoder: offerEncoder InputBufSize:1413120 outputSize:3619 bytes written
//        03-06 15:37:13.841 21261-21261/? E/offerEncoder2: offer Encoder save sps head,len:3619
//        03-06 15:37:13.841 21261-21261/? E/ret: ret3644
//        03-06 15:37:13.841 21261-21261/? E/发送数据: 发送数据
//        03-06 15:37:13.851 21261-21261/? E/AndroidRuntime: FATAL EXCEPTION: main
//        Process: com.d.dao.bishe, PID: 21261
//        java.lang.NullPointerException
//        at com.d.dao.bishe.MainActivity.onPreviewFrame(MainActivity.java:198)
//        at android.hardware.Camera$EventHandler.handleMessage(Camera.java:960)
//        at android.os.Handler.dispatchMessage(Handler.java:102)
//        at android.os.Looper.loop(Looper.java:136)
//        at android.app.ActivityThread.main(ActivityThread.java:5052)
//        at java.lang.reflect.Method.invokeNative(Native Method)
//        at java.lang.reflect.Method.invoke(Method.java:515)
//        at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:796)
//        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:612)
//        at dalvik.system.NativeStart.main(Native Method)
