package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

/**
 * Current weather for Launcher 2's greeting card, from Open-Meteo (free, no key): the city is looked up once,
 * then the temperature and condition are fetched at most every 30 minutes and kept in prefs for offline starts.
 */
final class Weather {
    private Weather() {}

    interface Callback { void done(String temp, String condition, String city); }

    private static final long MAX_AGE_MS = 30 * 60 * 1000L;

    static String city(Context c) {
        return c.getSharedPreferences("weather", Context.MODE_PRIVATE)
                .getString("city", c.getString(R.string.weather_city)).trim();
    }

    /** Cached answer at once (if any), then a fresh one when the cache is old. */
    static void get(Context c, Callback cb) {
        SharedPreferences p = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        String city = city(c);
        if (city.isEmpty()) return;
        boolean sameCity = city.equalsIgnoreCase(p.getString("for", ""));
        if (sameCity && p.contains("temp")) cb.done(p.getString("temp", ""), p.getString("cond", ""), p.getString("name", city));
        if (sameCity && System.currentTimeMillis() - p.getLong("at", 0) < MAX_AGE_MS) return;
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject geo = json("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + Uri.encode(city))
                        .getJSONArray("results").getJSONObject(0);
                double lat = geo.getDouble("latitude"), lon = geo.getDouble("longitude");
                String name = geo.getString("name");
                JSONObject cur = json("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day").getJSONObject("current");
                String temp = Math.round(cur.getDouble("temperature_2m")) + "°";
                String cond = condition(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
                p.edit().putString("for", city).putString("temp", temp).putString("cond", cond).putString("name", name)
                        .putLong("at", System.currentTimeMillis()).apply();
                main.post(() -> cb.done(temp, cond, name));
            } catch (Exception ignored) {
                // Offline or the city is unknown: the card keeps the cached reading, or none.
            }
        });
    }

    private static JSONObject json(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            return new JSONObject(buf.toString("UTF-8"));
        } finally {
            con.disconnect();
        }
    }

    /** WMO weather code to a short label with an emoji. */
    static String condition(int code, boolean day) {
        if (code == 0) return day ? "☀️  Clear" : "🌙  Clear";
        if (code <= 2) return day ? "🌤  Partly cloudy" : "☁️  Partly cloudy";
        if (code == 3) return "☁️  Cloudy";
        if (code == 45 || code == 48) return "🌫  Fog";
        if (code >= 51 && code <= 57) return "🌦  Drizzle";
        if (code >= 61 && code <= 67) return "🌧  Rain";
        if (code >= 71 && code <= 77) return "❄️  Snow";
        if (code >= 80 && code <= 82) return "🌦  Showers";
        if (code >= 95) return "⛈  Thunderstorm";
        return "🌡  —";
    }
}
