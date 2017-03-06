package com.d.dao.bishe;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.d.dao.bishe.bean.HW;
import com.d.dao.bishe.bean.SettingInfo;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class SettingActivity extends AppCompatActivity {

    private ProgressActivity mContainer;
    private EditText et_server_ip;
    private Button btn_test_server;
    private TextView tv_device_info;
    private Spinner mVideoSizeSpinner;
    private EditText et_bit_rate;
    private EditText et_frame_rate;
    private EditText et_server_port;
    private Button btn_finish;

    private String mPhoneIp;//设备ip
    private int mBitRate = 2500000;
    private int mFrameRate = 20;

    private String mPort = "5000";

    private List<HW> mSizeList = new ArrayList<>();
    private HW mSelectedSize;

    private int mScreenWidth;
    private int mScreenHeight;

    private boolean testServerSuccess = false;

    private SweetAlertDialog mDialog;

    private boolean mClickFinish = false;//是否点击完成

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        initView();

        mContainer.showLoading();

        initDeviceData();//初始化设备信息
    }

    private void initView() {
        mContainer = (ProgressActivity) findViewById(R.id.container);

        et_server_ip = (EditText) findViewById(R.id.et_server_ip);
        btn_test_server = (Button) findViewById(R.id.btn_test_server);
        tv_device_info = (TextView) findViewById(R.id.tv_device_info);
        mVideoSizeSpinner = (Spinner) findViewById(R.id.spinner);
        et_bit_rate = (EditText) findViewById(R.id.et_bit_rate);
        et_frame_rate = (EditText) findViewById(R.id.et_frame_rate);
        btn_finish = (Button) findViewById(R.id.btn_finish);
        et_server_port = (EditText) findViewById(R.id.et_server_port);

        et_server_ip.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                testServerSuccess = false;
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        btn_finish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                if (TextUtils.isEmpty(et_server_ip.getText().toString().trim())) {
                    Toast.makeText(SettingActivity.this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                    et_server_ip.findFocus();
                    return;
                }


                if (!TextUtils.isEmpty(et_server_port.getText().toString().trim())) {
                    mPort = et_server_port.getText().toString().trim();
                }
                if (!TextUtils.isEmpty(et_bit_rate.getText().toString().trim())) {
                    mBitRate = Integer.parseInt(et_bit_rate.getText().toString().trim());
                }
                if (!TextUtils.isEmpty(et_frame_rate.getText().toString().trim())) {
                    mFrameRate = Integer.parseInt(et_frame_rate.getText().toString().trim());
                }

                if (!testServerSuccess) {
                    mClickFinish = true;
                    testServer();
                } else {
                    gotoMain();
                }

            }
        });

        findViewById(R.id.btn_finish2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(et_server_ip.getText().toString().trim())) {
                    Toast.makeText(SettingActivity.this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                    et_server_ip.findFocus();
                    return;
                }



                if (!TextUtils.isEmpty(et_server_port.getText().toString().trim())) {
                    mPort = et_server_port.getText().toString().trim();
                }
                if (!TextUtils.isEmpty(et_bit_rate.getText().toString().trim())) {
                    mBitRate = Integer.parseInt(et_bit_rate.getText().toString().trim());
                }
                if (!TextUtils.isEmpty(et_frame_rate.getText().toString().trim())) {
                    mFrameRate = Integer.parseInt(et_frame_rate.getText().toString().trim());
                }

                if (!testServerSuccess) {
                    mClickFinish = true;
                    testServer();
                } else {
                    gotoSetting();
                }

            }
        });

        btn_test_server.setOnClickListener(new View.OnClickListener() {
                                               @Override
                                               public void onClick(View v) {
                                                   if (TextUtils.isEmpty(et_server_ip.getText().toString().trim())) {
                                                       Toast.makeText(SettingActivity.this, "先输入服务器地址", Toast.LENGTH_SHORT).show();
                                                       et_server_ip.requestFocus();
                                                       et_server_ip.findFocus();
                                                       return;
                                                   }
// else if (TextUtils.isEmpty(et_server_port.getText().toString().trim())) {
//                    Toast.makeText(SettingActivity.this, "先输入服务器端口", Toast.LENGTH_SHORT).show();
//                    et_server_port.findFocus();
//                    return;
//                }
                                                   else {
                                                       testServer();
                                                   }
                                               }
                                           }

        );

    }

    private void testServer() {
        mDialog = new SweetAlertDialog(SettingActivity.this, SweetAlertDialog.PROGRESS_TYPE);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
        mDialog.setTitleText("服务器检测中...");
        mDialog.setCancelable(false);
        mDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String serverIp = et_server_ip.getText().toString().trim();
                Log.e("serverIp", serverIp);
                int status;
                try {
                    InetAddress address = InetAddress.getByName(serverIp);
                    if (address.isReachable(10000)) {//10秒内能连接上
                        status = 0;
                    } else {
                        status = 1;
                    }
                } catch (SocketException e) {
                    status = 1;
                    Toast.makeText(SettingActivity.this, "socketException", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                    Log.e("SocketException", e.toString());
                } catch (UnknownHostException e) {
                    status = 1;
                    Toast.makeText(SettingActivity.this, "unknownHostException", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                    Log.e("UnknownHostException", e.toString());

                } catch (IOException e) {
                    status = 1;
                    e.printStackTrace();
                    Log.e("IOException", e.toString());

                }
                switch (status) {
                    case 0:
                        testServerSuccess = true;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mDialog != null && mDialog.isShowing()) {
                                    mDialog.changeAlertType(SweetAlertDialog.SUCCESS_TYPE);
                                    mDialog.setTitleText("连接服务器成功");
                                    mContainer.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            mDialog.dismiss();
                                            if (mClickFinish) {
                                                gotoMain();
                                            }
                                        }
                                    }, 1000);
                                }
                            }
                        });
                        break;
                    case 1:
                    default:
                        if (mClickFinish) {
                            mClickFinish = false;
                        }
                        testServerSuccess = false;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mDialog != null && mDialog.isShowing()) {
                                    mDialog.changeAlertType(SweetAlertDialog.ERROR_TYPE);
                                    mDialog.setTitleText("无法连接到服务器");
                                    mDialog.setContentText("请修改服务器地址");
                                }
                            }
                        });
                        break;
                }

            }
        }).start();
    }

    private void initDeviceData() {

        mScreenWidth = Utils.getScreenWidth(SettingActivity.this);
        mScreenHeight = Utils.getScreenHeight(SettingActivity.this);

        mPhoneIp = Utils.getIp();

        //默认后摄像头
        Camera camera = Camera.open();
        Camera.Parameters parameters = camera.getParameters(); // Camera parameters to obtain

        //获取支持的视频尺寸
        List<Camera.Size> listSize = parameters.getSupportedVideoSizes();
        for (int i = 0; listSize != null && i < listSize.size(); i++) {
            Camera.Size size = listSize.get(i);
            HW hw = new HW(size.height, size.width);
            mSizeList.add(hw);
            Log.e("initVideo", "supportedSize:" + size.width + "-" + size.height);
        }

        List<Integer> listFormats = parameters.getSupportedPreviewFormats();
        for (int i = 0; i < listFormats.size(); i++) {
            Integer format = listFormats.get(i);
            Log.e("initVideo", "supportedFormat:" + format);
        }

        camera.release();
        camera = null;

        mContainer.showContent();

        updateDeviceInfo();

        updateSpinner();


    }

    private void gotoMain() {
        SettingInfo info = new SettingInfo();
        info.setServerIp(et_server_ip.getText().toString().trim());
        info.setServerPort(et_server_port.getText().toString().trim());
        info.setBitRate(mBitRate);
        info.setFrameRate(mFrameRate);
        info.setSelectSize(mSelectedSize);
        info.setSizeList(mSizeList);
        info.setServerPort(mPort);
        mClickFinish = false;

        Intent intent = new Intent(SettingActivity.this, MainActivity.class);
        intent.putExtra("info", info);
        startActivity(intent);
    }

    private void gotoSetting() {
        SettingInfo info = new SettingInfo();
        info.setServerIp(et_server_ip.getText().toString().trim());
        info.setServerPort(et_server_port.getText().toString().trim());
        info.setBitRate(mBitRate);
        info.setFrameRate(mFrameRate);
        info.setSelectSize(mSelectedSize);
        info.setSizeList(mSizeList);
        info.setServerPort(mPort);
        mClickFinish = false;

        Intent intent = new Intent(SettingActivity.this, CameraActivity.class);
        intent.putExtra("info", info);
        startActivity(intent);
    }


    private void updateDeviceInfo() {
        tv_device_info.setText("设备宽高:" + mScreenWidth + " * " + mScreenHeight + "   设备ip:" + mPhoneIp);
    }

    private void updateSpinner() {
        int size = mSizeList.size();
        final String[] items = new String[size];
        for (int i = 0; i < size; i++) {
            items[i] = mSizeList.get(i).getWidth() + " * " + mSizeList.get(i).getHeight();
        }
        // 建立Adapter并且绑定数据源
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        //绑定 Adapter到控件
        mVideoSizeSpinner.setAdapter(adapter);
        mVideoSizeSpinner.setSelection(0);
        mSelectedSize = mSizeList.get(0);
        mVideoSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {
                mSelectedSize = mSizeList.get(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
            }
        });
    }
}
