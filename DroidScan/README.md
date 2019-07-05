Stock scanner app for Android

-- Main purpose of application

DroidScan allows users to enter filter parameters for scanning stocks. By default the filter process is run every 30 minutes. The price history (6 months) for each displayed stock is also retrieved. Multiple filters are supported.

-- Notes

This project was created using Android Studio 2.3.1.

To access the filter feed, you must register with Intrinio. The feed is free (500 calls per day). After registering, enter your username\password on the settings screen (or in SettingsActivity.java).
https://intrinio.com/account

Note that fine print on API calls - they add up fast
http://blog.intrinio.com/whats-an-api-call-and-how-are-they-counted

The available filter parameters can be found here:
http://docs.intrinio.com/tags/intrinio-public#financial 

The price history is provided by IEX Trading. The API documentation can be found here: https://iextrading.com/developer/docs/

IEX provides 5 years of chart data, but the app only stores the last 6 months to reduce memory load.

The app targets my phone - a Samsung J7 (720p).

NOTE - IEX recently changed there API. You need to get a (free) token and attach it to each request. You can get a token at https://iextrading.com/developer/.

    Old Call:  https://api.iextrading.com/1.0/ref-data/symbols?format=CSV

    New Call:  https://cloud.iexapis.com/stable/ref-data/symbols?format=CSV&token=[YourPkToken]
    
Questions and comments are welcome - mjwaddell {AT} hotmail.com
