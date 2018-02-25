package com.axon.droidscan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.view.*;
import android.widget.*;
import android.widget.LinearLayout.*;
import java.text.SimpleDateFormat;

import java.util.Arrays;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;

public class MainActivity extends Activity
{

	public static MainActivity mMainActivity;
	TextView txtUpdateTime = null;
	Button btnService = null;
	Button btnRefresh = null;
	MenuItem mnuService = null;
	SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		try
		{
			super.onCreate(savedInstanceState);
			if (savedInstanceState == null && ScanSvc.saveState != null)
				savedInstanceState = ScanSvc.saveState; //use state from service

			setContentView(R.layout.activity_main);

			mMainActivity = this; //referenced by other classes

			txtUpdateTime = (TextView) findViewById(R.id.txtUpdateTime);

			btnService = (Button) findViewById(R.id.btnService);
			btnService.setText((ScanSvc.mInstance == null ? "Start" : "Stop") + " Service"); //upper case on button
			btnService.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					//start service
					Intent svc = new Intent(getBaseContext(), ScanSvc.class);
					if (ScanSvc.mInstance == null) //if not started
					{
						((Button) findViewById(R.id.btnRefresh)).setEnabled(false);
						((Button) findViewById(R.id.btnService)).setEnabled(false);
						getApplicationContext().startService(svc); //start service
					} else //already started, so attempt stop
						//stopService returns true if stopped successfully
						getApplicationContext().stopService(svc);
				}
			});

			//Refresh button refreshes stock list on GUI. Will start\stop service if needed
			btnRefresh = (Button) findViewById(R.id.btnRefresh);
			btnRefresh.setOnClickListener(new View.OnClickListener() //Refresh button
			{
				@Override
				public void onClick(View view)
				{
					Util.LI("Refresh Click");
					//data load must run in background thread, so restart timer
					if (ScanSvc.mInstance != null) //service running
					{
						if (ScanSvc.mDataLoading)
							Util.ShowToast("Data loading. Please wait.");
						else
							ScanSvc.mInstance.StartTimer(); //restart timer
					} else
						ShowStockList(false); //update shown list from service, does not run filter if svc running
				}
			});

			//separate activity for settings
			((Button) findViewById(R.id.btnSettings)).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					Intent ss = new Intent(MainActivity.this, SettingsActivity.class);
					startActivity(ss);
				}
			});

			SettingsActivity.LoadSettings(); //load from pref storage

			//use savedInstanceState so gui is not cleared on rotate\restore
			if (savedInstanceState != null && !savedInstanceState.isEmpty()) //user rotated app or switched back to app
			{
				DeserializeFilters(savedInstanceState); //recreate filters
				ScanSvc.GetCompanyList(true); //retrieve from prefs
				ShowStockList(true); //parse saved data
			}
		}
		catch (Exception ex)
		{
			Util.LE(ex.getMessage());
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) //user switching apps or rotating
	{
		try
		{
			super.onSaveInstanceState(outState);
			SerializeFilters(outState); //save filter data
			ScanSvc.saveState = outState; //Android not storing state correctly so store in service
		}
		catch (Exception ex)
		{
			Util.LE("Act.onCreate:" + ex.getMessage());
		}
	}

	public void SerializeFilters(Bundle outState) //save filter data
	{
		String[] filters = new String[ScanSvc.Filters.size()];
		int ctr = 0;
		for (Filter f : ScanSvc.Filters.values())
		{
			filters[ctr++] = f.FilterName;
			outState.putBundle(f.FilterName,f.ToBundle()); //bundle of bundles
		}
		outState.putStringArray("FilterNames", filters);

	}

	public void DeserializeFilters(Bundle inState) //restore filter data
	{
		if (!inState.containsKey("FilterNames"))
			return; //no saved data
		ScanSvc.Filters.clear();
		String[] fnames = inState.getStringArray("FilterNames");
		for (String k : fnames) //rebuild each filter
		{
			Filter f = new Filter(inState.getBundle(k));
			ScanSvc.Filters.put(k, f); //add to filter collection
		}
	}

	@Override
	protected void onPause() //this app hidden, save state, - not used
	{
		super.onPause();
	}

	public void ShowStockList(boolean restoreGUI) //restoreGUI = true if user rotated app, do not start svc
	{
		((Button) findViewById(R.id.btnRefresh)).setEnabled(false);
		((Button) findViewById(R.id.btnService)).setEnabled(false);
		//if service not running, start svc, show list, stop svc
		if (!restoreGUI && ScanSvc.mInstance == null && !ScanSvc.mRefreshOnly) //run filters without service, this method called twice
		{
			ScanSvc.mRefreshOnly = true; //stop service after refresh
			getApplicationContext().startService(new Intent(getBaseContext(), ScanSvc.class)); //network error if run from UI thread
			//service will re-call this method
			return;
		}

		LinearLayout linStock = (LinearLayout) findViewById(R.id.linStockList);
		linStock.removeAllViews();
		linStock.setDividerPadding(0);

		int cnt = ParseStockData(linStock);
		TextView ns = (TextView) findViewById(R.id.txtNoStocks);
		ns.setVisibility(cnt == 0 ? View.VISIBLE : View.GONE);
		txtUpdateTime.setText("Last Check: " + sdfDate.format(ScanSvc.dtLastCheck) + " [" + cnt + "]");
		if (!restoreGUI && ScanSvc.mRefreshOnly) //stop service
		{
			ScanSvc.mRefreshOnly = false;
			getApplicationContext().stopService(new Intent(getBaseContext(), ScanSvc.class));
		}
		((Button) findViewById(R.id.btnRefresh)).setEnabled(true);
		((Button) findViewById(R.id.btnService)).setEnabled(true);
	}

	private int ParseStockData(LinearLayout linStock) //create GUI button for each stock
	{
		//https://api.intrinio.com/usage/current?access_code=com_fin_data
		try
		{
			LayoutParams lpMain = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 45);
			LayoutParams lpPop = new LinearLayout.LayoutParams(100, 60);
			lpMain.setMargins(0, 0, 0, 2);
			lpPop.setMargins(10, 10, 0, 2);
			Point sz = new Point();
			getWindowManager().getDefaultDisplay().getSize(sz); //screen dimensions
			int screenWidth = Math.min(sz.x, sz.y);
			int screenHeight = Math.max(sz.x, sz.y);
			int buttonHeight = screenHeight/30;
			LayoutParams lpChart = new LinearLayout.LayoutParams(screenWidth/2, screenWidth/2 * 2 / 3); //chart button
			lpChart.setMargins(10, 10, 10, 10);

			int cnt=0;
			//sort filters by name, ~ is default (unnamed) filter
			Object[] keys = ScanSvc.Filters.keySet().toArray();
			Arrays.sort(keys);
			for (Object k : keys) //each filter
			{
				Filter f = ScanSvc.Filters.get(k);
				if (f.TickerList.size() == 0) //no results for this filter
					continue;
				//String[] stStocks = ScanSvc.sbData.toString().split(";");
				//Arrays.sort(stStocks);
				for (final TickerInfo ti : f.TickerList)
				{
					if (ti.Ticker.contains("$") || ti.Ticker.contains("."))
						continue; //dup stock, $IBM IBM.A
					//create main button with symbol name
					Button btn = new Button(this);
					btn.setLayoutParams(lpMain);
					btn.setPadding(15, 0, 0, 0);
					btn.setAllCaps(false);
					if (f.FilterName.equals("~")) //default filter
						btn.setText(ti.Ticker + " : " + (ti.CompanyName == null ? "" : ti.CompanyName));
					else
						btn.setText(f.FilterName + " : " + ti.Ticker + " : " + (ti.CompanyName == null ? "" : ti.CompanyName));
					btn.setBackgroundColor(f.ButtonColor); //(Color.rgb(0, 175, 0));
					btn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
					final String tt = ti.Ticker;
					linStock.addView(btn); //symbol button

					//linear layout for chart icon and stock name
					final LinearLayout linPop = new LinearLayout(this);
					linPop.setOrientation(LinearLayout.HORIZONTAL);
					linPop.setGravity(Gravity.CENTER_VERTICAL);

					//chart button
					Button btnChart = new Button(this);
					btnChart.setLayoutParams(lpChart); //set height/width
					btnChart.setMinimumWidth(0);
					btnChart.setAllCaps(false);
					//chart image uses actual price history
					btnChart.setBackground(new BitmapDrawable(this.getResources(),ti.GetTickerChart()));
					btnChart.setGravity(Gravity.LEFT);
					linPop.addView(btnChart);

/*
					//chart button
					Button btnPop = new Button(this);
					btnPop.setLayoutParams(lpPop);
					btnPop.setPadding(0, 0, 0, 0);
					btnPop.setMinimumWidth(0);
					btnPop.setAllCaps(false);
					btnPop.setBackgroundResource(R.drawable.chart);
					btnPop.setGravity(Gravity.LEFT);
					linPop.addView(btnPop);
*/
					//stock name
					TextView txt = new TextView(this);
					txt.setText(ti.CompanyName == null ? "" : ti.CompanyName);
					txt.setGravity(Gravity.CENTER_VERTICAL);
					txt.setPadding(10, 5, 0, 0);
					linPop.addView(txt);
					linStock.addView(linPop);
					linPop.setVisibility(View.GONE);

					//grid for stock data
					final GridLayout grd = new GridLayout(this);
					grd.setColumnCount(2);
					grd.setRowCount(ti.TickerData.size() + 5);
					grd.setPadding(10,0,0,4);

					//if user clicks chart, open web site with symbol
					btnChart.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View view)
						{
							Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SettingsActivity.ChartURL.replace("{SYMBOL}", ti.Ticker)));
							startActivity(browserIntent);
						}

						;
					});

					int rw = 0;
					//put last price as first row
					AddLayoutText(grd, "price    ", rw, 0);
					AddLayoutText(grd, ""+ti.LastPrice, rw, 1);
					rw++;
					//Intrinio returns each filter item (EPS, ProfitMargin,...), use for list
					for (String kk : ti.TickerData.keySet()) {
						AddLayoutText(grd, kk + "    ", rw, 0);
						AddLayoutText(grd, ti.TickerData.get(kk).toString(), rw, 1);
						rw++;
					}
					linStock.addView(grd);
					grd.setVisibility(View.GONE);

					//show\hide data grid
					btn.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View view)
						{
							grd.setVisibility(grd.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
							linPop.setVisibility(linPop.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
						}

						;
					});
					cnt++;
				}
			}
			return cnt;
		} catch (Exception ex)
		{
			Util.LE(ex.getMessage());
			return -1;
		}
	}

	public void AddLayoutChild(GridLayout grd, View vChild, int row, int col)
	{
		grd.addView(vChild, new GridLayout.LayoutParams(
				GridLayout.spec(row, GridLayout.LEFT),
				GridLayout.spec(col, GridLayout.LEFT)));
	}

	public void AddLayoutText(GridLayout grd, String txt, int row, int col)
	{
		TextView vw = new TextView(this);
		vw.setText(txt);
		AddLayoutChild(grd, vw, row, col);
	}
}
