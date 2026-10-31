package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Poster bitmaps from assets/posters/<slug>.jpg (320x480), decoded off the main thread and kept in a small
 * memory cache. A title without a poster gets a typographic one in its first genre's colours.
 */
final class Posters {
    private Posters() {}

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // ~20 MB: about 30 posters at 320x480, enough for a screen and its neighbours.
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(20 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    interface Callback { void done(Bitmap b); }

    static void into(ImageView v, Catalog.Title t) {
        v.setTag(R.id.poster_tag, t.slug);
        Bitmap hit = CACHE.get(t.slug);
        if (hit != null) {
            v.setImageBitmap(hit);
            return;
        }
        v.setImageDrawable(null);
        load(v.getContext(), t, b -> {
            if (t.slug.equals(v.getTag(R.id.poster_tag))) {
                v.setAlpha(0f);
                v.setImageBitmap(b);
                v.animate().alpha(1f).setDuration(220).start();
            }
        });
    }

    static void load(Context c, Catalog.Title t, Callback cb) {
        Bitmap hit = CACHE.get(t.slug);
        if (hit != null) {
            cb.done(hit);
            return;
        }
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            Bitmap b = decode(app, t);
            if (b == null) b = typographic(app, t);
            CACHE.put(t.slug, b);
            Bitmap fb = b;
            MAIN.post(() -> cb.done(fb));
        });
    }

    /** A few pixels wide and stretched over the screen: reads as a heavy blur without the cost of one. */
    static Bitmap backdrop(Bitmap poster) {
        return Bitmap.createScaledBitmap(poster, 12, 18, true);
    }

    private static Bitmap decode(Context c, Catalog.Title t) {
        try (InputStream in = c.getAssets().open("posters/" + t.slug + ".jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    static int genreColor(String g) {
        switch (g) {
            case "comedy": return 0xFFFFB020;
            case "scifi": return 0xFF00D1FF;
            case "action": return 0xFFFF4D2E;
            case "thriller": return 0xFF8B5CF6;
            case "crime": return 0xFFE11D48;
            case "romance": return 0xFFFF6FAE;
            default: return 0xFF64748B;
        }
    }

    private static Bitmap typographic(Context c, Catalog.Title t) {
        int w = 320, h = 480;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        int accent = genreColor(t.genres.length > 0 ? t.genres[0] : "");
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF0B0E16, 0xFF05060A, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.3f, h * 0.25f, w * 0.9f, (accent & 0xFFFFFF) | 0xAA000000, 0, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(44);
        Typeface tf = Ui.font(c, R.font.montserrat_extrabold);
        tp.setTypeface(tf);
        StaticLayout l = StaticLayout.Builder.obtain(t.name.toUpperCase(), 0, t.name.length(), tp, w - 48)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 0.92f).build();
        cv.save();
        cv.translate(24, h - 60 - l.getHeight());
        l.draw(cv);
        cv.restore();
        tp.setTextSize(18);
        tp.setTypeface(Ui.font(c, R.font.montserrat_medium));
        tp.setColor(0xB3FFFFFF);
        tp.setLetterSpacing(0.2f);
        cv.drawText(String.valueOf(t.year), 24, h - 28, tp);
        return b;
    }
}
