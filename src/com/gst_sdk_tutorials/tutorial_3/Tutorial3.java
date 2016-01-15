package com.gst_sdk_tutorials.tutorial_3;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


//******************usb import ******************//
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

//**********************************************//



import com.gstreamer.GStreamer;

public class Tutorial3 extends Activity implements SurfaceHolder.Callback {
    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    private long native_custom_data;      // Native code will use this to keep private data

    private boolean is_playing_desired;   // Whether the user asked to go to PLAYING

    private static final String ACTION_USB_PERMISSION = "com.gst_sdk_tutorials.tutorial_3.USB_PERMISSION";

    private Thread mReaderThread = null;
   

    

    UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    UsbAccessory mAccessory;
    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream; 
 // 1) find accessory
    mAccessory = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
    

    
    
    
    
    

    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Initialize GStreamer and warn if it fails
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish(); 
            return;
        }

        setContentView(R.layout.main);

        //****************register usblistener********************//
        


        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);


        mUsbManager.requestPermission(mAccessory, mPermissionIntent);


        //******************************************************//


        ImageButton play = (ImageButton) this.findViewById(R.id.button_play);
        play.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = true;
                nativePlay();
            }
        });

        ImageButton pause = (ImageButton) this.findViewById(R.id.button_stop);
        pause.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = false;
                nativePause();
            }
        });

        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        if (savedInstanceState != null) {
            is_playing_desired = savedInstanceState.getBoolean("playing");
            Log.i ("GStreamer", "Activity created. Saved state is playing:" + is_playing_desired);
        } else {
            is_playing_desired = false;
            Log.i ("GStreamer", "Activity created. There is no saved state, playing: false");
        }

        // Start with disabled buttons, until native code is initialized
        this.findViewById(R.id.button_play).setEnabled(false);
        this.findViewById(R.id.button_stop).setEnabled(false);

        nativeInit();
    }


    //*****************************usb***************************************//

    // 2) list the accessory
    


    // 3) requestPermission

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                          //call method to set up device communication
                       }
                    } 
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    // 4) connect to the accessory
    
   
    private void openAccessory() {
        Log.d("Gstreamer", "openAccessory: " + mAccessory);
        mFileDescriptor = mUsbManager.openAccessory(mAccessory);
        if (mFileDescriptor != null) {
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            mReaderThread = new Thread(new USBTransportReader());
            mReaderThread.start();
        }
    }



     // 5) End the connect
    
    
    // BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
    //     public void onReceive(Context context, Intent intent) {
    //         String action = intent.getAction(); 

    //         if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
    //             UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
    //             if (accessory != null) {
    //                 // call your method that cleans up and closes communication with the accessory
    //             }
    //         }
    //     }
    // };



    //**************************************************************************//














    protected void onSaveInstanceState (Bundle outState) {
        Log.d ("GStreamer", "Saving state, playing:" + is_playing_desired);
        outState.putBoolean("playing", is_playing_desired);
    }

    protected void onDestroy() {
        nativeFinalize();
        super.onDestroy();
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
        runOnUiThread (new Runnable() {
          public void run() {
            tv.setText(message);
          }
        });
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
        Log.i ("GStreamer", "Gst initialized. Restoring state, playing:" + is_playing_desired);
        // Restore previous playing state
        if (is_playing_desired) {
            nativePlay();
        } else {
            nativePause();
        }

        // Re-enable buttons, now that GStreamer is initialized
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            public void run() {
                activity.findViewById(R.id.button_play).setEnabled(true);
                activity.findViewById(R.id.button_stop).setEnabled(true);
            }
        });
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("tutorial-3");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize ();
    }




     private class USBTransportReader implements Runnable {
        
    	 
        private boolean isInterrupted() {
            return Thread.interrupted();
        }

        /**
         * Entry function that is called when the task is started. It attempts
         * to connect to the accessory, then starts a read loop until
         * interrupted.
         */
        @Override
        public void run() {
            Log.d("Gstreamer","USB reader started!");  
                readFromTransport();
            Log.d("Gstreamer","USB reader finished!");
        }

       

        /**
         * Continuously reads data from the transport's input stream, blocking
         * when no data is available.
         */
        private void readFromTransport() {
            final int READ_BUFFER_SIZE = 4096;
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int bytesRead;

            // read loop
            while (!isInterrupted()) {
                try {
                    bytesRead = mInputStream.read(buffer);
                    if (bytesRead == -1) {
                        if (isInterrupted()) {
                            Log.d("Gstreamer","EOF reached, and thread is interrupted");
                        } else {
                            Log.d("Gstreamer","EOF reached, disconnecting!");
                            //disconnect("EOF reached", null);
                        }
                        return;
                    }
                } catch (IOException e) {
                    if (isInterrupted()) {
                        Log.d("Gstreamer","Can't read data, and thread is interrupted", e);
                    } else {
                        Log.d("Gstreamer","Can't read data, disconnecting!", e);
                        //disconnect("Can't read data from USB", e);
                    }
                    return;
                }

                Log.d("Gstreamer","Read " + bytesRead + " bytes");

               

                if (bytesRead > 0) {
                    synchronized (this) {
                    /*
                    向上层通知 设备断开链接
                    */
                    // handleReceivedBytes(buffer, bytesRead);
                    }
                }
            }
        }
	}

}
