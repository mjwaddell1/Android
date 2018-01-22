package com.axon.trendscan;


import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

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

	public static void ClearPreferences()
	{
		SharedPreferences sharedPref = MainActivity.mMainActivity.getPreferences(Context.MODE_PRIVATE);
		sharedPref.edit().clear().commit();
	}

	public static void GetWebData(StringBuilder sbData, String uri, int startLine)
	{
		GetWebData(sbData, uri, startLine, 0);
	}

	public static void GetWebData(StringBuilder sbData, String uri, int startLine, int endLine) //append to stringbuilder
	{
		BufferedReader reader = null;

		try
		{
			URL url = new URL(uri);
			URLConnection connection = url.openConnection();

			//send request
			InputStream is = connection.getInputStream();
			reader = new BufferedReader(new InputStreamReader(is));
			String line;
			int ctr = 0;
			//read web data, write to StringBuilder
			while ((line = reader.readLine()) != null)
			{
				ctr++;
				if (ctr < startLine) continue; //some pages have headers, can skip header
				if (endLine > 0 && ctr > endLine) break;
				sbData.append(line);
				sbData.append("\n");
			}
			reader.close();
			reader = null;

		} catch (Exception ex)
		{
			ShowToast(ex.getMessage());
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

	public static String StringJoin(String[] lst, String sep) //like C# string[].join
	{
		String out = "";
		for (int i = 0; i < lst.length; i++)
			out += (i > 0 ? sep : "") + lst[i];
		return out;
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
}