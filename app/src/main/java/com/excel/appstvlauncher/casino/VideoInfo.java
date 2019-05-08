package com.excel.appstvlauncher.casino;

import android.content.Context;
import android.content.SharedPreferences;

import com.excel.configuration.ConfigurationReader;
import com.excel.excelclasslibrary.UtilSharedPreferences;
import com.excel.excelclasslibrary.UtilShell;
import com.excel.util.MD5;

import java.io.File;

import static com.excel.appstvlauncher.casino.Constants.SPFS_NAME;
import static com.excel.appstvlauncher.casino.Constants.VIDEO_FILE_NAME;
import static com.excel.appstvlauncher.casino.Constants.VIDEO_MD5;

public class VideoInfo {

    SharedPreferences spfs;
    String md5;

    public VideoInfo( Context context ){
        spfs = UtilSharedPreferences.createSharedPreference( context, SPFS_NAME );
    }

    public String getVideoMD5(){
        return (String) UtilSharedPreferences.getSharedPreference( spfs, VIDEO_MD5, "" );
    }

    public void setVideoMD5( String md5 ){
        UtilSharedPreferences.editSharedPreference( spfs, VIDEO_MD5, md5 );
    }

    // Try to download video only once per reboot
    // Return true if require download
    public static boolean requireTryVideoDownload(){
        String test = UtilShell.executeShellCommandWithOp( "getprop is_casino_vdo_dwnlded" );
        test = test.trim();
        return !test.equals( "1" );
    }

    public static void setVideoDownloaded(){
        UtilShell.executeShellCommandWithOp( "setprop is_casino_vdo_dwnlded 1" );
    }

    // Check if the video file exist on the sdcard
    public static boolean doesVideoExist(){
        File file = new File( ConfigurationReader.getAppstvDataRootDirectoryPath() + File.separator + VIDEO_FILE_NAME );
        if( file.exists() ){
            return true;
        }
        return false;
    }

}
