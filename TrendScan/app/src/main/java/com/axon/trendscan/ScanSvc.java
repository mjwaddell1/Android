package com.axon.trendscan;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;


import java.util.*;

import static android.app.PendingIntent.getActivity;

public class ScanSvc extends Service { //runs in background

	//public static boolean mSvcStarted = false;
	public static boolean mRefreshOnly = false; //run service only to show stock list, then end
	public static String strStockList = "<not set>"; //for notification
	public static Date dtLastCheck = new Date(); //shown on GUI
	public static long CheckGap = 15 * 60 * 1000L; //time between checks, 15 minutes
	public static long lastTrigger = -2 * CheckGap; //ms since boot, force first run
	public static Hashtable<String,Filter> Filters = new Hashtable<>();
	public static Bundle saveState = null;
	public static boolean mDataLoading = false;
	public static ScanSvc mInstance = null;

	Timer timer = null;

	//indicates how to behave if the service is killed
	int mStartMode = 0;

	//interface for clients that bind - not used
	IBinder mBinder = null;

	//indicates whether onRebind should be used - not used
	boolean mAllowRebind;

	//Called when the service is being created.
	@Override
	public void onCreate() //service created, only called once
	{
		Util.LI("************\nService onCreate"); //shows as separate entries
		startForeground(1, DoNotify("no stocks")); //keep service alive if GUI exits
	}

	//The service is starting, due to a call to startService()
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Util.LI("Service onStartCommand");
		mInstance = this;
		//mSvcStarted = true;
		dtLastCheck = new Date(); //now
		StartTimer(); //rerun stock filters
		MainActivity.mMainActivity.btnService.setText((ScanSvc.mInstance == null ? "Start" : "Stop") + " Service");
		MainActivity.mMainActivity.txtUpdateTime.setText("SERVICE " + (ScanSvc.mInstance == null ? "STOPPED" : "STARTED"));
		return START_STICKY; //service started\stopped manually
	}

	//A client is binding to the service with bindService() - not used
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	//Called when all clients have unbound with unbindService() - not used
	@Override
	public boolean onUnbind(Intent intent) {
		return mAllowRebind;
	}

	//Called when a client is binding to the service with bindService() - not used
	@Override
	public void onRebind(Intent intent) {}

	//Called when The service is no longer used and is being destroyed
	@Override
	public void onDestroy() {
		Util.LI("Service onDestroy");
		mInstance = null;
		//mSvcStarted = false;
		timer.cancel();
		MainActivity.mMainActivity.btnService.setText((ScanSvc.mInstance == null ? "Start" : "Stop") + " Service");
		MainActivity.mMainActivity.txtUpdateTime.setText("SERVICE " + (ScanSvc.mInstance == null ? "STOPPED" : "STARTED"));

		Util.LI("Service Stopped", true);
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll(); //remove notification
	}

	public void StartTimer() {
		//timer is inconsistent, sometimes 1 hour, sometimes constant loop, so must check time gap
		if (timer != null) //may be called by Refresh button, so restart timer
			timer.cancel(); //kill existing timer
		lastTrigger = -2 * CheckGap; //immediately do stock check
		TimerTask repeatedTask = new TimerTask() {
			public void run() {
				TimerHit();
			}
		};
		//name timer thread for logging, check for multiple timers
		timer = new Timer("Tmr:" + SystemClock.elapsedRealtime()); //milliseconds since boot

		long delay = 1*1000L; //milliseconds (1 second) first hit
		long period = 1*60*1000L; //1 minute
		timer.scheduleAtFixedRate(repeatedTask, delay, period);
	}

	private void TimerHit()
	{
		//Util.LI(Thread.currentThread().getName()); //timer name
		if (!firstRun && !mRefreshOnly) //always load data at svc start
		{
			if (!MarketOpen())
				return; //market closed
		}
		Date now = new Date();
		if (mRefreshOnly || SystemClock.elapsedRealtime() - lastTrigger  > CheckGap)
		{
			DoStockCheck(this);
			lastTrigger = SystemClock.elapsedRealtime();
		}
	}

	public boolean MarketOpen()
	{
		Calendar c = new GregorianCalendar(); //now
		if (c.get(Calendar.DAY_OF_WEEK) == android.icu.util.Calendar.SATURDAY ||
				c.get(Calendar.DAY_OF_WEEK) == android.icu.util.Calendar.SUNDAY ||
				(c.get(Calendar.HOUR_OF_DAY) == 9 && c.get(Calendar.MINUTE) < 30) ||
				c.get(Calendar.HOUR_OF_DAY) < 9 || c.get(Calendar.HOUR_OF_DAY) >= 16) //9:30am - 4pm
			return false; //closed
		return true; //market open
	}

	public boolean firstRun = true; //service just started
	public String strNote = ""; //notification alert

	public static void DoStockCheck(ScanSvc svc) //if called from main, svc = null
	{
		Util.LI("DoStockCheck");
		mDataLoading = true;
		try
		{
			/* //data service is free so run 24/7, but limit if paid service
			if (svc != null && !svc.firstRun) //called from svc, only run during market hours, assumes phone is EST
			{
				Calendar c = new GregorianCalendar(); //now
				if ((c.get(Calendar.HOUR_OF_DAY) == 9 && c.get(Calendar.MINUTE) < 30) ||
						c.get(Calendar.HOUR_OF_DAY) < 9 || c.get(Calendar.HOUR_OF_DAY) >= 16) //9:30am - 4pm
					return; //market closed
			}
			svc.firstRun = false; //if first started after hours
			*/
			dtLastCheck = new Date(); //now

			String strNoteNew = "";
			for (String k : Filters.keySet()) //each filter
			{
				Filter f = Filters.get(k);
				int cnt = f.RunFilter(); //get data from API, parse results, CANNOT be called from GUI thread
				for (TickerInfo ti: f.TickerList.values()) //each stock in result
					if (ti.PassedFilter)
						strNoteNew += ti.Symbol + " "; //add stock symbol to notification
				Util.LI("TickCnt " + k + " " + f.TickerList.size() +"|"+ cnt, true);
			}

			if (!mRefreshOnly && svc != null && (svc.firstRun || !strNoteNew.equals(svc.strNote))) //service running && notification changed
			{
				svc.strNote = strNoteNew;
				svc.DoNotify(svc.strNote); //show notification
			}

			//if (mRefreshOnly || svc.firstRun) //update displayed stock list
			//{
				MainActivity.mMainActivity.runOnUiThread(new Runnable() {
					public void run()
					{
						//update GUI stock list
						MainActivity.mMainActivity.ShowStockList(false);
					}
				});
			//}
			svc.firstRun = false;
		}
		catch(Exception ex)
		{
			Util.LE(ex.getMessage());
		}
		mDataLoading = false;
	}

	private Notification DoNotify(String msg) //update notification text and icon
	{
		Intent resultIntent = new Intent(this, MainActivity.class);

		PendingIntent resultPendingIntent =
				getActivity(
						this,
						0,
						resultIntent,
						PendingIntent.FLAG_UPDATE_CURRENT
				);
		strStockList = msg.trim();
		if (strStockList.isEmpty()) strStockList = "<No Alerts>"; //if no alerts
		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
						.setSmallIcon(R.drawable.biohazardpurp60)
						.setContentIntent(resultPendingIntent)
						.setContentTitle("Trend Scan")
						.setContentText(strStockList)
						.setVibrate(new long[] { 500, 500, 500 }); //vibrate if new notification
		Notification ntfcn = mBuilder.build();

		// Sets an ID for the notification
		int mNotificationId = 001;
		// Gets an instance of the NotificationManager service
		NotificationManager mNotifyMgr =
				(NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		ntfcn.flags |= Notification.FLAG_ONGOING_EVENT; //Cannot be cleared
		// Builds the notification and issues it.
		mNotifyMgr.notify(mNotificationId, ntfcn);
		return ntfcn;
	}
}
