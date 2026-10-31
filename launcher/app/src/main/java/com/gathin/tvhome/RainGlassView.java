package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * Launcher 2's wallpaper: a night window in the rain. Out-of-focus city lights (soft bokeh) behind wet glass with
 * droplets and a few running streaks. Painted once into a bitmap at the screen's size, so it costs nothing per frame.
 */
final class RainGlassView extends View {

    private Bitmap art;
    private final String asset;

    RainGlassView(Context c) { this(c, "home2/screensaver.jpg"); }

    /** With another photo from assets, e.g. the screensaver's. */
    RainGlassView(Context c, String asset) {
        super(c);
        this.asset = asset;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w == 0 || h == 0) return;
        if (art != null) art.recycle();
        art = photo(getContext(), asset, w, h);
        if (art == null) art = paint(w, h, Ui.density(getContext()));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (art != null) c.drawBitmap(art, 0, 0, null);
    }

    /**
     * A photo from assets (the screensaver's is assets/home2/screensaver.jpg), scaled to cover the screen and dimmed a little so the
     * glass cards and their white text stay readable over bright greens. Null when there is none.
     */
    private static Bitmap photo(Context ctx, String asset, int w, int h) {
        Bitmap src;
        try (java.io.InputStream in = ctx.getAssets().open(asset)) {
            src = android.graphics.BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
        if (src == null) return null;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        float dw = src.getWidth() * scale, dh = src.getHeight() * scale;
        c.drawBitmap(src, null, new android.graphics.RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        Paint p = new Paint();
        p.setColor(0x47000000);
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0x8C000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.55f, w, h, p);
        return b;
    }

    private static Bitmap paint(int w, int h, float d) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        Random r = new Random(7);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Dusk sky to dark street.
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF0B1C2C, 0xFF14324A, 0xFF1B2A3A, 0xFF0A0F18},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Bokeh: big soft discs of warm streetlight and cool signage, out of focus behind the glass.
        int[] warm = {0xFFFFB45C, 0xFFFF8A3D, 0xFFFFD27A, 0xFFFF6B6B};
        int[] cool = {0xFF4FC3F7, 0xFF7C8CFF, 0xFF5EEAD4, 0xFFB388FF};
        for (int i = 0; i < 46; i++) {
            float x = r.nextFloat() * w, y = h * (0.25f + r.nextFloat() * 0.7f);
            float rad = (18 + r.nextFloat() * 70) * d;
            int col = (r.nextFloat() < 0.65f ? warm : cool)[r.nextInt(4)];
            int a = 40 + r.nextInt(70);
            p.setShader(new RadialGradient(x, y, rad, new int[]{(a << 24) | (col & 0xFFFFFF), ((a / 2) << 24) | (col & 0xFFFFFF), 0},
                    new float[]{0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        p.setShader(null);

        // Fog on the glass: a light milky veil, thicker at the top.
        p.setShader(new LinearGradient(0, 0, 0, h, 0x33C8D8E8, 0x0AC8D8E8, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Running streaks: thin clear trails where drops slid down.
        Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
        streak.setStyle(Paint.Style.STROKE);
        streak.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 26; i++) {
            float x = r.nextFloat() * w, y0 = r.nextFloat() * h * 0.6f, len = (60 + r.nextFloat() * 260) * d;
            streak.setStrokeWidth((1.2f + r.nextFloat() * 2.2f) * d);
            streak.setShader(new LinearGradient(x, y0, x, y0 + len, 0x00FFFFFF, 0x2EFFFFFF, Shader.TileMode.CLAMP));
            float wobble = (r.nextFloat() - 0.5f) * 10 * d;
            c.drawLine(x, y0, x + wobble, y0 + len, streak);
        }

        // Droplets: a dark rim at the bottom, a lens of lighter colour, a tiny specular highlight top-left.
        for (int i = 0; i < 900; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            float rad = (float) (Math.pow(r.nextFloat(), 3) * 9 + 1.2) * d;
            float ry = rad * (1.05f + r.nextFloat() * 0.25f);
            p.setColor(0x38000000);
            c.drawOval(x - rad, y - ry + rad * 0.25f, x + rad, y + ry + rad * 0.25f, p);
            p.setShader(new RadialGradient(x, y + ry * 0.3f, rad * 1.2f, 0x40FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP));
            c.drawOval(x - rad, y - ry, x + rad, y + ry, p);
            p.setShader(null);
            if (rad > 2.5f * d) {
                p.setColor(0xB3FFFFFF);
                c.drawCircle(x - rad * 0.35f, y - ry * 0.4f, Math.max(0.8f * d, rad * 0.18f), p);
            }
        }

        // Darken the bottom so the dock reads.
        p.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.6f, w, h, p);
        return b;
    }
}
