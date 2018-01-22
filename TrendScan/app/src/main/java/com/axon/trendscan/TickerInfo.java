package com.axon.trendscan;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;

public class TickerInfo //single stock (ETF)
{
	public String Symbol = null; //stock symbol
	public String StockName = null;
	public String TkrURL = null;
	public float LastPrice = 0;
	public float[] Prices; //just prices
	public float[] SpanChgs = null; //price chg per span (5 day, 30 day,...)
	public boolean PassedFilter = false;

	public TickerInfo(String tkr, String stkName)
	{
		Symbol = tkr;
		StockName = stkName;
	}

	public TickerInfo(Bundle b)
	{
		FromBundle(b);
	}

	public Bundle ToBundle() //save to preferences
	{
		Bundle b = new Bundle();
		b.putString("Ticker", Symbol);
		b.putString("StockName", StockName);
		b.putString("TkrURL", TkrURL);
		b.putFloatArray("Prices", Prices);
		b.putFloatArray("SpanChgs", SpanChgs);
		b.putBoolean("PassedFilter", PassedFilter);
		return b;
	}

	public void FromBundle(Bundle b) //restore to preferences
	{
		Symbol = b.getString("Ticker");
		StockName = b.getString("StockName");
		TkrURL = b.getString("TkrURL");
		Prices = b.getFloatArray("Prices");
		SpanChgs = b.getFloatArray("SpanChgs");
		PassedFilter = b.getBoolean("PassedFilter");
	}

	public void CalcShowData(int[] spans) //pct changes for each day span to be displayed
	{
		SpanChgs = new float[spans.length];
		for (int i=0;i<SpanChgs.length;i++)
		{
			if (Prices.length >= spans[i])
				SpanChgs[i] = Math.round((Prices[0] / Prices[spans[i]] - 1.0) * 1000) / 10.0f; //1 decimal place
			else
				SpanChgs[i]=0f; //out of range
		}
	}

	// create price chart bitmap for last 6 months
	// chart has no labels, not needed. Only care about percentage changes.
	// Bottom of chart is price=0, top is max price over last 6 mos
	public Bitmap GetTickerChart()
	{
		//height and width are virtual, not actual pixels. Actual chart size determined in MainActivity (lpPop)
		int width = 1000; //only for proportions
		int height = 1000; //width * 2/3; //actual display size set in MainActivity
		float maxprice = 0; //max price last 6 months
		int chartbottom = 25; //chart border
		int charttop = height - chartbottom;
		int chartleft = 20;
		int chartright = width - chartleft;

		for (int i = 0; i < Prices.length; i++)
			if (Prices[i] > maxprice)
				maxprice = Prices[i];

		//scale data points to bitmap width, height
		float scaleX = (chartright - chartleft) / (float)Prices.length;
		float scaleY = (charttop - chartbottom) / maxprice;
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		Paint paint = new Paint();
		paint.setColor(Color.parseColor("#DD9EDD")); //border
		paint.setStyle(Paint.Style.FILL);
		canvas.drawPaint(paint);
		paint.setColor(Color.parseColor("#FF9EFF")); //chart area
		canvas.drawRect(chartleft, charttop, chartright, chartbottom, paint);

		paint.setStrokeWidth(5);
		paint.setAntiAlias(true);
		paint.setColor(Color.parseColor("#DD9EDD"));

		//1 month bars
		for (int i = 0; i < Prices.length - 1; i++)
		{
			if (i > 0 && i < 110 && i%21.0 == 0) //each month, except last
			{
				canvas.drawLine(
						chartright - (i * scaleX), charttop,
						chartright - (i * scaleX), chartbottom,
						paint);
			}
		}

		//10% horizontal bars
		float t = (charttop - chartbottom) / 10.0f;
		for (int i = 1; i < 10; i++)
		{
			canvas.drawLine(
					chartright, chartbottom + t * i,
					chartleft, chartbottom + t * i,
					paint);
		}

		//price line chart
		paint.setColor(Color.BLACK);//line color
		for (int i = 0; i < Prices.length - 1; i++)
		{
			canvas.drawLine(chartright - (i * scaleX),
					charttop - (Prices[i] * scaleY),
					chartright - ((i + 1) * scaleX),
					charttop - (Prices[i + 1] * scaleY),
					paint);
		}

		//paint.setTextSize(14.f);
		//paint.setTextAlign(Paint.Align.CENTER);
		//canvas.drawText("test", (width / 2.f) , (height / 2.f), paint);
		return bitmap;
	}
}
