package com.excel.appstvlauncher.casino;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.excel.excelclasslibrary.UtilShell;

public class Receiver extends BroadcastReceiver {

    final static String TAG = "Receiver";

    @Override
    public void onReceive( Context context, Intent intent ) {

        Log.d( TAG, "Receiver called with action : "+intent.getAction() );

        if( intent.getAction().equals( "reboot" ) ){
            Log.d( TAG, "intent action : reboot" );
            UtilShell.executeShellCommandWithOp( "reboot" );
        }

    }
}
