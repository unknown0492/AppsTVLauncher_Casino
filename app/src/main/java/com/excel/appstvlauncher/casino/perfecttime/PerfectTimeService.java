package com.excel.appstvlauncher.casino.perfecttime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.excel.excelclasslibrary.UtilNetwork;

import java.util.Calendar;
import java.util.Date;

public class PerfectTimeService extends Service {
	
	Context context = this;
	final static String TAG = "PerfectTimeService";
	PerfectTime pt = new PerfectTime( context );
	// PerfectTime pt;

	public PerfectTimeService(){
		pt = new PerfectTime( context );
		pt.setTimeFromSystem();
		// setRebootAlarm( context );
	}

	@Override
	public IBinder onBind( Intent intent ) {
		return null;
	}

	@Override
	public int onStartCommand( Intent intent, int flags, int startId ) {
		//pt = PerfectTime.getInstance( context );
		Log.d( TAG, "inside PerfectTimeService" );
        context = this;
		/**
		 *  Algorithm 
		 * (PerfectTimeService)
		 * 1. If time-not-synced = 0 OR "" (check from getprop)
		 *    2. If box not connected to Network
		 *    	 3. If Retry-Count <= 3
		 *			4. Set Retry Timer that after 10 seconds, Re-Run PerfectTimeService and Increment Retry-Count by 1
		 *		 5. If RetryCount > 3
		 *			6. Set time-not-synced = 0 (using setprop)
		 *			7. Set Time From System on the PerfectTime Object
		 *			8. Make Retry-Counter = 1
		 *			9. Start a Delay Timer that after 60 seconds, Re-Run PerfectTimeService
		 *	 10. If box is connected to Network 
		 *		 11. Run AsyncTime to retrieve millis from TimeServer (webservice)
		 *		 12. If result is not null
		 *			 13. Set Time From Internet on the PerfectTime Object
		 *			 14. Set time-not-synced = 1 (using setprop)
		 *		 15. If result is null
		 *			 16. [Same as step-9]
		 *			 17. Set time-not-synced = 0 (using setprop)
		 *			 17.1 [Same as step 7]
		 * 18. If time-not-synced = 1 (check from getprop)
		 * 	   19. [Same as step 7]
		 */
		
		// Step-1
		if( !isTimeSynced() ){
			
			// Step-2
			if( ! UtilNetwork.isConnectedToInternet( pt.getContext() ) ){   	
				
				// Step-3
				if( pt.getRetryCounter() <= pt.getMaxRetries() ){
					
					// Step-4
					setRetryTimer();
					
				}
				else{	// Step-5
					
					// Step-6
					pt.setWasInternetTimeSyncSuccessful( false );
					
					// Step-7
					pt.setTimeFromSystem();
					setRebootAlarm( context );

					// Step-8
					pt.resetRetryTimer();
					
					// Step-9
					setDelayTimer();
					
					String data = String.format( "%s-%s-%s  %s:%s:%s", pt.getDate(), pt.getMonth(), pt.getYear(), pt.getHours(), pt.getMinutes(), pt.getSeconds() );
					Log.d( TAG, "System Time : " + data );
					// Toast.makeText( context, "System Time : " + data, Toast.LENGTH_LONG ).show();

				}
			}
			else{	// Step-10
				
				// Step-11
				AsyncTime getMillis = new AsyncTime();
				getMillis.execute( "" );
			}
		}
		else{	// Step-18
			
			// Step-19 ~ Step-7
			pt.setTimeFromSystem();
			setRebootAlarm( context );
			
			String data = String.format( "%s-%s-%s  %s:%s:%s", pt.getDate(), pt.getMonth(), pt.getYear(), pt.getHours(), pt.getMinutes(), pt.getSeconds() );
			Log.d( TAG, "System Time : " + data );
			// Toast.makeText( context, "System Time : " + data, Toast.LENGTH_LONG ).show();
		}
		
		
        
        
		return START_NOT_STICKY;
	}
	
	class AsyncTime extends AsyncTask< String, Integer, String >{

		@Override
		protected String doInBackground( String... params ) {
			// pt.setTimeFromInternet();
			return pt.getMillisFromInternet();
		}

		@Override
		protected void onPostExecute( String millis ) {
			super.onPostExecute( millis );
			
			// Step-12
			if( millis != null ){
				
				//Step-13
				pt.setTimeFromInternet( Long.parseLong( millis ) );
				setRebootAlarm( context );
				
				// Step-14
				pt.setWasInternetTimeSyncSuccessful( true );
				
				String data = String.format( "%s-%s-%s  %s:%s:%s", pt.getDate(), pt.getMonth(), pt.getYear(), pt.getHours(), pt.getMinutes(), pt.getSeconds() );
				Log.d( TAG, "Internet Time : " + data + "," +millis );
				// Toast.makeText( context, "Internet Time : " + data, Toast.LENGTH_LONG ).show();
				  
			}
			else{	 // Step-15
				
				// Step-16 ~ Step-9
				setDelayTimer();
				
				// Step-17
				pt.setWasInternetTimeSyncSuccessful( false );
				
				// Step-17.1 ~ Step-7
				pt.setTimeFromSystem();
				setRebootAlarm( context );
				
				String data = String.format( "%s-%s-%s  %s:%s:%s", pt.getDate(), pt.getMonth(), pt.getYear(), pt.getHours(), pt.getMinutes(), pt.getSeconds() );
				Log.d( TAG, "Wrong System Time : " + data );
				// Toast.makeText( context, "Wrong System Time : " + data, Toast.LENGTH_LONG ).show();
				Log.e( TAG, "Result was null" );
			}
			
		}
		
		
		
	}
	
	public void setRetryTimer(){
		new Handler().postDelayed( new Runnable() {
			
			@Override
			public void run() {
				// Toast.makeText( context, "Retry timer attempt : "+pt.getRetryCounter(), Toast.LENGTH_LONG ).show();
				pt.setRetryCounter( pt.getRetryCounter() + 1 );
				pt.getContext().startService( new Intent( pt.getContext(), PerfectTimeService.class ) );
			}
			
		}, 10000 );
	}
	
	public void setDelayTimer(){
		new Handler().postDelayed( new Runnable() {
			
			@Override
			public void run() {
				// Toast.makeText( context, "Delay Timer Run", Toast.LENGTH_LONG ).show();
				pt.getContext().startService( new Intent( pt.getContext(), PerfectTimeService.class ) );
			}
			
		}, 60000 );	// 10000*60
	}
	
	public boolean isTimeSynced(){
		String wasInternetTimeSyncSuccessful = pt.getWasInternetTimeSyncSuccessful();
		Log.i( TAG, "isTimeSynced() : ,"+wasInternetTimeSyncSuccessful+"," );
		boolean b = (wasInternetTimeSyncSuccessful.equals( "1" ))?true:false;
		return b;
	}
	
	public void setRebootAlarm( Context context ){
		Log.d( "sender", "setRebootAlarm()" );
		/*Intent intent = new Intent( "send_update_to_clock" );
		intent.putExtra( "getHours()", pt.getHours() );
		intent.putExtra( "getMinutes()", pt.getMinutes() );
		intent.putExtra( "getSeconds()", pt.getSeconds() );
		intent.putExtra( "getDate()", pt.getDate() );
		intent.putExtra( "getMonth()", pt.getMonth() );
		intent.putExtra( "getYear()", pt.getYear() );
		
		LocalBroadcastManager.getInstance( context ).sendBroadcast( intent );*/

		// Alarm need to be set for Tuesday 5.30am

		AlarmManager am1 =( AlarmManager ) context.getSystemService( Context.ALARM_SERVICE );
		Intent in1 = new Intent( "reboot" );
		PendingIntent pi1 = PendingIntent.getBroadcast( context, 0, in1, 0 );

		Calendar calendarForNextRebootDate = Calendar.getInstance();
		calendarForNextRebootDate.set( Calendar.DAY_OF_WEEK, Calendar.TUESDAY );
		calendarForNextRebootDate.set( Calendar.HOUR_OF_DAY, 5 );
		calendarForNextRebootDate.set( Calendar.MINUTE, 30 );

		// If tuesday has passed for this week
        long currentMillis = System.currentTimeMillis();
        if( currentMillis >= calendarForNextRebootDate.getTimeInMillis() ){
            calendarForNextRebootDate.add( Calendar.DATE, 9  );
            Log.d( TAG, "Date after adding 7 days : " + new Date( calendarForNextRebootDate.getTimeInMillis() ).toString() );
            calendarForNextRebootDate.set( Calendar.DAY_OF_WEEK, Calendar.TUESDAY );
        }
		am1.set( AlarmManager.RTC_WAKEUP, calendarForNextRebootDate.getTimeInMillis(), pi1 );

		Log.d( TAG, "Alarm set for : " + new Date( calendarForNextRebootDate.getTimeInMillis() ).toString() );
		//calendarForNextRebootDate.set( Calendar.MONTH, calendarForNextRebootDate.get( Calendar.MONTH ));

	}

}
