package com.example.lolcharger;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class ControlActivity extends Activity implements Runnable {
    public final static byte IDLE            =  0x01;
    public final static byte CLEAR_BUFFER    =  0x03;
    public final static byte PUSH_TO_BUFFER  =  0x04;
    public final static byte DISPLAY_BUFFER  =  0x05;
    public final static byte SHOW_PERCENT    =  0x06;
    public final static byte CLEAR_DISPLAY   =  0x07;
    public final static byte IS_BUSY         =  0x08;
    public final static byte IS_READY        =  0x09;
    
    //targets
    public final static byte TWEET_BUFFER    =  0x01;
    public final static byte MESSAGE_BUFFER  =  0x02;
    
    public final static String TAG = "lolCharger";
    private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";
    
    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    UsbAccessory mAccessory;
    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;
    TextView mOutputView;
    SeekBar mSlider;
    EditText mInput;
    Button mSendString;
    Button mSendBattPercent;
    int mBattPercent;
    RelativeLayout root;
    
    private final BroadcastReceiver mBattReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int scale = intent.getIntExtra("scale", -1);
            int rawlevel = intent.getIntExtra("level", -1);
            
            setBattPercent((rawlevel * 100) / scale);
        }
    };
    
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(mAccessory)) {
                    closeAccessory();
                }
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (savedInstanceState != null) {
            mAccessory = savedInstanceState.getParcelable(UsbManager.EXTRA_ACCESSORY);
        }
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        
        IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBattReceiver, battFilter); 
        
        setContentView(R.layout.activity_control);
        root = (RelativeLayout) findViewById(R.id.control_root);
        initSendString();
        
        initSlider();
        
        initSendBattPercent();
        
        initButtonClear();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(UsbManager.EXTRA_ACCESSORY, mAccessory);
    };
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_control, menu);
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
    }
 
    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }
    
    @Override
    public void onResume() {
        super.onResume();

        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            //updateOutput("mAccessory is null");
        }
    }
    
    private void setBattPercent(int percent) {
        mBattPercent = percent;
        mInput.setText(Integer.toString(mBattPercent));
        sendCommand(SHOW_PERCENT, (byte) 0x00, mBattPercent);
    }
    
    
    private void openAccessory(UsbAccessory accessory) {
        
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Thread thread = new Thread(null, this, "lolCharger");
            thread.start();
        } else {
            //filedescriptor is null
        }
    }
    
    private void closeAccessory() {
        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
        Intent i = new Intent(ControlActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override
    public void run() {
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        while (ret >= 0) {
            try {
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            i = 0;
            while (i < ret) {
                int len = ret - i;

                switch (buffer[i]) {
                case IS_BUSY:
                    if (len >= 3) {
                        Message m = Message.obtain(mHandler, IS_BUSY);
                        mHandler.sendMessage(m);
                    }
                    i += 3;
                    break;
                case IS_READY:
                    if (len >=3) {
                        Message m = Message.obtain(mHandler, IS_READY);
                        mHandler.sendMessage(m);
                    }
                }
            }
        }
        
    }
    
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case IS_BUSY:
                root.setBackgroundColor(Color.RED);
                break;
            case IS_READY:
                root.setBackgroundColor(Color.WHITE);
                break;
            }
        }
    };
    
    public void pushToBuffer(byte bufferTarget, String message) {
        sendCommand(CLEAR_BUFFER, bufferTarget, 0);
        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            sendCommand(PUSH_TO_BUFFER, bufferTarget, (int) chars[i]);
        }
    }
    
    public void sendCommand(byte command, byte target, int value) {
        byte[] buffer = new byte[3];
        if (value > 255)
            value = 255;

        buffer[0] = command;
        buffer[1] = target;
        buffer[2] = (byte) value;
        if (mOutputStream != null && buffer[1] != -1) {
            try {
                mOutputStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }

    
    private void initSendString() {
        mInput = (EditText) findViewById(R.id.userString);
        mInput.setText(Integer.toString(mBattPercent));
        mSendString = (Button) findViewById(R.id.btn_sendString);
        mSendString.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pushToBuffer(MESSAGE_BUFFER, mInput.getText().toString());
                sendCommand(DISPLAY_BUFFER, MESSAGE_BUFFER, 0);
            }
        });
    }
    
    private void initSlider() {
        mSlider = (SeekBar) findViewById(R.id.slider);
        mSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sendCommand(SHOW_PERCENT, (byte) 0x00, progress);
            }
        });
    }
    
    private void initSendBattPercent() {
        findViewById(R.id.btn_send_batt_percent)
        .setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                pushToBuffer(MESSAGE_BUFFER, Integer.toString(mBattPercent)+"%");
                sendCommand(DISPLAY_BUFFER, MESSAGE_BUFFER, 0);
                sendCommand(SHOW_PERCENT, (byte) 0x00, mBattPercent);
            }
        });    
    }
    
    private void initButtonClear() {
        findViewById(R.id.btn_clear)
        .setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                sendCommand(CLEAR_DISPLAY, (byte) 0x00, 0); 
            }
        }); 
    }
}
