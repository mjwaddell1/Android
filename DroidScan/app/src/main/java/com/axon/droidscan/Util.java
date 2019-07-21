package com.axon.droidscan;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

import static android.content.Context.ACTIVITY_SERVICE;

public class Util
{
	public static void SavePreference(String key, String value) //keep data between runs
	{
		SharedPreferences sharedPref = MainActivity.mMainActivity.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(key, value);
		editor.commit();
	}

	public static String GetPreference(String key, String defValue)
	{
		SharedPreferences sharedPref = MainActivity.mMainActivity.getPreferences(Context.MODE_PRIVATE);
		return sharedPref.getString(key, defValue);
	}

	public static void ShowAPICallLimit() //show Intrinio call amount (free account 500 call/day)
	{
		//{"access_code":"com_fin_data","current":502,"limit":500,"percent":100}
		StringBuilder sbData = new StringBuilder();
		String uri = "https://api.intrinio.com/usage/current?access_code=com_fin_data";
		GetWebData(sbData, uri, true, 0);
		Util.replaceAll(sbData, "\"", "");
		Util.replaceAll(sbData, "}", "");
		sbData.deleteCharAt(0);
		//access_code:com_fin_data,current:502,limit:500,percent:100
		String msg = "Calls ";
		for (String s : sbData.toString().split(","))
		{
			String[] ss = s.split(":");
			if (ss[0].equals("current"))
				msg += ss[1] + "/";
			if (ss[0].equals("limit"))
				msg += ss[1];
		}
		LI(msg, true);
	}

	public static void GetWebData(StringBuilder sbData, String uri, boolean usePwd, int startLine) //append to stringbuilder
	{
		BufferedReader reader = null;

		try
		{
			URL url = new URL(uri);
			URLConnection connection = url.openConnection();

			if (usePwd)
			{
				//Intrinio requires user\pwd
				String ss = Base64
						.encodeToString((SettingsActivity.UserName + ":" + SettingsActivity.Password)
								.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

				connection.addRequestProperty("Authorization", "Basic " + ss);
			}

			if (sbData == null) sbData = new StringBuilder();
			//send request
			InputStream is = connection.getInputStream();
			reader = new BufferedReader(new InputStreamReader(is));
			String line;
			int ctr = 0;
			//read web data, write to StringBuilder
			while ((line = reader.readLine()) != null)
			{
				if (ctr++ < startLine) continue; //all pages have headers, can skip header
				sbData.append(line);
				sbData.append("\n");
			}
			reader.close();
			reader = null;

		} catch (Exception ex)
		{
			LE(ex.getMessage(), false);
		}
	}

	public static void ShowToast(final String msg)
	{
		MainActivity.mMainActivity.runOnUiThread(new Runnable() {
			public void run() {
				Toast.makeText(MainActivity.mMainActivity, msg, Toast.LENGTH_LONG).show();
			}
		});
	}

	//like String.Replace
	public static void replaceAll(StringBuilder builder, String from, String to)
	{
		int index = builder.indexOf(from);
		while (index != -1)
		{
			builder.replace(index, index + from.length(), to);
			index += to.length(); // Move to the end of the replacement
			index = builder.indexOf(from, index);
		}
	}

	final static String LogTag = "DrScn";
	public static void LI(String msg)
	{
		LI(msg, false);
	} //info, default no toast
	public static void LE(String msg)
	{
		LE(msg, true);
	} //error, default toast

	public static void LI(String msg, boolean showToast)
	{
		Log.i(LogTag, msg); //info
		if (showToast)
			ShowToast(msg);
	}

	public static void LE(String msg, boolean showToast)
	{
		Log.e(LogTag, msg); //error
		if (showToast)
			ShowToast(msg);
	}

	public static StringBuilder GetCatLog(StringBuilder log, boolean reverse)
	{
		//load log
		Process logcat;
		//final StringBuilder log = new StringBuilder("-- LOG --\n");
		try
		{
			logcat = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-s", LogTag});
			BufferedReader br = new BufferedReader(new InputStreamReader(logcat.getInputStream()), 4 * 1024);
			String line;
			String separator = System.getProperty("line.separator");
			while ((line = br.readLine()) != null)
			{
				if (line.contains(LogTag)) //skip system logs, only show app logs
				{
					if (reverse) //last entry first
					{
						log.insert(0, separator);
						log.insert(0, line);
					}
					else //start with first log entry
					{
						log.append(line);
						log.append(separator);
					}
				}
			}
		} catch (Exception e)
		{
			LE(e.getMessage());
		}
		return log;
	}

	public static Hashtable<String, float[]> GetPriceHistory(final int dayCnt, final String... tkrs)
	{
		try
		{
			Util.LI("GetPriceHistory - " + Util.StringJoin(tkrs,","), true);
			Hashtable<String, float[]> out = new Hashtable<>();
			//assume first returned entry is latest\current price
			final StringBuilder[] tkrData = new StringBuilder[tkrs.length];
			//do all calls in separate threads
			Thread[] tkrCalls = new Thread[tkrs.length];
			for (int i = 0; i < tkrCalls.length; i++)
			{
				final int ctr = i;
				tkrCalls[i] = new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						//String url = "https://api.iextrading.com/1.0/stock/"+tkrs[ctr]+"/chart/1y?format=CSV"; //1 year of data, latest date at bottom
						//get (free) token from https://iextrading.com/developer/
						String url = "https://cloud.iexapis.com/stable/stock/"+tkrs[ctr]+"/chart/1y?format=CSV&token=pk_xxxx"; //1 year of data, latest date at bottom
						tkrData[ctr] = new StringBuilder();
						GetWebData(tkrData[ctr], url, false, 2); //skip header
					}
				});
				tkrCalls[i].start();
				try{Thread.sleep(100);}catch(InterruptedException ex){} //don't overload data service
			}
			//wait for all threads to finish
			for (int i = 0; i < tkrCalls.length; i++)
				try { tkrCalls[i].join(); } catch (InterruptedException ex) {}

			Util.LI("Got Price History", true);

			//parse and merge results
			for (int i = 0; i < tkrCalls.length; i++)
			{
				//timestamp,open,high,low,close,volume
				String webstr = tkrData[i].toString();
				if (!webstr.startsWith("20")) //if not 20##, then error
				{
					Util.LE(tkrs[i] + " - No Data");
					continue;
				}
				String[] lns = webstr.split("\n");
				float[] prclst = new float[Math.min(dayCnt+1, lns.length+1)]; //leave spot for last price
				for (int ln = 0; ln < prclst.length-1; ln++)
				{
					//feed data in reverse, latest price last
					String[] cols = lns[lns.length - ln - 1].split(",");
					prclst[ln+1] = Float.parseFloat(cols[4]); //date,open,high,low,close,volume,unadjustedVolume,change,changePercent,vwap,label,changeOverTime
				}
				prclst[0] = prclst[1]; //in case last price not available
				out.put(tkrs[i], prclst);
			}
			return GetLastPrice(out, tkrs); //add latest price to [0] slot
		} catch (Exception e)
		{
			LE(e.getMessage());
		}
		return null;
	}

	public static Hashtable<String, float[]> GetLastPrice(Hashtable<String, float[]> out, final String... tkrs)
	{
		try
		{
			Util.LI("GetLastPrice", false);
			String alltkrs = Util.StringJoin(tkrs,",");
			StringBuilder sb = new StringBuilder();
			//String uri = "https://api.iextrading.com/1.0/stock/market/batch?symbols="+alltkrs+"&types=quote&format=JSON";
			//String uri = "https://api.iextrading.com/1.0/tops/last?symbols="+alltkrs+"&format=csv";
			//delayed quote seems to have most accurate information
			//String uri = "https://api.iextrading.com/1.0/stock/market/batch?symbols="+alltkrs+"&types=delayed-quote&format=JSON";
			//get (free) token from https://iextrading.com/developer/
			String uri = "https://cloud.iexapis.com/stable/stock/market/batch?symbols="+alltkrs+"&types=delayed-quote&format=JSON&token=pk_xxxx";
			Util.GetWebData(sb, uri, false, 0);
			int cnt = out.size(); //ensure we get each symbol
			for (String tkr : tkrs)
			{
				//{"SMG":{"delayed-quote":{"symbol":"SMG","delayedPrice":91.21,"high":91.26, .....
				int pos = sb.indexOf("\""+tkr+"\",\"delayed");
				if (pos == -1) continue; //no symbol date
				int pos2 = sb.indexOf(":",pos);
				int pos3 = sb.indexOf(",",pos2);
				float[] prclst = out.get(tkr);
				prclst[0] = Float.parseFloat(sb.substring(pos2+1,pos3));
				cnt--;
			}
			if (cnt > 0)
				Util.LI("Last Price missed " + cnt);
		} catch (Exception e)
		{
			Util.LE(e.getMessage());
		}
		return out;
	}

	public static ActivityManager.MemoryInfo GetMemoryInfo(boolean log)
	{
		ActivityManager activityManager = (ActivityManager) MainActivity.mMainActivity.getSystemService(ACTIVITY_SERVICE);
		ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
		activityManager.getMemoryInfo(memoryInfo);
		if (log)
			LI("Mem Free: " + memoryInfo.availMem + "/" + memoryInfo.totalMem);
		return memoryInfo;
	}

	public static String StringJoin(String[] lst, String sep) //like C# string[].join
	{
		String out = "";
		for (int i = 0; i < lst.length; i++)
			out += (i > 0 ? sep : "") + lst[i];
		return out;
	}
}
