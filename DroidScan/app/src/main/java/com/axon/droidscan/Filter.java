package com.axon.droidscan;


import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.regex.Pattern;

public class Filter
{
	public String FilterName = null; // ~ is default
	public String FilterURL = null; //Intrinio URL for stock screener
	ArrayList<TickerInfo> TickerList = new ArrayList<TickerInfo>(); //stock result list for filter
	public String[] Tkrs = null;
	public int ButtonColor = Color.rgb(0, 175, 0); //default green
	public long CheckInterval = 30*60*1000L; //30 minutes default
	public long LastCheck = -2 * CheckInterval;  //milliseconds since boot
	public String JSONData = null; //data returned from Intrinio

	public Filter(String pName, String url)
	{
		FilterName = pName;
		FilterURL = url;
	}

	public Filter(String pName, String url, int clrButton, String json)
	{
		FilterName = pName;
		FilterURL = url;
		ButtonColor = clrButton;
		JSONData = json;
	}

	public Filter(Bundle b)
	{
		FromBundle(b);
	}

	public Bundle ToBundle() //app paused, store memory
	{
		Bundle b = new Bundle();
		b.putString("FilterName", FilterName);
		b.putString("FilterURL", FilterURL);
		b.putStringArray("Tkrs", Tkrs);
		b.putInt("ButtonColor", ButtonColor);
		for (TickerInfo t : TickerList) //each stock in this filter
			b.putBundle(t.Ticker, t.ToBundle());
		b.putString("JSONData", JSONData);
		b.putLong("LastCheck",LastCheck);
		return b;
	}

	public void FromBundle(Bundle b) //app unpaused, restore object
	{
		FilterName = b.getString("FilterName");
		FilterURL = b.getString("FilterURL");
		Tkrs = b.getStringArray("Tkrs");
		ButtonColor = b.getInt("ButtonColor");
		TickerList.clear();
		JSONData = b.getString("JSONData");
		LastCheck = b.getLong("LastCheck");
		if (Tkrs == null) return;
		for (String s : Tkrs) //each stock in this filter
			if (b.getBundle(s) != null)
				TickerList.add(new TickerInfo(b.getBundle(s)));
	}

	public boolean PassedCheckInterval()
	{
		return SystemClock.elapsedRealtime() - LastCheck  > CheckInterval;
	}

	public int RunFilter() //run filter request, get json data
	{
		if (!PassedCheckInterval())
			return 0; //another filter updated
		StringBuilder sbWebReturn = new StringBuilder();
		Util.GetWebData(sbWebReturn, FilterURL, true, 0);
		LastCheck = SystemClock.elapsedRealtime();
		GetTickerDataFromJSON(sbWebReturn);
		Hashtable<String, float[]> prices = Util.GetPriceHistory(126, Tkrs); //for all tickers, 6 months
		ArrayList<TickerInfo> remove = new ArrayList<>();
		for (TickerInfo tt : TickerList)
		{
			tt.Prices = prices.get(tt.Ticker);
			if (tt.Prices == null)
				remove.add(tt);
			else
				tt.LastPrice = tt.Prices[0]; //price history has latest price
		}
		for (TickerInfo tkr : remove)
			TickerList.remove(tkr); //remove bad ticker
		return TickerList.size();
	}

	public int GetTickerDataFromJSON(StringBuilder sbJSON) //parse json string
	{
		if (sbJSON == null) //deserializing from savedInstanceState, JSONData already set
			sbJSON = new StringBuilder(JSONData);
		else
			JSONData = sbJSON.toString(); //got data from service

		//create Ticker object for each stock returned
		TickerList.clear();
		if (sbJSON.length() == 0) return -1; //no data
		if (sbJSON.indexOf("[]") > 0) return 0; //no stocks passed filter
		int b1 = sbJSON.indexOf("[{"); //{"data":[{
		sbJSON.replace(0, b1 + 2, "");
		int b2 = sbJSON.indexOf("}]"); //}],"result_count":10, .....
		sbJSON.replace(b2, sbJSON.length(), "");
		Util.replaceAll(sbJSON, "},{", "!");
		String[] tkrsx = Pattern.compile("!").split(sbJSON); //each stock
		//"bookvaluepershare":30.0163,"currentratio":0.6126,"debttoequity":1.0879,"ltdebttoequity":0.9245,"ticker":"AEE"
		Tkrs = new String[tkrsx.length];
		int tkctr = 0;
		for (String stk : tkrsx) //each stock returned from filter
		{
			String[] info = stk.replace("\"", "").split(",");
			Hashtable<String, Double> data = new Hashtable<>();
			String TkrSymbol = null;
			for (String ii : info) //each data element (EPS, ProfitMargin, ...) of stock
			{
				String[] x = ii.split(":");
				if (x[0].equals("ticker"))
					TkrSymbol = x[1];
				else
					data.put(x[0], Double.parseDouble(x[1])); //all elements are doubles
			}
			Tkrs[tkctr++] = TkrSymbol;
			TickerList.add(new TickerInfo(TkrSymbol,
					(ScanSvc.CompanyList.containsKey(TkrSymbol) ? ScanSvc.CompanyList.get(TkrSymbol) : null),
					0f, data));
		}
		return TickerList.size();

		/*
			API call limit 500/day, each filter item is one call
			http://blog.intrinio.com/whats-an-api-call-and-how-are-they-counted/?utm_source=Intercom&utm_campaign=Support%20Whats%20an%20API%20call%20and%20how%20are%20they%20counted&utm_medium=Support

			max 10 items per web call

			--Send
			https://api.intrinio.com/securities/search?conditions=bookvaluepershare~gt~25,currentratio~lt~1,debttoequity~lt~1,ltdebttoequity~lt~1,profitmargin~gt~0.10,profitmargin~lt~1,dividendyield~gt~0.01,percent_change~lt~100

			-- Return (columns match sent screen columns). Note total_pages. App only shows first page (max 100 stocks) per filter.
			{"data":[
			{"bookvaluepershare":49.0701,"currentratio":0.6888,"debttoequity":0.3775,"ltdebttoequity":0.3133,"profitmargin":0.112481,"dividendyield":0.032678,"percent_change":0.0097,"ticker":"AGR"},
			{"bookvaluepershare":28.1034,"currentratio":0.8007,"debttoequity":0.7737,"ltdebttoequity":0.678,"profitmargin":0.103402,"dividendyield":0.017833,"percent_change":0.0295,"ticker":"ALK"},
			{"bookvaluepershare":36.7452,"currentratio":0.5325,"debttoequity":0.9015,"ltdebttoequity":0.7867,"profitmargin":0.138677,"dividendyield":0.020328,"percent_change":0.0086,"ticker":"ATO"},
			{"bookvaluepershare":26.1819,"currentratio":0.8109,"debttoequity":0.548,"ltdebttoequity":0.4143,"profitmargin":0.169868,"dividendyield":0.021951,"percent_change":0.006,"ticker":"DIS"},
			{"bookvaluepershare":44.5427,"currentratio":0.5099,"debttoequity":0.8978,"ltdebttoequity":0.7959,"profitmargin":0.132665,"dividendyield":0.026769,"percent_change":0.004,"ticker":"EIX"},
			{"bookvaluepershare":26.1913,"currentratio":0.8962,"debttoequity":0.518,"ltdebttoequity":0.518,"profitmargin":0.697208,"dividendyield":0.055203,"percent_change":-0.0096,"ticker":"EQM"},
			{"bookvaluepershare":48.2422,"currentratio":0.77,"debttoequity":0.5343,"ltdebttoequity":0.4799,"profitmargin":0.150237,"dividendyield":0.038617,"percent_change":-0.0079,"ticker":"KHC"},
			{"bookvaluepershare":39.0197,"currentratio":0.6926,"debttoequity":0.5979,"ltdebttoequity":0.508,"profitmargin":0.214915,"dividendyield":0.012632,"percent_change":0.0068,"ticker":"KSU"},
			{"bookvaluepershare":39.0197,"currentratio":0.6926,"debttoequity":0.5979,"ltdebttoequity":0.508,"profitmargin":0.214915,"dividendyield":0.012632,"percent_change":0.0,"ticker":"KSU$"},
			{"bookvaluepershare":44.3295,"currentratio":0.8284,"debttoequity":0.7763,"ltdebttoequity":0.7292,"profitmargin":0.178558,"dividendyield":0.018748,"percent_change":0.0209,"ticker":"NSC"},
			{"bookvaluepershare":45.7475,"currentratio":0.9011,"debttoequity":0.9151,"ltdebttoequity":0.851,"profitmargin":0.152212,"dividendyield":0.029302,"percent_change":0.0086,"ticker":"PNW"},
			{"bookvaluepershare":26.9691,"currentratio":0.9491,"debttoequity":0.9896,"ltdebttoequity":0.948,"profitmargin":0.102081,"dividendyield":0.026917,"percent_change":0.0039,"ticker":"POR"},
			{"bookvaluepershare":48.3518,"currentratio":0.1578,"debttoequity":0.7275,"ltdebttoequity":0.5823,"profitmargin":0.184059,"dividendyield":0.020032,"percent_change":0.0044,"ticker":"RCL"},
			{"bookvaluepershare":39.9904,"currentratio":0.1684,"debttoequity":0.6443,"ltdebttoequity":0.4461,"profitmargin":0.552163,"dividendyield":0.071919,"percent_change":-0.0048,"ticker":"SEP"},
			{"bookvaluepershare":57.6822,"currentratio":0.6852,"debttoequity":0.9703,"ltdebttoequity":0.8328,"profitmargin":0.182057,"dividendyield":0.02087,"percent_change":-0.0101,"ticker":"TAP"},
			{"bookvaluepershare":57.6822,"currentratio":0.6852,"debttoequity":0.9703,"ltdebttoequity":0.8328,"profitmargin":0.182057,"dividendyield":0.02087,"percent_change":0.0,"ticker":"TAP.A"}],
			"result_count":16,"page_size":100,"current_page":1,"total_pages":1,"api_call_credits":8}
		 */
	}
}
