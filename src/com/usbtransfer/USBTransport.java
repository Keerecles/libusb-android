package com.usbtransfer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.usbtransfer.SdlException;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class that implements USB transport.
 *
 * A note about USB Accessory protocol. If the device is already in the USB
 * accessory mode, any side (computer or Android) can open connection even if
 * the other side is not connected. Conversely, if one side simply disconnects,
 * the other side will NOT be notified and unblocked from reading data until
 * some data is sent again or the USB is physically disconnected.
 */
public class USBTransport {
   


    
    public Context mContext;

    /**
     * Broadcast action: sent when a USB accessory is attached.
     *
     * UsbManager.EXTRA_ACCESSORY extra contains UsbAccessory object that has
     * been attached.
     */
    public static final String ACTION_USB_ACCESSORY_ATTACHED =
            "com.gst_sdk_tutorials.USB_ACCESSORY_ATTACHED";
    /**
     * String tag for logging.
     */
    private static final String TAG = USBTransport.class.getSimpleName();
    
    /**
     * Broadcast action: sent when the user has granted access to the USB
     * accessory.
     */
    private static final String ACTION_USB_PERMISSION =
            "com.gst_sdk_tutorials.USB_PERMISSION";
    /**
     * Manufacturer name of the accessory we want to connect to. Must be the
     * same as in accessory_filter.xml to work properly.
     */
    private final static String ACCESSORY_MANUFACTURER = "EKAI";
    /**
     * Model name of the accessory we want to connect to. Must be the same as
     * in accessory_filter.xml to work properly.
     */
    private final static String ACCESSORY_MODEL = "Host";
    /**
     * Version of the accessory we want to connect to. Must be the same as in
     * accessory_filter.xml to work properly.
     */
    private final static String ACCESSORY_VERSION = "1.0";
    /**
     * Prefix string to indicate debug output.
     */
    private static final String DEBUG_PREFIX = "DEBUG: ";
    /**
     * String to prefix exception output.
     */
    private static final String EXCEPTION_STRING = " Exception String: ";
    /**
     * Broadcast receiver that receives different USB-related intents: USB
     * accessory connected, disconnected, and permission granted.
     */
    private final BroadcastReceiver mUSBReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("Gstreamer","USBReceiver Action: " + action);

            UsbAccessory accessory =
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (accessory != null) {
                if (ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                    Log.d("Gstreamer","Accessory " + accessory + " attached");
                    if (isAccessorySupported(accessory)) {
                        connectToAccessory(accessory);
                    } else {
                        Log.d("Gstreamer","Attached accessory is not supported!");
                    }
                } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED
                        .equals(action)) {
                    Log.d("Gstreamer","Accessory " + accessory + " detached");
                    final String msg = "USB accessory has been detached";
                    disconnect(msg, new SdlException(msg,
                            SdlExceptionCause.SDL_USB_DETACHED));
                } else if (ACTION_USB_PERMISSION.equals(action)) {
                    boolean permissionGranted = intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (permissionGranted) {
                        Log.d("Gstreamer","Permission granted for accessory " + accessory);
                        openAccessory(accessory);
                    } else {
                        final String msg =
                                "Permission denied for accessory " + accessory;
                        Log.d("Gstreamer",msg);
                        disconnect(msg, new SdlException(msg,
                                SdlExceptionCause.SDL_USB_PERMISSION_DENIED));
                    }
                }
            } else {
                Log.d("Gstreamer","Accessory is null");
            }
        }
    };
    
    
    /**
     * Current state of transport.
     *
     * Use setter and getter to access it.
     */
    private State mState = State.IDLE;
    /**
     * Current accessory the transport is working with if any.
     */
    private UsbAccessory mAccessory = null;
    /**
     * FileDescriptor that owns the input and output streams. We have to keep
     * it, otherwise it will be garbage collected and the streams will become
     * invalid.
     */
    private ParcelFileDescriptor mParcelFD = null;
    /**
     * Data input stream to read data from USB accessory.
     */
    private InputStream mInputStream = null;
    /**
     * Data output stream to write data to USB accessory.
     */
    private OutputStream mOutputStream = null;
    /**
     * Thread that connects and reads data from USB accessory.
     *
     * @see USBTransportReader
     */
    private Thread mReaderThread = null;

    /**
     * Constructs the USBTransport instance.
     *
     * @param usbTransportConfig Config object for the USB transport
     * @param transportListener  Listener that gets notified on different
     *                           transport events
     */
    public USBTransport() {
//        super(transportListener);
        
    	registerReciever();
    }

    /**
     * Returns the current state of transport.
     *
     * @return Current state of transport
     */
    public State getState() {
        return this.mState;
    }

    /**
     * Changes current state of transport.
     *
     * @param state New state
     */
    private void setState(State state) {
        Log.d("Gstreamer","Changing state " + this.mState + " to " + state);
        this.mState = state;
    }

    /**
     * Sends the array of bytes over USB.
     *
     * @param msgBytes Array of bytes to send
     * @param offset   Offset in the array to start from
     * @param length   Number of bytes to send
     * @return true if the bytes are sent successfully
     */
    public boolean sendBytesOverTransport(byte[] msgBytes, int offset,
                                             int length) {
        Log.d("Gstreamer","SendBytes: array size " + msgBytes.length + ", offset " + offset +
                ", length " + length);

        boolean result = false;
        final State state = getState();
        switch (state) {
            case CONNECTED:
                    if (mOutputStream != null) {
                        try {
                            mOutputStream.write(msgBytes, offset, length);
                            result = true;

                            Log.d("Gstreamer","Bytes successfully sent");
//                            SdlTrace.logTransportEvent(TAG + ": bytes sent",
//                                    null, InterfaceActivityDirection.Transmit,
//                                    msgBytes, offset, length,
//                                    SDL_LIB_TRACE_KEY);
                        } catch (IOException e) {
                            final String msg = "Failed to send bytes over USB";
                            Log.d("Gstreamer",msg, e);
                            /*
                            向上层通知 设备断开链接
                            */
//                            handleTransportError(msg, e);
                        }
                    } else {
                        final String msg =
                                "Can't send bytes when output stream is null";
                        Log.d("Gstreamer",msg);
                        /*
                        向上层通知 设备断开链接
                        */
//                        handleTransportError(msg, null);
                    }
                break;

            default:
                Log.d("Gstreamer","Can't send bytes from " + state + " state");
                break;
        }

        return result;
    }

    
    public void registerReciever()
    {
    	IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);        
        getContext().registerReceiver(mUSBReceiver, filter);
    }
    
    /**
     * Opens a USB connection if not open yet.
     *
     * @throws SdlException
     */
    
    public void openConnection() throws SdlException {
        final State state = getState();
        switch (state) {
            case IDLE:
                synchronized (this) {
                    Log.d("Gstreamer","openConnection()");
                    setState(State.LISTENING);
                }

                Log.d("Gstreamer","Registering receiver");
                try {
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(ACTION_USB_ACCESSORY_ATTACHED);
                    filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
                    filter.addAction(ACTION_USB_PERMISSION);
//                  getContext().registerReceiver(mUSBReceiver, filter);

                    initializeAccessory();
                } catch (Exception e) {
                    String msg = "Couldn't start opening connection";
                    Log.d("Gstreamer",msg, e);
                    throw new SdlException(msg, e,
                            SdlExceptionCause.SDL_CONNECTION_FAILED);
                }

                break;

            default:
                Log.d("Gstreamer","openConnection() called from state " + state +
                        "; doing nothing");
                break;
        }
    }

    /**
     * Closes the USB connection if open.
     */
  
    public void disconnect() {
        disconnect(null, null);
    }

    /**
     * Asks the reader thread to stop while it's possible. If it's blocked on
     * read(), there is no way to stop it except for physical USB disconnect.
     */
    
    public void stopReading() {
    	Log.d("Gstreamer","USBTransport: stop reading requested, doing nothing");
        //DebugTool.Log.dnfo("USBTransport: stop reading requested, doing nothing");
        // TODO - put back stopUSBReading(); @see <a href="https://adc.luxoft.com/jira/browse/SmartDeviceLink-3450">SmartDeviceLink-3450</a>
    }

    @SuppressWarnings("unused")
    private void stopUSBReading() {
        final State state = getState();
        switch (state) {
            case CONNECTED:
                Log.d("Gstreamer","Stopping reading");
                synchronized (this) {
                    stopReaderThread();
                }
                break;

            default:
                Log.d("Gstreamer","Stopping reading called from state " + state +
                        "; doing nothing");
                break;
        }
    }

    /**
     * Actually asks the reader thread to interrupt.
     */
    private void stopReaderThread() {
        if (mReaderThread != null) {
            Log.d("Gstreamer","Interrupting USB reader");
            mReaderThread.interrupt();
            // don't join() now
            mReaderThread = null;
        } else {
            Log.d("Gstreamer","USB reader is null");
        }
    }

    /**
     * Closes the USB connection from inside the transport with some extra info.
     *
     * @param msg Disconnect reason message, if any
     * @param ex  Disconnect exception, if any
     */
    private void disconnect(String msg, Exception ex) {
        final State state = getState();
        switch (state) {
            case LISTENING:
            case CONNECTED:
                synchronized (this) {
                    Log.d("Gstreamer","Disconnect from state " + getState() + "; message: " +
                            msg + "; exception: " + ex);
                    setState(State.IDLE);

//                    SdlTrace.logTransportEvent(TAG + ": disconnect", null,
//                            InterfaceActivityDirection.None, null, 0,
//                            SDL_LIB_TRACE_KEY);

                    stopReaderThread();

                    if (mAccessory != null) {
                        if (mOutputStream != null) {
                            try {
                                mOutputStream.close();
                            } catch (IOException e) {
                                Log.d("Gstreamer","Can't close output stream", e);
                                mOutputStream = null;
                            }
                        }
                        if (mInputStream != null) {
                            try {
                                mInputStream.close();
                            } catch (IOException e) {
                                Log.d("Gstreamer","Can't close input stream", e);
                                mInputStream = null;
                            }
                        }
                        if (mParcelFD != null) {
                            try {
                                mParcelFD.close();
                            } catch (IOException e) {
                                Log.d("Gstreamer","Can't close file descriptor", e);
                                mParcelFD = null;
                            }
                        }

                        mAccessory = null;
                    }
                }

                Log.d("Gstreamer","Unregistering receiver");
                try {
//                    getContext().unregisterReceiver(mUSBReceiver);
                } catch (IllegalArgumentException e) {
                    Log.d("Gstreamer","Receiver was already unregistered", e);
                }

                String disconnectMsg = (msg == null ? "" : msg);
                if (ex != null) {
                    disconnectMsg += ", " + ex.toString();
                }

                if (ex == null) {
                    // This disconnect was not caused by an error, notify the
                    // proxy that the transport has been disconnected.
                    Log.d("Gstreamer","Disconnect is correct. Handling it");
                    /*
                    向上层通知 设备断开链接
                    */
                    
//                    handleTransportDisconnected(disconnectMsg);
                } else {
                    // This disconnect was caused by an error, notify the proxy
                    // that there was a transport error.
                    Log.d("Gstreamer","Disconnect is incorrect. Handling it as error");
                    /*
                    向上层通知 设备断开链接
                    */
//                  handleTransportError(disconnectMsg, ex);
                }
                break;

            default:
                Log.d("Gstreamer","Disconnect called from state " + state +
                        "; doing nothing");
                break;
        }
    }

 
 
    /**
     * Looks for an already connected compatible accessory and connect to it.
     */
    private void initializeAccessory() {
        Log.d("Gstreamer","Looking for connected accessories");
        UsbManager usbManager = getUsbManager();
        UsbAccessory[] accessories = usbManager.getAccessoryList();
        if (accessories != null) {
            Log.d("Gstreamer","Found total " + accessories.length + " accessories");
            for (UsbAccessory accessory : accessories) {
                if (isAccessorySupported(accessory)) {
                    connectToAccessory(accessory);
                    break;
                }
            }
        } else {
            Log.d("Gstreamer","No connected accessories found");
        }
    }

    /**
     * Checks if the specified connected USB accessory is what we expect.
     *
     * @param accessory Accessory to check
     * @return true if the accessory is right
     */
    private boolean isAccessorySupported(UsbAccessory accessory) {
        boolean manufacturerMatches =
                ACCESSORY_MANUFACTURER.equals(accessory.getManufacturer());
        boolean modelMatches = ACCESSORY_MODEL.equals(accessory.getModel());
        boolean versionMatches =
                ACCESSORY_VERSION.equals(accessory.getVersion());
        return manufacturerMatches && modelMatches && versionMatches;
    }

    /**
     * Attempts to connect to the specified accessory.
     *
     * If the permission is already granted, opens the accessory. Otherwise,
     * requests permission to use it.
     *
     * @param accessory Accessory to connect to
     */
    private void connectToAccessory(UsbAccessory accessory) {
        final State state = getState();
        switch (state) {
            case LISTENING:
                UsbManager usbManager = getUsbManager();
                if (usbManager.hasPermission(accessory)) {
                    Log.d("Gstreamer","Already have permission to use " + accessory);
                    openAccessory(accessory);
                } else {
                    Log.d("Gstreamer","Requesting permission to use " + accessory);

                    PendingIntent permissionIntent = PendingIntent
                            .getBroadcast(getContext(), 0,
                                    new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(accessory, permissionIntent);
                }

                break;

            default:
                Log.d("Gstreamer","connectToAccessory() called from state " + state +
                        "; doing nothing");
        }
    }

    /**
     * Returns the UsbManager to use with accessories.
     *
     * @return System UsbManager
     */
    private UsbManager getUsbManager() {
        return (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
    }

    /**
     * Opens a connection to the accessory.
     *
     * When this function is called, the permission to use it must have already
     * been granted.
     *
     * @param accessory Accessory to open connection to
     */
    private void openAccessory(UsbAccessory accessory) {
        final State state = getState();
        switch (state) {
            case LISTENING:
                synchronized (this) {
                    Log.d("Gstreamer","Opening accessory " + accessory);
                    mAccessory = accessory;

                    mReaderThread = new Thread(new USBTransportReader());
                    mReaderThread.setDaemon(true);
                    mReaderThread
                            .setName(USBTransportReader.class.getSimpleName());
                    mReaderThread.start();

//                    // Initialize the SiphonServer
//                    if (SiphonServer.getSiphonEnabledStatus()) {
//                    	SiphonServer.init();
//                    }
                }

                break;

            default:
                Log.d("Gstreamer","openAccessory() called from state " + state +
                        "; doing nothing");
        }
    }

 

    /**
     * Logs the string and the throwable with WARN level.
     *
     * @param s  string to log
     * @param tr throwable to log
     */
//    private void Log.d("Gstreamer",String s, Throwable tr) {
//        StringBuilder res = new StringBuilder(s);
//        if (tr != null) {
//            res.append(EXCEPTION_STRING);
//            res.append(tr.toString());
//        }
//        Log.d("Gstreamer",res.toString());
//    }






    /**
     * Returns Context to communicate with the OS.
     *
     * @return current context to be used by the USB transport
     */
    private Context getContext() {
        return mContext;
    }

    /**
     * Possible states of the USB transport.
     */
    private enum State {
        /**
         * Transport initialized; no connections.
         */
        IDLE,

        /**
         * USB accessory not attached; SdlProxy wants connection as soon as
         * accessory is attached.
         */
        LISTENING,

        /**
         * USB accessory attached; permission granted; data IO in progress.
         */
        CONNECTED
    }

    /**
     * Internal task that connects to and reads data from a USB accessory.
     *
     * Since the class has to have access to the parent class' variables,
     * sdlhronization must be taken in consideration! For now, all access
     * to variables of USBTransport must be surrounded with
     * synchronized (USBTransport.this) { … }
     */
    private class USBTransportReader implements Runnable {
        /**
         * String tag for logging inside the task.
         */
        private final String TAG = USBTransportReader.class.getSimpleName();

        /**
         * Checks if the thread has been interrupted.
         *
         * @return true if the thread has been interrupted
         */
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

            if (connect()) {
                readFromTransport();
            }

            Log.d("Gstreamer","USB reader finished!");
        }

        /**
         * Attemps to open connection to USB accessory.
         *
         * @return true if connected successfully
         */
        private boolean connect() {
            if (isInterrupted()) {
                Log.d("Gstreamer","Thread is interrupted, not connecting");
                return false;
            }

            final State state = getState();
            switch (state) {
                case LISTENING:

                    synchronized (USBTransport.this) {
                        try {
                            mParcelFD =
                                    getUsbManager().openAccessory(mAccessory);
                        } catch (Exception e) {
                            final String msg =
                                    "Have no permission to open the accessory";
                            Log.d("Gstreamer",msg, e);
                            disconnect(msg, e);
                            return false;
                        }
                        if (mParcelFD == null) {
                            if (isInterrupted()) {
                                Log.d("Gstreamer","Can't open accessory, and thread is interrupted");
                            } else {
                                Log.d("Gstreamer","Can't open accessory, disconnecting!");
                                String msg = "Failed to open USB accessory";
                                disconnect(msg, new SdlException(msg,
                                        SdlExceptionCause.SDL_CONNECTION_FAILED));
                            }
                            return false;
                        }
                        FileDescriptor fd = mParcelFD.getFileDescriptor();
                        mInputStream = new FileInputStream(fd);
                        mOutputStream = new FileOutputStream(fd);
                    }

                    Log.d("Gstreamer","Accessory opened!");

                    synchronized (USBTransport.this) {
                        setState(State.CONNECTED);
                        
                        /*
                        向上层通知 设备断开链接
                        */

                        // handleTransportConnected();
                    }
                    break;

                default:
                    Log.d("Gstreamer","connect() called from state " + state +
                            ", will not try to connect");
                    return false;
            }

            return true;
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
                            disconnect("EOF reached", null);
                        }
                        return;
                    }
                } catch (IOException e) {
                    if (isInterrupted()) {
                        Log.d("Gstreamer","Can't read data, and thread is interrupted", e);
                    } else {
                        Log.d("Gstreamer","Can't read data, disconnecting!", e);
                        disconnect("Can't read data from USB", e);
                    }
                    return;
                }

                Log.d("Gstreamer","Read " + bytesRead + " bytes");
//                SdlTrace.logTransportEvent(TAG + ": read bytes", null,
//                        InterfaceActivityDirection.Receive, buffer, bytesRead,
//                        SDL_LIB_TRACE_KEY);

                if (isInterrupted()) {
                    Log.d("Gstreamer","Read some data, but thread is interrupted");
                    return;
                }

                if (bytesRead > 0) {
                    synchronized (USBTransport.this) {
                    /*
                    向上层通知 设备断开链接
                    */
                    // handleReceivedBytes(buffer, bytesRead);
                    }
                }
            }
        }



        // private void Log.d("Gstreamer",String s, Throwable tr) {
        //     StringBuilder res = new StringBuilder(s);
        //     if (tr != null) {
        //         res.append(EXCEPTION_STRING);
        //         res.append(tr.toString());
        //     }
        //     Log.d("Gstreamer",res.toString());
        // }


    }

	
	public String getBroadcastComment() {
		
		return null;
	}
}
