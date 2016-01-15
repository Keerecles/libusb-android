package com.usbtransfer;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

 
public class USBAccessoryAttachmentActivity extends Activity {
    private static final String TAG =
            USBAccessoryAttachmentActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkUsbAccessoryIntent("Create");
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkUsbAccessoryIntent("Resume");
    }

    private void checkUsbAccessoryIntent(String sourceAction) {
        final Intent intent = getIntent();
        String action = intent.getAction();
        Log.d(TAG, sourceAction + " with action: " + action);

        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            Intent usbAccessoryAttachedIntent =
                    new Intent(USBTransport.ACTION_USB_ACCESSORY_ATTACHED);
            usbAccessoryAttachedIntent.putExtra(UsbManager.EXTRA_ACCESSORY,
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY));
            usbAccessoryAttachedIntent
                    .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED,
                            intent.getParcelableExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED));
            sendBroadcast(usbAccessoryAttachedIntent);
        }

        finish();
    }
}
