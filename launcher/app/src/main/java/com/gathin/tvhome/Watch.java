package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Opens a title in the app that streams it. Netflix, Prime Video, JioHotstar and YouTube take a search deep link,
 * so the title is one press away; anything else (or a deep link the app refuses) just opens the app.
 */
final class Watch {
    private Watch() {}

    static final String NETFLIX = "com.netflix.ninja";
    static final String PRIME = "com.amazon.amazonvideo.livingroom";
    static final String HOTSTAR = "in.startv.hotstar";
    static final String SONYLIV = "com.sonyliv";
    static final String YOUTUBE = "com.google.android.youtube.tv";

    static String pkg(String platform) {
        switch (platform) {
            case "netflix": return NETFLIX;
            case "prime": return PRIME;
            case "hotstar": return HOTSTAR;
            case "sonyliv": return SONYLIV;
            case "youtube": return YOUTUBE;
            default: return null;
        }
    }

    static boolean open(Context c, Catalog.Title t) { return search(c, t.platform, t.name); }

    /** Search for a phrase inside a platform's app; false when the app is not installed. */
    static boolean search(Context c, String platform, String query) {
        String pkg = pkg(platform);
        if (pkg == null) return false;
        String q = Uri.encode(query);
        String uri = null;
        switch (platform) {
            case "netflix": uri = "nflx://www.netflix.com/search?q=" + q; break;
            case "prime": uri = "https://app.primevideo.com/search?phrase=" + q; break;
            case "hotstar": uri = "https://www.hotstar.com/in/explore?search_query=" + q; break;
            case "youtube": uri = "https://www.youtube.com/results?search_query=" + q; break;
        }
        if (uri != null) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // fall through to a plain launch
            }
        }
        return launch(c, pkg);
    }

    static boolean launch(Context c, String pkg) {
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
