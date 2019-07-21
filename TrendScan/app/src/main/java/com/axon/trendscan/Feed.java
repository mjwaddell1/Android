package com.axon.trendscan;
import android.text.StaticLayout;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

public class Feed
{

	static String APIToken1 = Util.GetPreference("Token1", "");
	static String APIToken2 = Util.GetPreference("Token2", "");

	public static Hashtable<String, String> GetStockNames(String[] pTkrs) //get full name for tickers, if found,
	{
		//sort symbols so we can traverse results just once
		Arrays.sort(pTkrs);

		//use IEX API
		//String FilterURL = "https://api.iextrading.com/1.0/ref-data/symbols?format=CSV"; //or format=JSON, returns all stocks in alpha order
		String FilterURL = "https://cloud.iexapis.com/stable/ref-data/symbols?format=CSV&token=" + APIToken1; //or format=JSON, returns all stocks in alpha order

		Hashtable<String, String> TickerList = new Hashtable<>();
		try
		{
			StringBuilder sb = new StringBuilder();
			Util.GetWebData(sb, FilterURL, 2); //https://api.iextrading.com/1.0/ref-data/symbols?format=CSV
			if (sb.length() == 0)
				return GetKnownStockNames(pTkrs);  //fucking IEX
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

	public static Hashtable<String, String> GetKnownStockNames(String[] pTkrs)
	{
		Hashtable<String, String> lstKnown = new Hashtable<>();
		//TNA,BRZU,LABU,TECL,NAIL,DRN,DFEN,UBIO,JPNL,LBJ,UTSL,PILL,UGAZ,UCO
		lstKnown.put("TNA","Direxion Daily Small Cap Bull 3X Shares");
		lstKnown.put("BRZU","Direxion Daily MSCI Brazil Bull 3X Shares");
		lstKnown.put("LABU","Direxion Daily S&P Biotech Bull");
		lstKnown.put("TECL","Direxion Daily Technology Bull 3X Shares");
		lstKnown.put("NAIL","Direxion Daily Homebuilders & Supplies Bull 3X Shares");
		lstKnown.put("DRN","Direxion Daily MSCI Real Estate Bull 3X Shares");
		lstKnown.put("DFEN","Direxion Daily Aerospace & Defense Bull 3X Shares");
		lstKnown.put("UBIO","ProShares UltraPro Nasdaq Biotechnology");
		lstKnown.put("JPNL","Direxion Daily Japan Bull 3X Shares");
		lstKnown.put("LBJ","Direxion Daily Latin America Bull 3X Shares");
		lstKnown.put("UTSL","Direxion Daily Utilities Bull 3X Shares");
		lstKnown.put("PILL","Direxion Daily Pharmaceutical & Medical Bull 3X Shares");
		lstKnown.put("UGAZ","VelocityShares 3x Long Natural Gas ETN");
		lstKnown.put("UCO","ProShares Ultra Bloomberg Crude Oil");
		Hashtable<String, String> out = new Hashtable<>();
		for (String tkr : pTkrs)
		{
			if (lstKnown.containsKey(tkr))
				out.put(tkr, lstKnown.get(tkr));
			else
				out.put(tkr, tkr); //just copy symbol
		}
		return out;
	}

	public static Hashtable<String, float[]> GetPriceHistoryMultiThread(final int dayCnt, final String... tkrs) //Not Used
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
						//can only get history 1 stock at a time
						//use WorldTradingData API  //https://api.worldtradingdata.com/api/v1/history?symbol=LABU&sort=newest&date_from=2019-02-12&output=csv&api_token=iXTv6APQ4XfDBFVwg7gchBcC9gms4PyzU0CddXMhk9GZmz8Ac3tOtZI0hz8S
						String url = "https://api.worldtradingdata.com/api/v1/history?symbol=" + tkrs[ctr] + "&sort=newest&date_from=" + Util.DateAddDays(-dayCnt - 1) + "&output=csv&api_token=" + APIToken2; //at least 6 mo of data, latest date at bottom
						//String url = "https://cloud.iexapis.com/stable/stock/"+tkrs[ctr]+"/chart/1y?format=CSV&token=" + APIToken; //1 year of data, latest date at bottom
						tkrData[ctr] = new StringBuilder();
						Util.GetWebData(tkrData[ctr], url, 2); //skip header
					}
				});
				tkrCalls[i].start();
				try
				{
					Thread.sleep(100);
				} catch (InterruptedException ex)
				{
				} //don't overload data service
			}
			//wait for all threads to finish
			for (int i = 0; i < tkrCalls.length; i++)
				try
				{
					tkrCalls[i].join();
				} catch (InterruptedException ex)
				{
				}

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
				String[] lns = webstr.split("\n"); //each day
				float[] prclst = new float[Math.min(dayCnt + 1, lns.length + 1)]; //skip 0 entry
				for (int ln = 0; ln < prclst.length - 1; ln++)
				{
					//feed data in reverse, latest price last - EIX
					//String[] cols = lns[lns.length - ln - 1].split(",");
					//latest first - WTD
					String[] cols = lns[ln].split(",");
					prclst[ln + 1] = Float.parseFloat(cols[2]); //Date,Open,Close,High,Low,Volume  //WorldTradingData //skip 0 entry, that will be current price
					//prclst[ln+1] = Float.parseFloat(cols[4]); //date,open,high,low,close,volume,unadjustedVolume,change,changePercent,vwap,label,changeOverTime //IEX
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


	public static Hashtable<String, float[]> GetLastPriceOnly(final String... tkrs) //without history
	{
		Util.LI("GetLastPriceOnly", false);
		Hashtable<String, float[]> out = new Hashtable<>();
		for (String tkr : tkrs)
			out.put(tkr, new float[]{0}); //one entry per symbol
		return GetLastPrice(out, tkrs);
	}

	public static Hashtable<String, float[]> GetLastPrice(Hashtable<String, float[]> out, final String... tkrs)
	{
		try
		{
			Util.LI("GetLastPrice", false);
			String alltkrs = Util.StringJoin(tkrs, ",");
			StringBuilder sb = new StringBuilder();
			//max stocks per request is 5
			ArrayList<String> tkrsreq = new ArrayList<>(); //ticker list for one request
			int tkrcntreq = 0;
			//send requests 5 stocks per request, merge results
			for (int tkrcnt = 0; tkrcnt < tkrs.length; tkrcnt++) //   String tkr : tkrs)
			{
				String tkr = tkrs[tkrcnt];
				tkrcntreq++;
				tkrsreq.add(tkr);
				if (tkrcntreq == 5 || tkrcnt == tkrs.length - 1) //max stock cnt or end of list
				{
					//Send request
					//use WorldTradingData API  //https://api.worldtradingdata.com/api/v1/stock?symbol=LABU,BRZU,UCO&output=csv&api_token=iXTv6APQ4XfDBFVwg7gchBcC9gms4PyzU0CddXMhk9GZmz8Ac3tOtZI0hz8S
					String uri = "https://api.worldtradingdata.com/api/v1/stock?symbol=" + TextUtils.join(",", tkrsreq) + "&output=csv&api_token=" + APIToken2;
					//String uri = "https://cloud.iexapis.com/stable/stock/market/batch?symbols="+alltkrs+"&types=delayed-quote&format=JSON&token=" + APIToken;
					Util.GetWebData(sb, uri, 2); //1 based
					tkrcntreq = 0;
					tkrsreq.clear();
				}
			}
			int cnt = out.size(); //ensure we get each symbol
			//Symbol,Name,Currency,Price,"Price Open","Day High","Day Low","52 Week High","52 Week Low","Day Change","Change %","Close Yesterday","Market Cap",Volume,"Volume Avg",Shares,"Stock Exchange Long","Stock Exchange Short",Timezone,"Timezone Name","GMT Offset","Last Trade Time"
			//BRZU,"Direxion Daily MSCI Brazil Bull 3X Shares",USD,38.81,39.97,40.29,38.51,41.57,14.27,-0.72,-1.82,39.53,N/A,2314848,3413685,N/A,"NYSE Arca (Archipelago Exchange)",NYSEARCA,EDT,America/New_York,-14400,"2019-07-12 16:00:00"
			//LABU,"Direxion Daily S&P Biotech Bull 3X Shares",USD,46.55,47.06,47.46,45.39,112.79,24.00,-0.22,-0.47,46.77,N/A,1749511,1796214,N/A,"NYSE Arca (Archipelago Exchange)",NYSEARCA,EDT,America/New_York,-14400,"2019-07-12 16:00:00"
			//UCO,"ProShares Ultra Bloomberg Crude Oil",USD,20.78,20.75,21.00,20.55,39.36,12.20,-0.11,-0.53,20.89,N/A,1609908,3117014,N/A,"NYSE Arca (Archipelago Exchange)",NYSEARCA,EDT,America/New_York,-14400,"2019-07-12 16:00:00"

			//loop through CSV data
			String lines[] = sb.toString().split("\\r?\\n");
			for (String line : lines) //data should not include header row
			{
				String symbol = line.substring(0, line.indexOf(','));
				//name may have comma, so find USD
				int pos = line.indexOf(",USD,") + 5;
				int pos2 = line.indexOf(',', pos);
				String num = line.substring(pos, pos2);
				float[] prclst = out.get(symbol);
				prclst[0] = Float.parseFloat(num);  //(line.substring(pos, pos2));
				cnt--;
			}

//			for (String tkr : tkrs)
//			{
//				//{"SMG":{"delayed-quote":{"symbol":"SMG","delayedPrice":91.21,"high":91.26, .....
//				int pos = sb.indexOf("\"" + tkr + "\",\"delayed");
//				if (pos == -1) continue; //no symbol date
//				int pos2 = sb.indexOf(":", pos);
//				int pos3 = sb.indexOf(",", pos2);
//				float[] prclst = out.get(tkr);
//				prclst[0] = Float.parseFloat(sb.substring(pos2 + 1, pos3));
//				cnt--;
//			}
			if (cnt > 0)
				Util.LI("Last Price missed " + cnt);

		} catch (Exception e)
		{
			Util.LE(e.getMessage());
		}
		return out;
	}

	/*
	public static Hashtable<String, float[]> GetPriceHistorySingleThread(final int dayCnt, final String... tkrs)
	{
		try
		{
			Util.LI("GetPriceHistory - " + Util.StringJoin(tkrs,","), true);
			Hashtable<String, float[]> out = new Hashtable<>();
			//assume first returned entry is latest\current price
			final StringBuilder[] tkrData = new StringBuilder[tkrs.length];
			//do all calls in separate threads
			Thread[] tkrCalls = new Thread[tkrs.length];
			for (int ctr = 0; ctr < tkrCalls.length; ctr++)
			{
				//can only get history   1 stock at a time
				//use WorldTradingData API  //https://api.worldtradingdata.com/api/v1/history?symbol=LABU&sort=newest&date_from=2019-02-12&output=csv&api_token=iXTv6APQ4XfDBFVwg7gchBcC9gms4PyzU0CddXMhk9GZmz8Ac3tOtZI0hz8S
				String url = "https://api.worldtradingdata.com/api/v1/history?symbol="+tkrs[ctr]+"&sort=newest&date_from="+Util.DateAddMonths(-7)+"&output=csv&api_token=" + APIToken2; //1 year of data, latest date at bottom
				//String url = "https://cloud.iexapis.com/stable/stock/"+tkrs[ctr]+"/chart/1y?format=CSV&token=" + APIToken; //1 year of data, latest date at bottom
				tkrData[ctr] = new StringBuilder();
				Util.GetWebData(tkrData[ctr], url, 2); //skip header

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
*/

	/*
	public static Hashtable<String, float[]> GetLastPriceIEX(Hashtable<String, float[]> out, final String... tkrs)
	{
		try
		{
			Util.LI("GetLastPrice", false);
			String alltkrs = Util.StringJoin(tkrs,",");
			StringBuilder sb = new StringBuilder();
			//String uri = "https://api.iextrading.com/1.0/stock/market/batch?symbols="+alltkrs+"&types=quote&format=JSON";
			//String uri = "https://api.iextrading.com/1.0/tops/last?symbols="+alltkrs+"&format=csv";
			//delayed quote seems to have most accurate information
			//use WorldTradingData API  //https://api.worldtradingdata.com/api/v1/stock?symbol=LABU,BRZU,UCO&output=csv&api_token=iXTv6APQ4XfDBFVwg7gchBcC9gms4PyzU0CddXMhk9GZmz8Ac3tOtZI0hz8S

			String uri = "https://cloud.iexapis.com/stable/stock/market/batch?symbols="+alltkrs+"&types=delayed-quote&format=JSON&token=" + APIToken1;
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
	*/
}


