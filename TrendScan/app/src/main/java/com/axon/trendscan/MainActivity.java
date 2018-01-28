package com.axon.trendscan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.widget.LinearLayout.*;
import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;

import static android.R.attr.bitmap;
import static android.R.attr.color;

public class MainActivity extends Activity //main display screen
{

	public static MainActivity mMainActivity;
	TextView txtUpdateTime = null;
	Button btnService = null;
	Button btnRefresh = null;
	SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		//savedInstanceState is always null for some reason, so store in service
		if (savedInstanceState == null && ScanSvc.saveState != null)
			savedInstanceState = ScanSvc.saveState; //use state from service

		setContentView(R.layout.activity_main);

		mMainActivity = this; //referenced by other classes

		txtUpdateTime = (TextView) findViewById(R.id.txtUpdateTime);

		btnService = (Button) findViewById(R.id.btnService);
		btnService.setText((ScanSvc.mInstance == null ? "Start" : "Stop") + " Service"); //upper case on button
		btnService.setOnClickListener(new View.OnClickListener() //start\stop service
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
				}
				else //already started, so attempt stop
					//stopService returns true if stopped successfully
					getApplicationContext().stopService(svc);
			}
		});

		//Refresh button refreshes stock list on GUI. Will start\stop service if svc not running
		btnRefresh = (Button) findViewById(R.id.btnRefresh);
		btnRefresh.setOnClickListener(new View.OnClickListener() //Refresh button
		{
			@Override
			public void onClick(View view)
			{
				Util.LI("Refresh Click");
				//data load must run in background thread, so restart timer
				if (ScanSvc.mInstance != null)
				{
					if (ScanSvc.mDataLoading)
					Util.ShowToast("Data loading. Please wait.");
				else
						ScanSvc.mInstance.StartTimer(); //restart timer
				}
				else
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
			ShowStockList(true); //parse saved data, show list on gui
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) //user switching apps or rotating
	{
		super.onSaveInstanceState(outState);
		SerializeFilters(outState); //save filter data
		ScanSvc.saveState = outState; //Android not storing state correctly so store in service
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) //rotated
	{
		super.onRestoreInstanceState(savedInstanceState);
	}

	public void SerializeFilters(Bundle outState) //save filter data
	{
		String[] filters = new String[ScanSvc.Filters.size()];
		int ctr = 0;
		for (Filter f : ScanSvc.Filters.values())
		{
			filters[ctr] = f.FilterName;
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
		//clear stock list on gui
		linStock.removeAllViews();
		linStock.setDividerPadding(0);

		int cnt = ParseStockData(linStock); //show stock list, returns stock cnt shown
		TextView ns = (TextView) findViewById(R.id.txtNoStocks); //hidden if stocks
		ns.setVisibility(cnt == 0 ? View.VISIBLE : View.GONE);
		txtUpdateTime.setText("Last Check: " + sdfDate.format(ScanSvc.dtLastCheck) + " [" + cnt + "-" + ScanSvc.Filters.size()+"]");
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
		try
		{
			Point sz = new Point();
			getWindowManager().getDefaultDisplay().getSize(sz); //screen dimensions
			//assume portrait
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
				Arrays.sort(f.Tkrs); //sort stock symbols
				final GridLayout grdFilter = new GridLayout(this); //per filter
				linStock.addView(grdFilter); //each filter has separate grid
				grdFilter.setColumnCount(f.ShowSpans.length+1); //tkr + spans
				grdFilter.setRowCount(f.Tkrs.length * 2); //ticker button + chart
				int rowctr = 0;

				for (String tkr : f.Tkrs) //each stock in filter
				{
					final TickerInfo ti = f.TickerList.get(tkr);
					if (ti == null) //needed?
					{
						Util.LI(tkr + ": No Ticker Data", true);
						continue;
					}
					if (!f.ShowAllStocks && !ti.PassedFilter) //only show alert stocks
						continue;
					if (ti.Symbol.contains("$") || ti.Symbol.contains("."))
						continue; //dup stock, $IBM IBM.A

					//create main button with symbol name, day spans
					//button is transparent in front of text
					//use textview for row background
					TextView tv = new TextView(this);
					tv.setHeight(buttonHeight);
					tv.setWidth(screenWidth - 10);
					tv.setBackgroundColor(ti.PassedFilter ? f.ButtonColorAlert : f.ButtonColorNorm);
					tv.setStateListAnimator(null); //allow send to background
					tv.setElevation(-1); //send to background if needed
					AddLayoutChild(grdFilter, tv, rowctr, 0, grdFilter.getColumnCount());

					//transparent button in front of row
					Button btn = new Button(this);
					btn.setAllCaps(false);
					btn.setMinHeight(0);
					btn.setMinimumHeight(0); //google fuckers
					btn.setMinWidth(0);
					btn.setMinimumWidth(0); //google fuckers
					btn.setHeight(buttonHeight*105/100);
					btn.setPadding(20,0,0,0);
					btn.setWidth(screenWidth - 10);
					btn.setBackgroundColor(Color.TRANSPARENT); //button on top of text and backcolor
					AddLayoutChild(grdFilter, btn, rowctr, 0, grdFilter.getColumnCount());

					//first row is symbol and span data, next row is chart, etc.
					String s = f.FilterName + " : " + ti.Symbol + " :";
					if (f.FilterName.equals("~")) s = ti.Symbol + " :"; //default filter, remove ~
					AddLayoutText(grdFilter, " " + s+"   ", rowctr, 0, Color.BLACK);
					for (int i=0;i<f.ShowSpans.length;i++)  //day spans
						AddLayoutText(grdFilter, f.ShowSpans[i]+"="+ ti.SpanChgs[i]+"   ", rowctr, i+1, Color.BLACK);

					//chart, stock data in next row
					//linear layout for chart icon and stock name
					final LinearLayout linDetails = new LinearLayout(this);
					linDetails.setOrientation(LinearLayout.HORIZONTAL);
					linDetails.setGravity(Gravity.CENTER_VERTICAL);

					//chart button
					Button btnChart = new Button(this);
					btnChart.setLayoutParams(lpChart); //set height/width
					btnChart.setMinimumWidth(0);
					btnChart.setAllCaps(false);
					//chart image uses actual price history
					btnChart.setBackground(new BitmapDrawable(this.getResources(),ti.GetTickerChart()));
					btnChart.setGravity(Gravity.LEFT);
					linDetails.addView(btnChart);

					//data next to chart
					final LinearLayout linData = new LinearLayout(this);
					linData.setOrientation(LinearLayout.VERTICAL);
					linData.setGravity(Gravity.CENTER_VERTICAL);

					//stock name
					TextView txt = new TextView(this);
					txt.setText(ti.StockName == null ? "" : ti.StockName);
					txt.setGravity(Gravity.CENTER_VERTICAL);
					txt.setTextColor(Color.BLUE);
					linData.addView(txt);

					//grid for stock span data, next to chart
					final GridLayout grdData = new GridLayout(this);
					grdData.setColumnCount(2);
					grdData.setRowCount(f.ShowSpans.length+1);

					//if user clicks chart, open web site with symbol
					btnChart.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View view)
						{
							Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SettingsActivity.ChartURL.replace("{SYMBOL}", ti.Symbol)));
							startActivity(browserIntent);
						}
					});

					AddLayoutText(grdData, "Price  ", 0, 0);
					AddLayoutText(grdData, ""+ti.LastPrice, 0, 1);
					int rw = 1; //row in data block
					for (int i=0;i<f.ShowSpans.length;i++)
					{
						AddLayoutText(grdData, f.ShowSpans[i] + " day   ", rw, 0);
						AddLayoutText(grdData, ""+ti.SpanChgs[i], rw, 1);
						rw++;
					}
					linData.addView(grdData);
					linDetails.addView(linData);

					//add details block to main filter grid
					AddLayoutChild(grdFilter, linDetails, rowctr+1, 0, grdFilter.getColumnCount()); //across all columns
					linDetails.setVisibility(View.GONE); //default hidden, show when user clicks button

					//show\hide detail block
					btn.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View view)
						{
							linDetails.setVisibility(linDetails.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
						}
					});
					cnt++;
					rowctr+=2;
				}
			}
			return cnt;
		} catch (Exception ex)
		{
			Toast.makeText(mMainActivity, ex.getMessage(), Toast.LENGTH_LONG).show();
			return -1;
		}
	}

	public void AddLayoutChild(GridLayout grd, View vChild, int row, int col)
	{
		GridLayout.LayoutParams p =
		new GridLayout.LayoutParams(
				GridLayout.spec(row, GridLayout.LEFT),
				GridLayout.spec(col, GridLayout.LEFT));
		grd.addView(vChild, p);
	}

	public void AddLayoutChild(GridLayout grd, View vChild, int row, int col, int colspan)
	{
		GridLayout.LayoutParams p =
				new GridLayout.LayoutParams(
						GridLayout.spec(row, GridLayout.LEFT),
						GridLayout.spec(col, GridLayout.LEFT));
		p.columnSpec = GridLayout.spec(col, colspan);
		p.bottomMargin = 0;
		grd.addView(vChild, p);
	}

	public void AddLayoutText(GridLayout grd, String txt, int row, int col, int textColor)
	{
		TextView vw = new TextView(this);
		vw.setTextColor(textColor);
		vw.setText(txt);
		AddLayoutChild(grd, vw, row, col);
	}

	public void AddLayoutText(GridLayout grd, String txt, int row, int col)
	{
		TextView vw = new TextView(this);
		vw.setText(txt);
		AddLayoutChild(grd, vw, row, col);
	}
}
