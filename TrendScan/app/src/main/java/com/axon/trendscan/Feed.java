package com.axon.trendscan;
import java.util.Hashtable;

public class Feed
{

	public static Hashtable<String, String> GetStockNames(String[] pTkrs) //get full name for tickers, if found
	{
		//clicktocontinue just reads from a static symbol list, should find alternate data source
		String FilterURL = "http://www.clicktocontinue.com/SymbolLookup.aspx?format=CSV&symbol=" + Util.StringJoin(pTkrs, ","); //or format=JSON

		Hashtable<String, String> TickerList = new Hashtable<>();
		try
		{
			StringBuilder sb = new StringBuilder();
			Util.GetWebData(sb, FilterURL, 0); //http://www.clicktocontinue.com/SymbolLookup.aspx?format=CSV&symbol=LABU,VIX
			String[] lns = sb.toString().split("\n");
			for (String tkr : pTkrs)
				for (String ln : lns)
					if (ln.startsWith(tkr + ","))
					{
						String[] cols = ln.split(",");
						TickerList.put(tkr, cols[2]);  //ticker, name
						break; //next ticker
					}
			return TickerList;
		} catch (Exception ex)
		{
			Util.ShowToast(ex.getMessage());
		}
		return null;
	}

	//get price history for each symbol, must register (free) to get regular downloads
	public static Hashtable<String, float[]> GetPriceHistory(final int dayCnt, final String... tkrs)
	{
		Util.ShowToast("GetPriceHistory - " + Util.StringJoin(tkrs,","));
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
					//get price history for this ticker
					String url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol=" + tkrs[ctr] + "&apikey=" + SettingsActivity.APIKey + "&datatype=csv&outputsize=full";
					tkrData[ctr] = new StringBuilder();
					Util.GetWebData(tkrData[ctr], url, 2, dayCnt+1); //6 months of data = 126 days, skip header
				}
			});
			tkrCalls[i].start();
			try{Thread.sleep(100);}catch(InterruptedException ex){} //don't overload data service
		}
		//wait for all threads to finish
		for (int i = 0; i < tkrCalls.length; i++)
			try { tkrCalls[i].join(); } catch (InterruptedException ex) {}

		Util.ShowToast("Got Price History");

		//parse and merge results
		for (int i = 0; i < tkrCalls.length; i++)
		{
			//timestamp,open,high,low,close,adjusted_close,volume,dividend_amount,split_coefficient
			String webstr = tkrData[i].toString();
			if (!webstr.startsWith("20")) //if not 2018, then error
			{
				Util.ShowToast(tkrs[i] + " - " + webstr);
				continue;
			}
			String[] lns = webstr.split("\n");
			float[] prclst = new float[lns.length];
			for (int ln = 0; ln < lns.length; ln++)
			{
				String[] cols = lns[ln].split(",");
				prclst[ln] = Float.parseFloat(cols[5]); //adjusted_close
			}
			out.put(tkrs[i], prclst);
		}
		return out;
	}
}

	/*
	Root,Symbol,Description,Type
	LABU,LABU,DIREXION DAILY S&P BIOTECH BULL,EQUITY
	LABU,LABU.IV.X,DIREXION DAILY S&P BIOTECH BULL IOPV,INDEX
	LABU,LABU.NV.X,DIREXION DAILY S&P BIOTECH BULL NAV,INDEX
	LABU,LABU.SO.X,DIREXION DAILY S&P BIOTECH BULL SHS OUT,INDEX
	LABU,LABU.TC.X,DIREXION DAILY S&P BIOTECH BULL 3X SHARE,INDEX
	VIX,VIX.XO,CBOE VOLATILITY INDEX S&P500,INDEX
	TNA,TNA,DIREXION DAILY SMALL CAP BULL,EQUITY
	TNA,TNA.IV.X,SMALL CAP BULL 3X SHARES IIV,INDEX
	TNA,TNA.NV.X,SMALL CAP BULL 3X SHARES NAV,INDEX
	TNA,TNA.SO.X,SMALL CAP BULL 3X SHARES>SHARES OUT<,INDEX
	TNA,TNA.TC.X,DIREXION DAILY SMALL CAP BULL 3X SHARES,INDEX
	AAPL,AAPL,APPLE,EQUITY
	 */

