package com.axon.trendscan;
import android.text.StaticLayout;

import java.util.Arrays;
import java.util.Hashtable;

public class Feed
{

	static String APIToken = Util.GetPreference("Token", "");

	public static Hashtable<String, String> GetStockNames(String[] pTkrs) //get full name for tickers, if found
	{
		//sort symbols so we can traverse results just once
		Arrays.sort(pTkrs);


		//String FilterURL = "https://api.iextrading.com/1.0/ref-data/symbols?format=CSV"; //or format=JSON, returns all stocks in alpha order
		String FilterURL = "https://cloud.iexapis.com/stable/ref-data/symbols?format=CSV&token=" + APIToken; //or format=JSON, returns all stocks in alpha order

		Hashtable<String, String> TickerList = new Hashtable<>();
		try
		{
			StringBuilder sb = new StringBuilder();
			Util.GetWebData(sb, FilterURL, 2); //https://api.iextrading.com/1.0/ref-data/symbols?format=CSV
			String[] lns = sb.toString().split("\n");
			int ctr = 0; //symbol index
			for (String ln : lns)
			{
				while (ctr < pTkrs.length && ln.substring(0, pTkrs[ctr].length()).compareTo(pTkrs[ctr]) > 0)
					ctr++; //symbol not found, move to next
				if (ctr >= pTkrs.length) break; //if last symbol
				if (ln.startsWith(pTkrs[ctr] + ","))
				{
					String[] cols = ln.split(",");
					TickerList.put(pTkrs[ctr], cols[1]);  //symbol, name
					if (++ctr >= pTkrs.length) break; //next symbol
				}
			}
			return TickerList;
		} catch (Exception ex)
		{
			Util.LE(ex.getMessage());
		}
		return null;
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
						String url = "https://cloud.iexapis.com/stable/stock/"+tkrs[ctr]+"/chart/1y?format=CSV&token=" + APIToken; //1 year of data, latest date at bottom
						tkrData[ctr] = new StringBuilder();
						Util.GetWebData(tkrData[ctr], url, 2); //skip header
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
					Util.LE(tkrs[i] + " - " + webstr);
					continue;
				}
				String[] lns = webstr.split("\n");
				float[] prclst = new float[Math.min(dayCnt+1, lns.length+1)];
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
			Util.LE(e.getMessage());
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
			String uri = "https://cloud.iexapis.com/stable/stock/market/batch?symbols="+alltkrs+"&types=delayed-quote&format=JSON&token=" + APIToken;
			Util.GetWebData(sb, uri, 0);
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
}


