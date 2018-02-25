package com.axon.trendscan;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Bundle;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.telephony.IccOpenLogicalChannelResponse;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

public class SettingsActivity extends Activity
{

	//defaults
	//if user clicks chart image, open site, {SYMBOL} is replaced with actual symbol
	static String ChartURL = "https://finviz.com/quote.ashx?t={SYMBOL}&ty=c&ta=0&p=d";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		//load settings from preferences
		((EditText)findViewById(R.id.txtSettings)).setText(Util.GetPreference("settings", null));
		((EditText)findViewById(R.id.txtChartURL)).setText(Util.GetPreference("ChartURL", ChartURL));

		((Button) findViewById(R.id.btnSave)).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view) //save settings
			{

				Util.ClearPreferences(); //clear all settings

				Util.SavePreference("settings",
						((EditText)findViewById(R.id.txtSettings)).getText().toString());
				ParseSettings(); //parse filter items

				//get text from GUI
				ChartURL = ((EditText)findViewById(R.id.txtChartURL)).getText().toString();

				//write to storage
				Util.SavePreference("ChartURL", ChartURL);

				finish(); //close settings
			}
		});

		((ImageButton) findViewById(R.id.btnLog)).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				if (((TextView)findViewById(R.id.txtLog)).getVisibility() == View.GONE)
				{
					//set log visible, hide save\cancel\title, leave cat button only
					((ImageButton) findViewById(R.id.btnLog)).setImageResource(R.drawable.cathead20red);
					((View)findViewById(R.id.txtLog)).setVisibility(View.VISIBLE);
					findViewById(R.id.btnSave).setVisibility(View.GONE);
					findViewById(R.id.btnCancel).setVisibility(View.GONE);
					findViewById(R.id.txtSettingTitle).setVisibility(View.GONE);
					findViewById(R.id.txtSettings).setVisibility(View.GONE);
					findViewById(R.id.grdKeyChart).setVisibility(View.GONE);
					((TextView)findViewById(R.id.txtLog)).setText("--- Log Cat ---\n" + Util.GetCatLog(new StringBuilder(), true).toString());
				}
				else //show settings buttons
				{
					((ImageButton) findViewById(R.id.btnLog)).setImageResource(R.drawable.cathead20);
					((View)findViewById(R.id.txtLog)).setVisibility(View.GONE);
					findViewById(R.id.btnSave).setVisibility(View.VISIBLE);
					findViewById(R.id.btnCancel).setVisibility(View.VISIBLE);
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
				finish(); //cancel save, close window, can also use back button
			}
		});

		SetBoxWidths(); //API Key and Chart URL
	}

	public void SetBoxWidths() //android bug, API Key and URL boxes stretch off screen, must manually set width
	{
		Point sz = new Point();
		getWindowManager().getDefaultDisplay().getSize(sz); //screen dimensions
		//assume portrait
		int screenWidth = sz.x;
		int screenHeight = sz.y;
		((EditText)findViewById(R.id.txtChartURL)).setWidth(screenWidth - ((TextView)findViewById(R.id.txtChartURLX)).getWidth()-150);
	}

	public static void LoadSettings() //load from preferences
	{
		if (Util.GetPreference("settings", "").isEmpty())
		{
			SetDefaultSettings(); //hard coded for first run
		}
		ParseSettings(); //parse settings text box

		ChartURL = Util.GetPreference("ChartURL", ChartURL); //if user clicks chart
	}

	public static int ParseSettings()
	{
		Util.LI("ParseSettings");
		String ps = Util.GetPreference("settings", null); //filter items
		if (ps == null) return 0; //no settings stored
		String fname = "";
		String[] ss = ps.split("\n"); //each line
		ScanSvc.Filters.clear();
		for (String s : ss) //each setting
		{
			try
			{
				s = s.trim();
				if (s.isEmpty() || s.charAt(0) == '*' || s.charAt(0) == '-')
					continue; //comment\blank line, ignore
				if (!s.contains(":")) //only named filters have colon - DN20:stk=AMZN
					fname = "~";  //default unnamed filter
				else //named filter
				{
					ss = s.split(":"); //FNAME:key=value
					fname = ss[0];
					s = ss[1]; //key=value
				}
				if (!ScanSvc.Filters.containsKey(fname)) //new filter name
				{
					Filter f = new Filter(fname); //new filter
					ScanSvc.Filters.put(fname, f);
					//prevent errors, set defaults for new filter in case setting is missing
					f.setTickerList("AAPL,GOOG,AMZN");
					f.ButtonColorAlert = Color.CYAN;
					f.ButtonColorNorm = Color.GRAY;
					f.PctChange = -5;
					f.DaySpan = 1;
					f.ShowAllStocks = true;
					f.ShowSpans = new int[]{1, 5, 10};
				}
				ss = s.split("=");
				if (ss[0].equals("stk") || ss[0].equals("stks"))
					ScanSvc.Filters.get(fname).setTickerList(ss[1]);
				else if (ss[0].equals("alertclr"))
					ScanSvc.Filters.get(fname).ButtonColorAlert = Color.parseColor(ss[1]); //#004400
				else if (ss[0].equals("normclr"))
					ScanSvc.Filters.get(fname).ButtonColorNorm = Color.parseColor(ss[1]); //#004400
				else if (ss[0].equals("pctchange"))
					ScanSvc.Filters.get(fname).PctChange = Integer.parseInt(ss[1]);
				else if (ss[0].equals("dayspan"))
					ScanSvc.Filters.get(fname).DaySpan = Integer.parseInt(ss[1]);
				else if (ss[0].equals("showallstk") || ss[0].equals("showallstks"))
					ScanSvc.Filters.get(fname).ShowAllStocks = Boolean.parseBoolean(ss[1]);
				else if (ss[0].equals("showspan") || ss[0].equals("showspans"))
				{
					String[] sx = ss[1].split(",");
					int[] ii = new int[sx.length];
					for (int i = 0; i < sx.length; i++)
						ii[i] = Integer.parseInt(sx[i]);
					ScanSvc.Filters.get(fname).ShowSpans = ii;
				} else
					Util.LE("Unknown Setting: " + ss[0]);
			}
			catch (Exception ex)
			{
				Util.LE("Parse Error:\n" + s + "\n" + ex.getMessage());
			}
		}
		return ScanSvc.Filters.size();
	}

	private static String SetDefaultSettings() //in app, set settings to blank to get default settings
	{
		Util.LI("SetDefaultSettings");
		StringBuilder sbSettings = new StringBuilder();
		sbSettings.append("stk=TNA,BRZU,LABU,TECL,NAIL,DRN,DFEN,UBIO,JPNL,LBJ,UTSL,PILL,VIX,UGAZ,UWTI\n"); //must specify specific watch list
		sbSettings.append("pctchange=-10\n"); //filter on pct change
		sbSettings.append("dayspan=5\n"); //across previous X days
		sbSettings.append("showspan=1,5,20\n"); //display on main gui button
		sbSettings.append("showallstk=true\n"); //show stocks even if not passed filter
		sbSettings.append("alertclr=#008800\n"); //button color if passed filter
		sbSettings.append("normclr=#00CCCC\n"); //button color not passed filter

		//can have multiple filters, must have name, these rows commented out
		sbSettings.append("--DN20:stk=UDOW,SPY\n");
		sbSettings.append("--DN20:pctchange=-20\n");
		sbSettings.append("--DN20:dayspan=5\n");
		sbSettings.append("--DN20:showspan=1,5,20\n");
		sbSettings.append("--DN20:alertclr=#AA0000\n");
		sbSettings.append("--DN20:normclr=#999999\n");

		Util.SavePreference("settings", sbSettings.toString().trim()); //save for app restart
		return sbSettings.toString().trim();
	}
}


