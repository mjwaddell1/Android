package com.axon.droidscan;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.*;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Base64;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Pattern;

import static android.app.PendingIntent.getActivity;


public class ScanSvc extends Service {

	//public static boolean mSvcStarted = false;
	public static boolean mRefreshOnly = false; //run service only to show stock list, then end
	public static String strStockList = "<not set>"; //for notification
	public static Date dtLastCheck = new Date(); //shown on GUI
	//public static long lastTrigger = -99999; //ms since boot, force first run
	public static Hashtable<String, String> CompanyList = new Hashtable<>(); //retrieved from prefs
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
	public void onCreate() //service started
	{
		Util.LI("************\nService onCreate"); //shows as separate entries
		startForeground(1, DoNotify("no stocks")); //keep service alive if GUI exits
	}

	//The service is starting, due to a call to startService()
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		mInstance = this;
		Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();
		StartTimer(); //rerun stock filters
		MainActivity.mMainActivity.btnService.setText((ScanSvc.mInstance == null ? "Start" : "Stop") + " Service");
		MainActivity.mMainActivity.txtUpdateTime.setText("SERVICE " + (ScanSvc.mInstance == null ? "STOPPED" : "STARTED"));
		//mMainActivity.mnuService.setTitle((ScanSvc.mSvcStarted ? "Stop" : "Start") + " Service");
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
		mInstance = null;
		timer.cancel();
		MainActivity.mMainActivity.btnService.setText((ScanSvc.mInstance == null ? "Start" : "Stop") + " Service");
		MainActivity.mMainActivity.txtUpdateTime.setText("SERVICE " + (ScanSvc.mInstance == null ? "STOPPED" : "STARTED"));

		Util.LI("Service Stopped");
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll(); //remove notification
	}

	public void StartTimer() {
		firstRun = true; //refresh button restarts timer
		//timer is inconsistent, sometimes 1 hour, sometimes constant loop, so must check time gap
		if (timer != null) //may be called by Refresh button, so restart timer
			timer.cancel(); //kill existing timer
		for (Filter f : Filters.values())
			f.LastCheck = -2 * f.CheckInterval; //force data check at start
		TimerTask repeatedTask = new TimerTask() {
			public void run() {
				TimerHit();
			}
		};
		//name timer thread for logging, check for multiple timers
		timer = new Timer("Tmr:" + SystemClock.elapsedRealtime()); //milliseconds since boot

		long delay = 1*1000L; //milliseconds (1 second) first hit
		long period = 60*1000L; //1 minute
		timer.scheduleAtFixedRate(repeatedTask, delay, period);
	}

	public boolean firstRun = true; //service just started

	private void TimerHit()
	{
		//Util.LI(Thread.currentThread().getName()); //timer name

		if (!firstRun && !mRefreshOnly) //always load data at svc start
		{
			if (!MarketOpen())
				return; //market closed
		}

		if (mRefreshOnly) //user clicked refresh
			DoStockCheck(this);
		else
		{
			boolean DoCheck = false;
			for (Filter f : Filters.values())
				DoCheck = DoCheck || f.PassedCheckInterval();
			if (DoCheck)
				DoStockCheck(this); //at least 1 filter updated
		}
		firstRun = false;
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

	public String strNote = ""; //notification alert

	//send request to Intrinio, parse results
	public static void DoStockCheck(ScanSvc svc) //if called from main, svc = null
	{
		try
		{
			Util.LI("DoStockCheck");
			mDataLoading = true;
			dtLastCheck = new Date(); //now

			if (CompanyList.size() == 0)
				GetCompanyList(false); //reload from prefs or from Intrinio

			//get index constituents
			//day drop > 5%
			/*
			-- Qtr

			marketcap										Market Cap > 1b
			bookvaluepershare								Book/sh > 25
			cashandequivalents/weightedavebasicsharesos		Cash/sh > 10 ???
			currentratio									Current Ratio  < 1
			debttoequity									Debt/Eq < 1
			ltdebttoequity									LT Debt/Eq < 1
			basiceps										EPS > 10
			roa												ROA > .2
			roe												ROE > .2
			roic											ROI > .2
			profitmargin									Profit Margin  > .1 (10%)  < 1

			-- Daily
			dividendyield									Dividend % > .01
			paymentofdividends								Dividend  ??????
			pricetoearnings									P/E < 10
			pricetobook										P/B < 3

			Forward P/E
				PEG
			G = Growth of EPS from previous year
				PEG = (P/E) / G  < 1
*/

			/*
			//max filter count is 10

			StringBuilder sbURL = new StringBuilder("https://api.intrinio.com/securities/search?conditions=");
			//qtr - check once per day - midnight
			///////sbURL.append("marketcap~gt~1000000000,");
			sbURL.append("bookvaluepershare~gt~25,");
			sbURL.append("currentratio~lt~1,");
			sbURL.append("debttoequity~lt~1,");
			sbURL.append("ltdebttoequity~lt~1,");
			////sbURL.append("basiceps~gt~10,");
			//////sbURL.append("roa~gt~0.2,");
			///////sbURL.append("roa~gt~0.2,");
			///////sbURL.append("roic~gt~0.2,");
			sbURL.append("profitmargin~gt~0.10,");
			sbURL.append("profitmargin~lt~1,"); //bad data
			//daily - every 10 minutes 9:30-4
			sbURL.append("dividendyield~gt~0.01,");
			///////sbURL.append("pricetoearnings~lt~0.10,");
			///////sbURL.append("pricetobook~lt~3,");
			sbURL.append("percent_change~lt~100,"); //bad data

			sbURL.deleteCharAt(sbURL.length()-1); //remove last comma
			*/

			String strNoteNew = "";
			for (String k : Filters.keySet()) //each filter
			{
				Filter f = Filters.get(k);
				int cnt = f.RunFilter(); //get data from Intrinio, parse results, CANNOT be called from GUI thread
				for (TickerInfo ti: f.TickerList) //each stock in result
					strNoteNew += ti.Ticker + " "; //stock symbol
				Util.LI("TickCnt " + k + " " + cnt, true);
			}

			if (!mRefreshOnly && svc != null && (svc.firstRun || !strNoteNew.equals(svc.strNote))) //service running && notification changed
			{
				svc.strNote = strNoteNew;
				svc.DoNotify(svc.strNote); //show notification
			}
			Util.ShowAPICallLimit(); //max 500 calls per day (free acct)
			//if (mRefreshOnly) //service not already running, start svc, update GUI, stop svc
			//{
				MainActivity.mMainActivity.runOnUiThread(new Runnable() {
					public void run()
					{
						//update GUI stock list
						MainActivity.mMainActivity.ShowStockList(false);
					}
				});
			//}
		}
		catch(Exception ex)
		{
			Util.LE(ex.getMessage());
		}
		mDataLoading = false;
	}

	public static void GetCompanyList(boolean restoreGUI) //U.S. public companies
	{
		try
		{
			Util.LI("GetCompanyList");
			StringBuilder sb1 = null, sb2 = null;
			if (CompanyList.size() > 0) //already have list
			{
				Util.LI("GetCompanyList PreDone [" + CompanyList.size() + "]", true);
				return;
			}
			if (Util.GetPreference("CompanyList", "").isEmpty()) //get list from Intrinio
			{
				if (!restoreGUI) //called from GUI, can't load from web
				{
					// 2 pages - need 2 calls to Intrinio
					String uri = "https://api.intrinio.com/companies.csv?page_number="; //1,2
					sb1 = new StringBuilder();
					sb2 = new StringBuilder();
					Util.GetWebData(sb1, uri + "1", true, 1); //page 1
					Thread.sleep(1000); //else will skip second call
					Util.GetWebData(sb2, uri + "2", true, 2); //page 2, append to stringbuilder
					if (sb2.length() == 0) //be sure 2nd call happened
						Util.LI("Cpy Page 2 = 0", true);
					sb1.append(sb2);
					Util.SavePreference("CompanyList", sb1.toString()); //store for next refresh
				}
			}
			else // get list from preferences, note never refreshed
			{
				Util.LI("Using Pref Cache List", true);
				sb1 = new StringBuilder(Util.GetPreference("CompanyList", null));
			}

			if (sb1 == null)
			{
				Util.LI("Missing Company List", true);
				return;
			}

			//parse company list
			String tkr, cmp;
			int i = 0, i2 = 0;
			int ctr = 0;
			while (i > -1)
			{
				i = sb1.indexOf("\n", i);
				i2 = sb1.indexOf(",", i); //file ends with \n
				if (i2 == -1) break; //empty symbol
				tkr = sb1.substring(i + 1, i2);
				i = sb1.indexOf(",", i2 + 1);
				if (i == -1) break; //empty symbol
				cmp = sb1.substring(i2 + 1, i).trim();
				i = sb1.indexOf("\n", i);
				CompanyList.put(tkr, cmp);
				ctr++;
			}
			Util.LI("GetCompanyList Done [" + ctr + "]", true);
		}
		catch (Exception ex)
		{
			Util.LE(ex.getMessage());
		}
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
						.setSmallIcon(R.drawable.biohazardgreen60)
						.setContentIntent(resultPendingIntent)
						.setContentTitle("Droid Scan")
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
