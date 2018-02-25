package com.axon.droidscan;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Hashtable;

public class SettingsActivity extends Activity
{

	//defaults
	//For data feed, register (free) at https://intrinio.com/signup
	//free plan allows 500 calls for day
	static String UserName = "Your User Name";
	static String Password = "Your Password";
	static String ChartURL = "https://finviz.com/quote.ashx?t={SYMBOL}&ty=c&ta=0&p=d";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		try
		{
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_settings);
			//load settings from preferences
			((EditText) findViewById(R.id.txtSettings)).setText(Util.GetPreference("settings", null));
			((EditText) findViewById(R.id.txtUserName)).setText(Util.GetPreference("UserName", UserName));
			((EditText) findViewById(R.id.txtPassword)).setText(Util.GetPreference("Password", Password));
			((EditText) findViewById(R.id.txtChartURL)).setText(Util.GetPreference("ChartURL", ChartURL));

			((Button) findViewById(R.id.btnSave)).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view) //save settings
				{
					Util.SavePreference("settings",
							((EditText) findViewById(R.id.txtSettings)).getText().toString());
					ParseSettings(); //parse filter items

					//get text from GUI
					UserName = ((EditText) findViewById(R.id.txtUserName)).getText().toString();
					Password = ((EditText) findViewById(R.id.txtPassword)).getText().toString();
					ChartURL = ((EditText) findViewById(R.id.txtChartURL)).getText().toString();

					//write to storage
					Util.SavePreference("UserName", UserName);
					Util.SavePreference("Password", Password);
					Util.SavePreference("ChartURL", ChartURL);

					finish(); //close settings
				}
			});

			((ImageButton) findViewById(R.id.btnLog)).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					if (((TextView) findViewById(R.id.txtLog)).getVisibility() == View.GONE)
					{
						//set log visible, hide save\cancel\title, leave cat button only
						((ImageButton) findViewById(R.id.btnLog)).setImageResource(R.drawable.cathead20red);
						((View) findViewById(R.id.txtLog)).setVisibility(View.VISIBLE);
						findViewById(R.id.btnSave).setVisibility(View.GONE);
						findViewById(R.id.btnCancel).setVisibility(View.GONE);
						findViewById(R.id.btnShowFilters).setVisibility(View.GONE);
						findViewById(R.id.txtSettingTitle).setVisibility(View.GONE);
						findViewById(R.id.txtSettings).setVisibility(View.GONE);
						findViewById(R.id.grdKeyChart).setVisibility(View.GONE);
						((TextView) findViewById(R.id.txtLog)).setText("--- Log Cat ---\n" + Util.GetCatLog(new StringBuilder(), true).toString());
					} else //show settings buttons
					{
						((ImageButton) findViewById(R.id.btnLog)).setImageResource(R.drawable.cathead20);
						((View) findViewById(R.id.txtLog)).setVisibility(View.GONE);
						findViewById(R.id.btnSave).setVisibility(View.VISIBLE);
						findViewById(R.id.btnCancel).setVisibility(View.VISIBLE);
						findViewById(R.id.btnShowFilters).setVisibility(View.VISIBLE);
						findViewById(R.id.txtSettingTitle).setVisibility(View.VISIBLE);
						findViewById(R.id.txtSettings).setVisibility(View.VISIBLE);
						findViewById(R.id.grdKeyChart).setVisibility(View.VISIBLE);
					}
				}
			});

			((Button) findViewById(R.id.btnCancel)).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					finish(); //don't save, can also use back button
				}
			});

			((Button) findViewById(R.id.btnShowFilters)).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view) //open Intrinio web site, screener tag list
				{
					String url = "http://docs.intrinio.com/tags/intrinio-public#screener";
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(browserIntent);
				}
			});
		}
		catch(Exception ex)
		{
			Util.LE(ex.getMessage());
		}
	}

	public static void LoadSettings() //load from preferences
	{
		if (Util.GetPreference("settings", "").isEmpty())
			SetDefaultSettings(); //hard coded for first run
		ParseSettings();
		//user\pwd\chart url
		UserName = Util.GetPreference("UserName", UserName);
		Password = Util.GetPreference("Password", Password);
		ChartURL = Util.GetPreference("ChartURL", ChartURL);
	}

	public static int ParseSettings()
	{
		try
		{
			String rootURL = "https://api.intrinio.com/securities/search?conditions=";
			String ps = Util.GetPreference("settings", null); //filter items
			if (ps == null) return 0; //no settings stored
			String fname = "";
			String[] ss = ps.split("\n");
			ScanSvc.Filters.clear();
			for (String s : ss) //each filter item - max 10 items per filter (Intrinio)
			{
				s = s.trim();
				if (s.isEmpty() || s.charAt(0) == '*' || s.charAt(0) == '-')
					continue; //comment\blank line, ignore
				if (!s.contains(":")) //only named filters have colon
					fname = "~";  //default unnamed filter
				else //named filter
				{
					ss = s.split(":");
					fname = ss[0];
					s = ss[1];
				}
				if (!ScanSvc.Filters.containsKey(fname))
					ScanSvc.Filters.put(fname, new Filter(fname, rootURL)); //new filter
				if (s.startsWith("#")) //button color or interval
				{
					String[] kv = s.substring(1).split("="); //skip #, split at =
					if (kv[0].equals("BTN"))
						ScanSvc.Filters.get(fname).ButtonColor = Color.parseColor("#"+kv[1]); //#004400
					if (kv[0].equals("INT"))
						ScanSvc.Filters.get(fname).CheckInterval = Long.parseLong(kv[1])*60*1000L; //minutes to milliseconds
					continue;
				}
				//convert logic to Intrinio syntax
				ScanSvc.Filters.get(fname).FilterURL += s
						.replace(">=", "~gte~")
						.replace("<=", "~lte~")
						.replace(">", "~gt~")
						.replace("<", "~lt~")
						.trim() + ",";
			}
			for (String s : ScanSvc.Filters.keySet())
			{
				ScanSvc.Filters.get(s).FilterURL = ScanSvc.Filters.get(s).FilterURL.substring(0, ScanSvc.Filters.get(s).FilterURL.length() - 1); //remove last comma
				//https://api.intrinio.com/securities/search?conditions=bookvaluepershare~gt~25,currentratio~lt~1,debttoequity~lt~1 .....  (see Filter.java)
			}
			return ScanSvc.Filters.size();
		}
		catch(Exception ex)
		{
			Util.LE(ex.getMessage());
		}
		return 0; //error
	}

	private static void SetDefaultSettings() //in app, set settings to blank to get default settings
	{
		//max 10 items per filter else Intrinio error
		//Intrinio fuckers, every filter item is an API call, max 500 (free) per day
		StringBuilder sbSettings = new StringBuilder();
		sbSettings.append("----- max 10 items per filter ----\n");
		sbSettings.append("*****quarterly\n");
		sbSettings.append("--marketcap>1000000000\n");
		sbSettings.append("bookvaluepershare>25\n");
		sbSettings.append("currentratio<1\n");
		sbSettings.append("debttoequity<1\n");
		sbSettings.append("ltdebttoequity<1\n");
		sbSettings.append("--basiceps>10\n");
		sbSettings.append("--roa>0.2\n");
		sbSettings.append("--roe>0.2\n");
		sbSettings.append("--roic>0.2\n");
		sbSettings.append("profitmargin>0.10\n");
		sbSettings.append("profitmargin<1\n");
		sbSettings.append("*****daily - every 10 minutes 9:30-4\n");
		sbSettings.append("dividendyield>0.01\n");
		sbSettings.append("--pricetoearnings>0.10\n");
		sbSettings.append("--pricetobook<3\n");
		sbSettings.append("percent_change<100\n");
		sbSettings.append("#BTN=008800\n"); //button color
		sbSettings.append("#INT=30\n"); //check interval

		sbSettings.append("DN10:percent_change<-0.10\n");
		sbSettings.append("DN10:profitmargin>0.0\n");
		sbSettings.append("DN10:profitmargin<1\n");
		sbSettings.append("DN10:#BTN=AA0000\n"); //button color
		sbSettings.append("DN10:#INT=30\n"); //check interval

		Util.SavePreference("settings", sbSettings.toString().trim()); //save for app restart
	}
}


