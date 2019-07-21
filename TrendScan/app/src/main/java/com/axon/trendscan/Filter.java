package com.axon.trendscan;

import android.graphics.Color;
import android.os.Bundle;

import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;

public class Filter
{
	public String FilterName = null; // ~ is default
	public String TkrList = null; //TNA,BRZU,LABU,TECL,NAIL,DRN,DFEN,UBIO,JPNL,LBJ,UTSL,PILL
	public String[] Tkrs = null;
	public Hashtable<String, TickerInfo> TickerList = new Hashtable<>();
	public int ButtonColorAlert = Color.rgb(0, 175, 0); //default green
	public int ButtonColorNorm = Color.rgb(150, 150, 150); //default grey
	public int PctChange = 0; //trigger change for this filter
	public int DaySpan = 0; //day span to check for this filter
	public int[] ShowSpans; //span pcts to show (5 day, 30 day,..)
	public boolean ShowAllStocks = false; //display full stock list (include non-alert stocks)
	public int PassCnt = 0; //number stocks passed filter

	public Filter(String pName)
	{
		FilterName = pName;
	}

	public Filter(Bundle b)
	{
		FromBundle(b);
	}

	public Filter(String pName, String pTkrs)
	{
		FilterName = pName;
		TkrList = pTkrs;
		Tkrs = pTkrs.split(",");
	}

	public Filter(String pName, String pTkrs, int pClrButton, int pPctChange, int pDaySpan, int[] pShowSpans)
	{
		FilterName = pName;
		ButtonColorAlert = pClrButton;
		PctChange = pPctChange;
		DaySpan = pDaySpan;
		ShowSpans = pShowSpans;
		TkrList = pTkrs;
		Tkrs = pTkrs.split(",");
	}

	public Bundle ToBundle() //app paused, store memory
	{
		Bundle b = new Bundle();
		b.putString("FilterName", FilterName);
		b.putString("TkrList", TkrList);
		b.putStringArray("Tkrs", Tkrs);
		b.putInt("ButtonColorAlert", ButtonColorAlert);
		b.putInt("ButtonColorNorm", ButtonColorNorm);
		b.putInt("pctChange", PctChange);
		b.putInt("daySpan", DaySpan);
		b.putIntArray("showSpans", ShowSpans);
		b.putBoolean("showAllStocks", ShowAllStocks);
		b.putInt("lastPriceHour", lastPriceHour);
		for (TickerInfo t : TickerList.values()) //each stock in this filter
			b.putBundle(t.Symbol, t.ToBundle());
		return b;
	}

	public void FromBundle(Bundle b) //app unpaused, restore object
	{
		FilterName = b.getString("FilterName");
		TkrList = b.getString("TkrList");
		Tkrs = b.getStringArray("Tkrs");
		ButtonColorAlert = b.getInt("ButtonColorAlert");
		ButtonColorNorm = b.getInt("ButtonColorNorm");
		PctChange = b.getInt("pctChange");
		DaySpan = b.getInt("daySpan");
		ShowSpans = b.getIntArray("showSpans");
		ShowAllStocks = b.getBoolean("showAllStocks");
		lastPriceHour = b.getInt("lastPriceHour", 100);
		TickerList.clear();
		for (String s : Tkrs) //each stock in this filter
			if (b.getBundle(s) != null)
				TickerList.put(s, new TickerInfo(b.getBundle(s)));
	}

	public void setTickerList(String pTkrs)
	{
		TkrList = pTkrs;
		Tkrs = pTkrs.split(",");
	}

	public void GetStockNames() //get full name for tickers from feed or saved data
	{
		Util.LI("Get Stock Names", true);
		try {
			String stks = "";
			TickerList.clear();
			Hashtable<String, String> comps = new Hashtable<>();
			if (Util.GetPreference("StockNames" + FilterName, "").isEmpty()) //can force refresh, re-save settings
				comps = Feed.GetStockNames(Tkrs); //from data feed
			else //get from preferences - AAPL=APPLE|LABU=DIREXION DAILY S&P BIOTECH BULL|VIX=CBOE VOLATILITY INDEX S&P500
			{
				String p = Util.GetPreference("StockNames" + FilterName, "");
				String[] xx = p.split("\\|"); //regex crap
				for (String st : xx) {
					String[] sx = st.split("=");
					comps.put(sx[0], sx[1]);
				}
				Util.LI("Cache had stock names", true);
			}
			int ctr = 0;
			for (String tkr : comps.keySet())
			{
				TickerList.put(tkr, new TickerInfo(tkr, comps.get(tkr)));
				stks += (ctr++ > 0 ? "|" : "") + tkr + "=" + comps.get(tkr);
			}
			Util.SavePreference("StockNames" + FilterName, stks); //store for next refresh
		}
		catch (Exception ex)
		{
			Util.LE("Error:" + ex.getMessage());
		}
	}

	public int FilterData() //determine which stocks pass filter
	{
		int passcnt = 0;
		for (TickerInfo t : TickerList.values())
		{
			t.PassedFilter =
				((PctChange > 0f && (t.Prices[0] / t.Prices[DaySpan] - 1.0 > PctChange / 100f)) ||
				(PctChange < 0f && (t.Prices[0] / t.Prices[DaySpan] - 1.0 < PctChange / 100f)));
			if (t.PassedFilter)
				passcnt++;
		}
		this.PassCnt = passcnt;
		return passcnt;
	}

	int lastPriceHour = 100; //always run first time
	public int RunFilter() //run filter request
	{
		Util.LI("RunFilter " + this.FilterName);
		try
		{
			if (TickerList.size() == 0)
				GetStockNames();
			Hashtable<String, float[]> prices;
			//check if new day or no data
			if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < lastPriceHour || TickerList.get(Tkrs[0]).Prices.length < 5) //get full history + current price
			{
				prices = Feed.GetPriceHistoryMultiThread(200, Tkrs); //for all tickers, 6 months
			for (TickerInfo tt : TickerList.values())
			{
				tt.Prices = prices.get(tt.Symbol);
				tt.LastPrice = tt.Prices[0]; //price history has latest price
				tt.CalcShowData(ShowSpans); //price change for each span
			}
			}
			else  //update current price
			{
				prices = Feed.GetLastPriceOnly(Tkrs); //for all tickers, one day
				for (String tkr : Tkrs)
					TickerList.get(tkr).Prices[0] = prices.get(tkr)[0]; //only one entry
				for (TickerInfo tt : TickerList.values())
				{
					tt.Prices[0] = prices.get(tt.Symbol)[0]; //last entry only
					tt.LastPrice = tt.Prices[0]; //price history has latest price
					tt.CalcShowData(ShowSpans); //price change for each span
				}
			}
			lastPriceHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			return FilterData(); //pass\fail
		}
		catch(Exception ex)
		{
			Util.LE(ex.getMessage());
		}
		return -1; //error
	}
}

