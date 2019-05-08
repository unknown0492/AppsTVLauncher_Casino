package com.excel.appstvlauncher.casino;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.VideoView;

import com.excel.appstvlauncher.casino.perfecttime.PerfectTimeService;
import com.excel.configuration.ConfigurationReader;
import com.excel.excelclasslibrary.RetryCounter;
import com.excel.excelclasslibrary.UtilFile;
import com.excel.excelclasslibrary.UtilMisc;
import com.excel.excelclasslibrary.UtilNetwork;
import com.excel.excelclasslibrary.UtilSharedPreferences;
import com.excel.excelclasslibrary.UtilShell;
import com.excel.excelclasslibrary.UtilURL;
import com.excel.util.MD5;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;
import java.util.Stack;

import static com.excel.appstvlauncher.casino.Constants.SPFS_NAME;
import static com.excel.appstvlauncher.casino.Constants.VIDEO_FILE_NAME;
import static com.excel.appstvlauncher.casino.Constants.VIDEO_TEMP_FILE_NAME;

public class MainActivity extends Activity {

    final static String TAG = "MainActivity";
    VideoView vv_video_bg;
    Button bt_ok;
    TextView tv_room_no;
    Context context;

    ConfigurationReader configurationReader;
    int pausedAt = -1;      // Pausing background video onPause time

    SharedPreferences spfs;
    VideoInfo videoInfo;
    DownloadManager downloadManager;
    long downloadReference = -1;

    RetryCounter retryCounter;
    String web_md5 = "";

    Stack<String> key_combination = new Stack<String>();
    static String Z = "KEYCODE_Z";
    static String K = "KEYCODE_K";
    static String X = "KEYCODE_X";
    static String P = "KEYCODE_P";
    static String O = "KEYCODE_O";
    static String ONE = "KEYCODE_1";
    static String THREE = "KEYCODE_3";
    static String NINE = "KEYCODE_9";
    static String SEVEN = "KEYCODE_7";
    static String DOT = "KEYCODE_PERIOD";
    String ALPHABET = "KEYCODE_";

    BroadcastReceiver perfectTimeReceiver;
    boolean isTurnedOn = false;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        init();
        registerDownloadCompleteReceiver();

        downloadWelcomeVideo();

        startPerfectTimeService();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {

                if( !isTurnedOn ) {
                    Log.d( TAG, "Running after 30 secs" );
                    UtilMisc.startApplicationUsingPackageName(context, "com.excel.livetv");
                    isTurnedOn = true;
                }
            }
        }, 30000);

    }

    private void setRetryTimer(){
        final long time = retryCounter.getRetryTime();

        Log.d( TAG, "time : " + time );

        new Handler( Looper.getMainLooper() ).postDelayed(new Runnable() {

            @Override
            public void run() {
                Log.d( TAG, "downloading wallpapers config after "+(time/1000)+" seconds !" );
                /*GetLauncherConfig glc = new GetLauncherConfig();
                glc.execute( "" );*/
                AsyncVideoInfo asyncVideoInfo = new AsyncVideoInfo();
                asyncVideoInfo.execute();

            }

        }, time );




    }

    private void init(){
        context = this;
        configurationReader = ConfigurationReader.getInstance();
        retryCounter = new RetryCounter( "casino_download_count" );
        videoInfo = new VideoInfo( context );

        vv_video_bg = (VideoView) findViewById( R.id.vv_video_bg );
        bt_ok = (Button) findViewById( R.id.bt_ok );
        tv_room_no = (TextView) findViewById( R.id.tv_room_no );

        tv_room_no.setText( configurationReader.getRoomNo() );
    }

    private void downloadWelcomeVideo(){
        // Check to download video only once per reboot, ONLY once successful verification done
        if( !VideoInfo.requireTryVideoDownload() ){
            Log.d( TAG, "Don't need to check to download video this time, already checked once !" );
            return;
        }

        // If there is existing video on the sdcard, then start playing it
        if( VideoInfo.doesVideoExist() ){
            initMediaPlayer();

        }
        //else{
            // Check if the md5 of existing video on the box is matching the md5 of the video on the CMS, if it is same, then do not download it again
            AsyncVideoInfo asyncVideoInfo = new AsyncVideoInfo();
            asyncVideoInfo.execute();


        //}




    }


    private void initMediaPlayer(){
        Uri uri = Uri.fromFile( new File( "/mnt/sdcard/appstv_data/welcome.mp4" ) );
        vv_video_bg.setVideoURI( uri );
        vv_video_bg.setOnPreparedListener( new MediaPlayer.OnPreparedListener() {

            @Override
            public void onPrepared( MediaPlayer mediaPlayer ) {
                mediaPlayer.setLooping( true );
            }

        });
        vv_video_bg.start();
    }

    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        Log.d( TAG, "Pressed : " +keyCode );

        if( keyCode == KeyEvent.KEYCODE_DPAD_CENTER ){  // OK button pressed
            Log.d( TAG, "OK Pressed" );
            // Open LiveTV App
            UtilMisc.startApplicationUsingPackageName( context, "com.excel.livetv" );
            //UtilMisc.startApplicationUsingPackageName( context, "com.excel.starhublivetv" );
            return true;
        }
        else if( keyCode == KeyEvent.KEYCODE_BACK ){
            return true;
        }

        // Short-Cut key toggling
        shortCutKeyMonitor( KeyEvent.keyCodeToString( keyCode ) );

        return super.onKeyDown( keyCode, event );
    }

    @Override
    public void onResume(){
        super.onResume();

        vv_video_bg.seekTo( pausedAt );
        vv_video_bg.start();

        configurationReader = ConfigurationReader.reInstantiate();
    }

    @Override
    public void onPause(){
        super.onPause();

        pausedAt = vv_video_bg.getCurrentPosition();
        vv_video_bg.pause();

    }

    public void shortCutKeyMonitor( String key_name ){
        key_combination.push( key_name );

        if( key_combination.size() == 3 ){
            String key_3 = key_combination.pop();
            String key_2 = key_combination.pop();
            String key_1 = key_combination.pop();

            // Z-K-Z
            if( key_1.equals( Z ) && key_2.equals( K ) && key_3.equals( Z ) ){
                Intent in = new Intent( context, ShortcutsActivity.class );
                in.putExtra( "who", "zkz" );
                startActivity( in );
            }
            // X-K-X
            else if( key_1.equals( X ) && key_2.equals( K ) && key_3.equals( X ) ){
                Intent in = new Intent( context, ShortcutsActivity.class );
                in.putExtra( "who", "xkx" );
                startActivity( in );
            }

            // 1-.-1  -> ZKZ
            if( key_1.equals( ONE ) && key_2.equals( DOT ) && key_3.equals( ONE ) ){
                Intent in = new Intent( context, ShortcutsActivity.class );
                in.putExtra( "who", "zkz" );
                startActivity( in );
            }
            // 1-7-1 -> ZKZ
            if( key_1.equals( ONE ) && key_2.equals( SEVEN ) && key_3.equals( ONE ) ){
                Intent in = new Intent( context, ShortcutsActivity.class );
                in.putExtra( "who", "zkz" );
                startActivity( in );
            }
            // 3-1-3  -> XKX
            else if( key_1.equals( THREE ) && key_2.equals( ONE ) && key_3.equals( THREE ) ){
                Intent in = new Intent( context, ShortcutsActivity.class );
                in.putExtra( "who", "xkx" );
                startActivity( in );
            }
            // 9-1-9
            else if( key_1.equals( NINE ) && key_2.equals( ONE ) && key_3.equals( NINE ) ){
                UtilShell.executeShellCommandWithOp( "reboot" );
            }
            // P-O-P  -> Refresh Launcher
            else if( key_1.equals( P ) && key_2.equals( O ) && key_3.equals( P ) ){
                configurationReader = ConfigurationReader.reInstantiate();
                recreate();
            }
            // 9.9  -> Refresh Launcher
            else if( key_1.equals( NINE ) && key_2.equals( DOT ) && key_3.equals( NINE ) ){
                configurationReader = ConfigurationReader.reInstantiate();
                recreate();
            }
            key_combination.removeAllElements();
        }
    }


    public void startPerfectTimeService(){
        Intent in = new Intent( context, PerfectTimeService.class );
        startService( in );
    }



    class AsyncVideoInfo extends AsyncTask< String, Integer, String > {

        @Override
        protected String doInBackground( String... params ) {
            String url = UtilURL.getWebserviceURL();
            Log.d( TAG, "Webservice path : " + url );
            String response = UtilNetwork.makeRequestForData( url, "POST",
                    UtilURL.getURLParamsFromPairs( new String[][]{ { "what_do_you_want", "get_casino_video_info" } } ) );

            return response;
        }

        @Override
        protected void onPostExecute( String result ) {
            super.onPostExecute( result );

            Log.i( TAG,  "inside onPostExecute()" );

            if( result != null ){

                Log.i( TAG,  result );
                try {
                    JSONArray jsonArray = new JSONArray( result );
                    JSONObject jsonObject = jsonArray.getJSONObject( 0 );

                    String type = jsonObject.getString( "type" );
                    if( type.equals( "success" )){

                        JSONObject infoObject = jsonObject.getJSONObject( "info" );
                        web_md5 = infoObject.getString( "md5" );
                        final String video_url = infoObject.getString( "video_url" );

                        // Check if the md5 of existing video on the box is matching the md5 of the video on the CMS, if it is same, then do not download it again
                        if( ! videoInfo.getVideoMD5().equals( web_md5 ) ) {

                            // Trigger the download
                            new Thread(new Runnable(){

                                @Override
                                public void run(){

                                    File file_save_path = new File( ConfigurationReader.getAppstvDataRootDirectoryPath() + File.separator + VIDEO_TEMP_FILE_NAME );

                                    downloadManager = (DownloadManager) getSystemService( Context.DOWNLOAD_SERVICE );
                                    String url = UtilURL.getCMSRootPath() + video_url;
                                    Log.d( TAG, "url : "+url );
                                    Uri uri = Uri.parse( url );
                                    DownloadManager.Request request = new DownloadManager.Request( uri );
                                    //request.setNotificationVisibility( DownloadManager.Request.VISIBILITY_HIDDEN );
                                    //request.setDestinationInExternalFilesDir( context, getExternalFilesDir( "Launcher" ).getAbsolutePath(), file_name );
                                    if( file_save_path.exists() )
                                        file_save_path.delete();

                                    request.setDestinationUri( Uri.fromFile( file_save_path ) );
                                    downloadReference = downloadManager.enqueue( request );
                                }

                            }).start();


                            // Initialize this media player only when there is a video file available in the appstv_data directory
                            //initMediaPlayer();
                        }
                        else{
                            Log.d( TAG, "No new video found on the CMS !" );
                        }

                    }

                }
                catch ( JSONException e ) {
                    e.printStackTrace();
                    setRetryTimer();
                }
            }
            else{
                Log.e( TAG, "Null was returned " );
                setRetryTimer();
            }

        }
    }

    BroadcastReceiver receiverDownloadComplete;

    private void  registerDownloadCompleteReceiver(){
        IntentFilter intentFilter = new IntentFilter( DownloadManager.ACTION_DOWNLOAD_COMPLETE );
        receiverDownloadComplete = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                long reference = intent.getLongExtra( DownloadManager.EXTRA_DOWNLOAD_ID, -1 );
                // String extraID = DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS;
                // long[] references = intent.getLongArrayExtra( extraID );
                if( downloadReference == -1 )
                    return;

                    long ref = downloadReference;
                    //for( long ref : downloadReferences ){
                    if( ref == reference ){
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById( ref );
                        Cursor cursor = downloadManager.query( query );
                        cursor.moveToFirst();
                        int status = cursor.getInt( cursor.getColumnIndex( DownloadManager.COLUMN_STATUS ) );
                        String savedFilePath = cursor.getString( cursor.getColumnIndex( DownloadManager.COLUMN_LOCAL_FILENAME ) );
                        if( savedFilePath == null ) {
                            Log.e( TAG, "savedFilePath is null for : " + status);
                            return;
                        }
                        Log.d( TAG, "savedFilePath : " + savedFilePath);
                        savedFilePath = savedFilePath.substring( savedFilePath.lastIndexOf( "/" ) + 1, savedFilePath.length() );
                        switch( status ){
                            case DownloadManager.STATUS_SUCCESSFUL:
                                Log.i( TAG, savedFilePath + " downloaded successfully !" );
                                // Verify if the download was done perfectly, confirm md5
                                try {
                                    String file_md5 = MD5.getMD5Checksum( new File( ConfigurationReader.getAppstvDataRootDirectoryPath() + File.separator + VIDEO_TEMP_FILE_NAME ) );
                                    if( web_md5.equals( file_md5 ) ){
                                        if( vv_video_bg != null ){
                                            vv_video_bg.stopPlayback();
                                        }
                                        // Delete original welcome.mp4
                                        File og = new File( ConfigurationReader.getAppstvDataRootDirectoryPath() + File.separator + VIDEO_FILE_NAME );
                                        File new_video = new File( ConfigurationReader.getAppstvDataRootDirectoryPath() + File.separator + VIDEO_TEMP_FILE_NAME );

                                        if( og.exists() ){
                                            og.delete();
                                        }
                                        //UtilFile.copyFile( new_video, og );
                                        UtilShell.executeShellCommandWithOp( "cp /mnt/sdcard/appstv_data/"+VIDEO_TEMP_FILE_NAME+ " /mnt/sdcard/appstv_data/"+VIDEO_FILE_NAME );
                                        if( new_video.exists() ){
                                            new_video.delete();
                                        }
                                        videoInfo.setVideoMD5( web_md5 );
                                        initMediaPlayer();

                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }


                                retryCounter.reset();
                                break;
                            case DownloadManager.STATUS_FAILED:
                                Log.e( TAG, savedFilePath + " failed to download !" );
                                break;
                            case DownloadManager.STATUS_PAUSED:
                                Log.e( TAG, savedFilePath + " download paused !" );
                                break;
                            case DownloadManager.STATUS_PENDING:
                                Log.e( TAG, savedFilePath + " download pending !" );
                                break;
                            case DownloadManager.STATUS_RUNNING:
                                Log.d( TAG, savedFilePath + " downloading !" );
                                break;

                        }
                        downloadReference = -1;
                    }
                }

        };
        registerReceiver( receiverDownloadComplete, intentFilter );
    }
}