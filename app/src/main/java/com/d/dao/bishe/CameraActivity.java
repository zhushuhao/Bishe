package com.d.dao.bishe;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.d.dao.bishe.bean.SettingInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;


public class CameraActivity extends AppCompatActivity implements
        SurfaceHolder.Callback, Camera.PreviewCallback, OnClickListener {


    private static final String TAG = "StudyCamera";
    private static final String H264FILE = "/sdcard/test.h264";


    //1280 720
    int width = 1280;
    int height = 720;
    int frameRate = 16;// 帧率
    int bitrate = 125000;//码率
    private String REMOTE_HOST = "192.168.1.102";
    private int REMOTE_HOST_PORT = 5000;

    private SettingInfo mSettingInfo;


    private boolean bOpening = false;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private Surface mSurface;
    private Camera mCamera;
    private int mSurfaceWidth, mSurfaceHeight;
    private MediaCodec mMediaEncoder;
    private MediaCodec mMediaDecoder;

    private InetAddress address;
    private DatagramSocket socket;
    private UdpSendTask netSendTask;
    private H264FileTask h264FileTask;

    byte[] h264;


    private int mFrameIndex = 0;
    private byte[] mEncoderH264Buf;
    private byte[] mMediaHead = null;

    private byte[] mYuvBuffer = new byte[1280 * 720 * 3 / 2];

    private SurfaceTexture surfaceTexture;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btnOpen).setOnClickListener(this);
        findViewById(R.id.btnClose).setOnClickListener(this);

        Log.e("CameraActivity", "CameraActivity");
        initOutData();

        initSurfaceView();

        surfaceTexture = new SurfaceTexture(10);

        keepScreenAlwaysLight();

        mMediaEncoder = null;
        mMediaDecoder = null;
        mSurface = null;


        netSendTask = new UdpSendTask();
        netSendTask.init();
        netSendTask.start();
    }

    private void initOutData() {
        Intent intent = getIntent();
        mSettingInfo = (SettingInfo) intent.getSerializableExtra("info");
        Log.e("info",mSettingInfo.toString());
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

        mEncoderH264Buf = new byte[width * height *3 /2];


    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnOpen: {
                startCamera();
                //play h264 file test
                //startPlayH264File();
            }
            break;
            case R.id.btnClose: {
                stopCamera();
            }
            break;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        //初始化摄像头
        mCamera = Camera.open();
        try {
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean mMediaRecorderRecording = false;

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.i(TAG, "surfaceChanged w:" + width + " h:" + height);
        mSurface = surfaceHolder.getSurface();

        mSurfaceWidth = width;
        mSurfaceHeight = height;

        //如果不是在录制
        if (!mMediaRecorderRecording) {
            initializeVideo();    //初始化视频录制的条件　
        }
    }

    private void initializeVideo() {
        if (surfaceHolder == null) {
            return;
        }
        mMediaRecorderRecording = true;
        Camera.Parameters parameters = mCamera.getParameters(); // Camera parameters to obtain
        parameters.setFlashMode(FLASH_MODE_OFF);//设置闪光灯模式
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);//设置相机照片白平衡
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);//自动对焦

        parameters.setPreviewSize(1280, 720);
        parameters.setPictureSize(1280, 720);
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
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        mSurface = null;
        releaseCamera();
//        avcCodec.close();
        mMediaRecorderRecording = false;
    }

    private PowerManager.WakeLock wl;

    private void keepScreenAlwaysLight() {
        //屏幕常亮
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "net.majorkernelpanic.spydroid.wakelock");
        wl.acquire();
    }

    @Override
    public void onPreviewFrame(byte[] rawData, Camera camera) {
        int w = camera.getParameters().getPreviewSize().width;
        int h = camera.getParameters().getPreviewSize().height;
        int format = camera.getParameters().getPreviewFormat();
//        Log.e(TAG, "preview frame format:" + format + " size:" + rawData.length + " w:" + w + " h:" + h);
        if (mMediaEncoder == null) {
            if (!setupEncoder("video/avc", w, h)) {
                releaseCamera();
                Log.e(TAG, "failed to setupEncoder");
                return;
            }
        }

//        assert (mSurface != null);
        if (mMediaDecoder == null) {
            if (!setupDecoder(mSurface, "video/avc", w, h)) {
                releaseCamera();
                Log.e(TAG, "failed to setupDecoder");
                return;
            }
        }
        //convert yv12 to i420
        swapYV12toI420(rawData, mYuvBuffer, w, h);
        System.arraycopy(mYuvBuffer, 0, rawData, 0, rawData.length);

        //set h264 buffer to zero.
        for (int i = 0; i < mEncoderH264Buf.length; i++)
            mEncoderH264Buf[i] = 0;
        int encoderRet = offerEncoder(rawData, mEncoderH264Buf);
        Log.e("ret", "" + encoderRet);
        if (encoderRet > 0) {
            Log.e(TAG, "encoder output h264 buffer len:" + encoderRet);
            /**
             * send to VLC client by udp://@port;
             */
            netSendTask.pushBuf(mEncoderH264Buf, encoderRet);

            /**
             * push data to decoder
             */
//            offerDecoder(mEncoderH264Buf, encoderRet);
        }


        //reset buff to camera.
        camera.addCallbackBuffer(rawData);
    }


    private void initSurfaceView() {
        if (null != surfaceHolder) {
            surfaceHolder.removeCallback(this);
            surfaceView = null;
        }
        if (null != surfaceView) {
            surfaceView = null;
        }

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setFixedSize(1280, 720);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(this);
    }

    private void startCamera() {
        try {
            if (bOpening) {
                Toast.makeText(CameraActivity.this, "已经打开", Toast.LENGTH_SHORT).show();
                return;
            }
            mCamera.startPreview(); // Start Preview
            bOpening = true;
//            mCamera.unlock();
//            mMediaRecorder.prepare();
//            mMediaRecorder.start();
        } catch (Exception e) {
            Log.e("startcamera exception", e.toString());
            Toast.makeText(CameraActivity.this, "打开摄像机失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopCamera() {
        if (!bOpening) {
            Toast.makeText(CameraActivity.this, "照相机还没有打开", Toast.LENGTH_SHORT).show();
            return;
        }
        mCamera.stopPreview();// stop preview
//        mMediaRecorder.stop();
        bOpening = false;
    }

    private void releaseCamera() {
        mCamera.setPreviewCallback(null); //！！这个必须在前，不然退出出错
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }


    private boolean setupEncoder(String mime, int width, int height) {
        int colorFormat = selectColorFormat(selectCodec(mime), mime);
        Log.d(TAG, "setupEncoder " + mime + " colorFormat:" + colorFormat + " w:" + width + " h:" + height);

        try {
            mMediaEncoder = MediaCodec.createEncoderByType(mime);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        //mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        mMediaEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaEncoder.start();

        return true;
    }

    private boolean setupDecoder(Surface surface, String mime, int width, int height) {
        Log.d(TAG, "setupDecoder surface:" + surface + " mime:" + mime + " w:" + width + " h:" + height);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, width, height);
        try {
            mMediaDecoder = MediaCodec.createDecoderByType(mime);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mMediaDecoder == null) {
            Log.e("DecodeActivity", "createDecoderByType fail!");
            return false;
        }

		/*
        int codecCount = MediaCodecList.getCodecCount();
		for(int i=0;i<codecCount;i++){
			MediaCodecInfo info =MediaCodecList.getCodecInfoAt(i);
			Log.d(TAG,"codec:"+info.getName());
		}*/

        mMediaDecoder.configure(mediaFormat, surface, null, 0);
        mMediaDecoder.start();

        return true;
    }

    private int offerEncoder(byte[] input, byte[] output) {

        int pos = 0;
        try {
            ByteBuffer[] inputBuffers = mMediaEncoder.getInputBuffers();
            ByteBuffer[] outputBuffers = mMediaEncoder.getOutputBuffers();
            int inputBufferIndex = mMediaEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                long timestamp = mFrameIndex++ * 1000000 / frameRate;

                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                Log.d(TAG, "offerEncoder InputBufSize: " + inputBuffer.capacity() + " inputSize: " + input.length + " bytes");
                inputBuffer.clear();
                inputBuffer.put(input);
                mMediaEncoder.queueInputBuffer(inputBufferIndex, 0, input.length, timestamp, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mMediaEncoder.dequeueOutputBuffer(bufferInfo, 0);
            Log.e("outputBufferIndex", "" + outputBufferIndex);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                byte[] data = new byte[bufferInfo.size];
                outputBuffer.get(data);

                Log.d(TAG, "offerEncoder InputBufSize:" + outputBuffer.capacity() + " outputSize:" + data.length + " bytes written");

                if (mMediaHead != null) {
                    System.arraycopy(data, 0, output, pos, data.length);
                    pos += data.length;
                } else // ����pps sps ֻ�п�ʼʱ ��һ��֡���У� ��������������
                {
                    Log.d(TAG, "offer Encoder save sps head,len:" + data.length);
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(data);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        mMediaHead = new byte[data.length];
                        System.arraycopy(data, 0, mMediaHead, 0, data.length);
                    } else {
                        Log.e(TAG, "not found media head.");
                        return -1;
                    }
                }

                mMediaEncoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mMediaEncoder.dequeueOutputBuffer(bufferInfo, 0);


            }

            if (output[4] == 0x65) //key frame   ���������ɹؼ�֡ʱֻ�� 00 00 00 01 65 û��pps sps�� Ҫ����
            {
                System.arraycopy(output, 0, input, 0, pos);
                System.arraycopy(mMediaHead, 0, output, 0, mMediaHead.length);
                System.arraycopy(input, 0, output, mMediaHead.length, pos);
                pos += mMediaHead.length;
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return pos;
    }


    private void offerDecoder(byte[] input, int length) {
        try {
            ByteBuffer[] inputBuffers = mMediaDecoder.getInputBuffers();
            int inputBufferIndex = mMediaDecoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                long timestamp = mFrameIndex++ * 1000000 / frameRate;
                Log.d(TAG, "offerDecoder timestamp: " + timestamp + " inputSize: " + length + " bytes");
                inputBuffer.clear();
                inputBuffer.put(input, 0, length);
                mMediaDecoder.queueInputBuffer(inputBufferIndex, 0, length, timestamp, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mMediaDecoder.dequeueOutputBuffer(bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                Log.d(TAG, "offerDecoder OutputBufSize:" + bufferInfo.size + " bytes written");
                //If a valid surface was specified when configuring the codec,
                //passing true renders this output buffer to the surface.
                mMediaDecoder.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = mMediaDecoder.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


    private void startPlayH264File() {
        assert (mSurface != null);
        if (mMediaDecoder == null) {
            if (!setupDecoder(mSurface, "video/avc", mSurfaceWidth, mSurfaceHeight)) {
                Log.e(TAG, "failed to setupDecoder");
                return;
            }
        }

        h264FileTask = new H264FileTask();
        h264FileTask.start();
    }


    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + width * height / 4, i420bytes, width * height, width * height / 4);
        System.arraycopy(yv12bytes, width * height, i420bytes, width * height + width * height / 4, width * height / 4);
    }


    private void readH264FromFile() {

        File file = new File(H264FILE);
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "failed to open h264 file.");
            return;
        }

        try {
            int len = 0;
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[1024];
            while ((len = fis.read(buf)) > 0) {
                offerDecoder(buf, len);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return;
    }


    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }


    class H264FileTask extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "fall in H264File Read thread");
            readH264FromFile();
        }
    }

    class UdpSendTask extends Thread {
        private ArrayList<ByteBuffer> mList;

        public void init() {
            try {
                socket = new DatagramSocket();
                address = InetAddress.getByName(REMOTE_HOST);
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

            mList = new ArrayList<>();

        }

        public void pushBuf(byte[] buf, int len) {
            ByteBuffer buffer = ByteBuffer.allocate(len);
            buffer.put(buf, 0, len);
            mList.add(buffer);
        }

        @Override
        public void run() {
            Log.e(TAG, "fall in udp send thread");
            while (true) {
                if (mList.size() <= 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                while (mList.size() > 0) {
                    ByteBuffer sendBuf = mList.get(0);
                    try {
                        DatagramPacket packet = new DatagramPacket(sendBuf.array(), sendBuf.capacity(), address, REMOTE_HOST_PORT);
                        socket.send(packet);
                        Log.e(TAG, "send udp packet len:" + sendBuf.capacity());

                    } catch (Throwable t) {
                        Log.e("e",t.toString());
                        t.printStackTrace();
                    }
                    mList.remove(0);
                }
            }
        }
    }

}
