Stock scanner app for Android

-- Main purpose of application

TrendScan allows users to enter a list of stocks to watch. Every 30 minutes the price history of each stock is downloaded and a scan filter is run to check if the price history matches a desired trend (i.e -10% change over 5 days). Multiple filters are supported.

-- Notes

This project was created using Android Studio 2.3.1.

The price data feed is provided by IEX Trading. The API documentation can be found here: https://iextrading.com/developer/docs/

IEX provides 5 years of chart data, but the app only stores the last 6 months to reduce memory load.

The app targets my phone - a Samsung J7 (720p).

NOTE - IEX recently changed there API. You need to get a (free) token and attach it to each request. You can get a token at https://iextrading.com/developer/. 

    Old Call:  https://api.iextrading.com/1.0/ref-data/symbols?format=CSV

    New Call:  https://cloud.iexapis.com/stable/ref-data/symbols?format=CSV&token=[YourToken]


==== UPDATE ====

IEX severely reduced their request limit, so for stock prices, I switched to World Trading Data (also free)

    https://api.worldtradingdata.com/api/v1/history?symbol=LABU&output=csv&api_token=[YourToken]

Questions and comments are welcome - mjwaddell {AT} hotmail.com
