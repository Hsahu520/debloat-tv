package com.gathin.tvhome;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns what was said (English, Hindi or Hinglish) into one action: play a title, open an app, search an app,
 * or suggest titles for a mood. Rules and the catalog only, so it works offline and costs nothing.
 *
 *   "Panchayat chala do"            -> play Panchayat on Prime Video
 *   "play stranger things"          -> play Stranger Things on Netflix
 *   "kuch funny dikhao"             -> comedy picks
 *   "hindi thriller suggest karo"   -> Hindi thriller picks
 *   "open hotstar" / "netflix kholo"-> open the app
 *   "search Lapata Ladies on prime" -> search Prime Video
 */
final class Brain {

    static final class Answer {
        String say;                      // spoken and shown
        Catalog.Title title;             // play this
        String platform;                 // search or open on this platform
        String query;                    // search phrase
        boolean openApp;                 // just open the platform's app
        List<Catalog.Title> picks;       // suggestions to show
        List<String> choices;            // platforms to choose from for a search
    }

    private static final Map<String, String[]> GENRE_WORDS = new LinkedHashMap<>();
    static {
        GENRE_WORDS.put("comedy", new String[]{"comedy", "comedies", "funny", "funniest", "hasi", "hasna", "hasne", "hansi", "mazedar", "mazedaar", "light", "fun", "हंसी", "कॉमेडी", "फनी"});
        GENRE_WORDS.put("scifi", new String[]{"sci fi", "scifi", "science fiction", "space", "future", "alien", "aliens", "robot", "time travel", "साइंस"});
        GENRE_WORDS.put("action", new String[]{"action", "fight", "fighting", "maar dhaad", "maardhaad", "dhamaka", "explosive", "एक्शन"});
        GENRE_WORDS.put("thriller", new String[]{"thriller", "thrilling", "suspense", "suspenseful", "edge of", "रोमांचक", "थ्रिलर"});
        GENRE_WORDS.put("crime", new String[]{"crime", "murder", "detective", "police", "gangster", "mafia", "investigation", "क्राइम"});
        GENRE_WORDS.put("romance", new String[]{"romance", "romantic", "love story", "pyaar", "pyar", "ishq", "date night", "रोमांटिक", "प्यार"});
    }

    private static final String[][] PLATFORM_WORDS = {
            {"netflix", "netflix", "नेटफ्लिक्स"},
            {"prime", "prime video", "amazon prime", "amazon", "prime", "प्राइम"},
            {"hotstar", "jiohotstar", "jio hotstar", "hotstar", "disney", "हॉटस्टार"},
            {"youtube", "youtube", "you tube", "यूट्यूब"},
            {"sonyliv", "sony liv", "sonyliv", "sony"},
    };

    private static final String[] PLAY_WORDS = {"play", "chala do", "chalao", "chala", "laga do", "lagao", "laga", "start", "watch",
            "dekhna hai", "dekhna", "dekhte", "resume", "चलाओ", "चला दो", "लगाओ"};
    private static final String[] OPEN_WORDS = {"open", "kholo", "khol do", "khol", "launch", "खोलो"};
    private static final String[] SEARCH_WORDS = {"search", "find", "dhundo", "dhoondo", "khojo", "सर्च"};
    private static final String[] SUGGEST_WORDS = {"suggest", "recommend", "kuch", "something", "dikhao", "dikha do", "what should",
            "kya dekhu", "kya dekhen", "kya dekhe", "show me", "bored", "कुछ", "दिखाओ"};
    private static final String[] FILLER = {"please", "plz", "jarvis", "hey", "ok", "okay", "mujhe", "hume", "humein", "mere liye",
            "for me", "on", "par", "pe", "mein", "me", "in", "the", "a", "an", "karo", "kar do", "do", "yaar", "bhai", "now", "abhi",
            "series", "movie", "film", "show", "web series", "episode", "wala", "wali", "vala", "vali", "ko", "ka", "ki", "ke"};

    private final Context c;
    private final Taste taste;

    Brain(Context c) {
        this.c = c;
        taste = new Taste(c);
    }

    Answer think(String heard) {
        Answer a = new Answer();
        String q = " " + Catalog.norm(heard).replaceAll("[?!.,'\"]", " ").replaceAll("\\s+", " ").trim() + " ";

        String platform = null, platformWord = null;
        for (String[] p : PLATFORM_WORDS) {
            for (int i = 1; i < p.length && platform == null; i++) {
                if (q.contains(" " + p[i] + " ")) {
                    platform = p[0];
                    platformWord = p[i];
                }
            }
        }
        String genre = null;
        for (Map.Entry<String, String[]> e : GENRE_WORDS.entrySet()) {
            for (String w : e.getValue()) if (genre == null && q.contains(" " + w + " ") || genre == null && q.contains(w) && w.length() > 5) genre = e.getKey();
        }
        String lang = q.contains(" hindi ") || q.contains(" desi ") || q.contains(" bollywood ") || q.contains("हिंदी") ? "hi"
                : q.contains(" english ") || q.contains(" hollywood ") || q.contains(" angrezi ") ? "en" : null;
        boolean play = has(q, PLAY_WORDS), open = has(q, OPEN_WORDS), search = has(q, SEARCH_WORDS), suggest = has(q, SUGGEST_WORDS);

        // What is left once the command words are gone: the name of something.
        String rest = q;
        if (platformWord != null) rest = rest.replace(" " + platformWord + " ", " ");
        for (String[] list : new String[][]{PLAY_WORDS, OPEN_WORDS, SEARCH_WORDS}) {
            for (String w : list) rest = rest.replace(" " + w + " ", " ");
        }
        String forMatch = rest.replaceAll("\\s+", " ").trim();   // keeps "the", so "The Family Man" still matches
        for (String w : FILLER) rest = rest.replace(" " + w + " ", " ");
        rest = rest.replaceAll("\\s+", " ").trim();

        if (open && platform != null && rest.isEmpty()) {
            a.openApp = true;
            a.platform = platform;
            a.say = "Opening " + Catalog.platformLabel(platform) + ".";
            return a;
        }

        Catalog.Title t = Catalog.match(c, forMatch.isEmpty() ? q : forMatch);
        if (t != null && !search) {
            a.title = t;
            a.platform = platform != null ? platform : t.platform;
            a.say = "Playing " + t.name + " on " + Catalog.platformLabel(a.platform) + ".";
            return a;
        }

        if (genre != null || lang != null || (suggest && rest.length() < 3)) {
            List<Catalog.Title> picks = new ArrayList<>();
            List<Catalog.Title> pool = taste.ranked(genre, true);
            if (lang == null) {   // mix Hindi and English, best first in each
                List<Catalog.Title> hi = new ArrayList<>(), en = new ArrayList<>();
                for (Catalog.Title x : pool) ("hi".equals(x.lang) ? hi : en).add(x);
                pool = new ArrayList<>();
                for (int i = 0; i < Math.max(hi.size(), en.size()); i++) {
                    if (i < hi.size()) pool.add(hi.get(i));
                    if (i < en.size()) pool.add(en.get(i));
                }
            }
            for (Catalog.Title x : pool) {
                if (lang != null && !lang.equals(x.lang)) continue;
                if (platform != null && !platform.equals(x.platform)) continue;
                picks.add(x);
                if (picks.size() == 6) break;
            }
            if (!picks.isEmpty()) {
                a.picks = picks;
                StringBuilder s = new StringBuilder("Here are some ");
                if (lang != null) s.append("hi".equals(lang) ? "Hindi " : "English ");
                s.append(genre != null ? Catalog.genreLabel(genre).toLowerCase() + " " : "").append("picks for you");
                if (platform != null) s.append(" on ").append(Catalog.platformLabel(platform));
                a.say = s.append(".").toString();
                return a;
            }
        }

        if (!rest.isEmpty() && (play || search || platform != null)) {
            a.query = rest;
            if (platform != null) {
                a.platform = platform;
                a.say = "Searching " + Catalog.platformLabel(platform) + " for " + rest + ".";
            } else {
                a.choices = new ArrayList<>();
                a.choices.add("netflix");
                a.choices.add("prime");
                a.choices.add("hotstar");
                a.choices.add("youtube");
                a.say = "Where should I look for " + rest + "?";
            }
            return a;
        }

        if (open && platform == null && t != null) {
            a.title = t;
            a.platform = t.platform;
            a.say = "Playing " + t.name + " on " + Catalog.platformLabel(t.platform) + ".";
            return a;
        }

        a.say = "Sorry, I didn't get that. Try \"Play Panchayat\" or \"kuch funny dikhao\".";
        return a;
    }

    private static boolean has(String q, String[] words) {
        for (String w : words) if (q.contains(" " + w + " ")) return true;
        return false;
    }
}
