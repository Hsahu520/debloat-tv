package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Synthwave Run: a neon car on an endless retro grid towards a striped sunset. Left/Right switch lanes, dodge the
 * pyramids, grab the orbs. It gets faster the longer you last.
 */
final class SynthwaveGame extends GameSurface {

    private static final float CAM = 1.2f;          // camera height in world units
    private static final float LANE = 1.6f;         // lane width in world units
    private static final float FAR = 60f;
    private static final float CAR_Z = 3.4f;       // how far ahead of the camera the car sits

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final Path path = new Path();
    private Shader sky, ground, sun;
    private float horizon;
    private float[] stars;
    private Path mountainsFar, mountainsNear;

    private enum Phase { READY, RUN, OVER }
    private Phase phase = Phase.READY;
    private int lane;                 // -1, 0, 1
    private float carX;               // smoothed lane position
    private float speed, travelled, spawnIn, gridOffset, flash;
    private int score, best, orbs;

    private static final class Thing {
        float z;
        int lane;
        boolean orb, hit;
    }
    private final List<Thing> things = new ArrayList<>();

    SynthwaveGame(Context c) {
        super(c);
        Typeface bold = Ui.font(c, R.font.montserrat_extrabold);
        text.setTypeface(bold);
        p.setStyle(Paint.Style.FILL);
        best = prefs().getInt("synth_best", 0);
    }

    @Override
    protected void onSize(int w, int h) {
        horizon = h * 0.52f;
        sky = new LinearGradient(0, 0, 0, horizon, new int[]{0xFF07001A, 0xFF2B0A4A, 0xFF8A1C7C, 0xFFFF4D8D},
                new float[]{0f, 0.45f, 0.82f, 1f}, Shader.TileMode.CLAMP);
        ground = new LinearGradient(0, horizon, 0, h, 0xFF16002E, 0xFF04000C, Shader.TileMode.CLAMP);
        float sr = h * 0.2f;
        sun = new LinearGradient(0, horizon - sr, 0, horizon, 0xFFFFE45E, 0xFFFF3D7F, Shader.TileMode.CLAMP);
        stars = new float[160];
        for (int i = 0; i < stars.length; i += 2) {
            stars[i] = rnd.nextFloat() * w;
            stars[i + 1] = rnd.nextFloat() * horizon * 0.8f;
        }
        mountainsFar = ridge(w, h, 0.10f, 9, 11);
        mountainsNear = ridge(w, h, 0.06f, 14, 23);
    }

    private Path ridge(int w, int h, float height, int peaks, long seed) {
        Random r = new Random(seed);
        Path m = new Path();
        m.moveTo(0, horizon);
        float step = w / (float) peaks;
        for (int i = 0; i <= peaks; i++) {
            float x = i * step;
            float centre = Math.abs(x - w / 2f) / (w / 2f);        // lower towards the sun in the middle
            float y = horizon - h * height * (0.35f + r.nextFloat() * 0.65f) * (0.3f + centre);
            m.lineTo(x - step / 2f, horizon - h * height * 0.15f * r.nextFloat());
            m.lineTo(x, y);
        }
        m.lineTo(w, horizon);
        m.close();
        return m;
    }

    private void reset() {
        things.clear();
        lane = 0;
        carX = 0;
        speed = 14f;
        travelled = 0;
        spawnIn = 1.2f;
        score = 0;
        orbs = 0;
        phase = Phase.RUN;
    }

    @Override
    protected void onKey(int k) {
        if (k == KeyEvent.KEYCODE_DPAD_LEFT && phase == Phase.RUN) lane = Math.max(-1, lane - 1);
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && phase == Phase.RUN) lane = Math.min(1, lane + 1);
        else if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) && phase != Phase.RUN) reset();
    }

    @Override
    protected void update(float dt) {
        gridOffset = (gridOffset + dt * (phase == Phase.RUN ? speed : 4f)) % 2f;
        carX += (lane - carX) * Math.min(1f, dt * 12f);
        flash = Math.max(0, flash - dt * 2.5f);
        if (phase != Phase.RUN) return;
        speed = Math.min(42f, speed + dt * 0.55f);
        travelled += speed * dt;
        score = Math.round(travelled) + orbs * 50;
        spawnIn -= dt;
        if (spawnIn <= 0) {
            spawnIn = Math.max(0.35f, 1.1f - speed / 60f) * (0.7f + rnd.nextFloat() * 0.6f);
            Thing t = new Thing();
            t.z = FAR;
            t.lane = rnd.nextInt(3) - 1;
            t.orb = rnd.nextFloat() < 0.28f;
            things.add(t);
            // Sometimes a wall of two, never three: there is always a way through.
            if (!t.orb && rnd.nextFloat() < 0.3f * Math.min(1f, speed / 30f)) {
                Thing u = new Thing();
                u.z = FAR;
                u.lane = t.lane == 1 ? -1 : t.lane + 1;
                things.add(u);
            }
        }
        for (Iterator<Thing> it = things.iterator(); it.hasNext(); ) {
            Thing t = it.next();
            t.z -= speed * dt;
            if (!t.hit && Math.abs(t.z - CAR_Z) < 0.7f && Math.abs(t.lane - carX) < 0.55f) {
                t.hit = true;
                if (t.orb) {
                    orbs++;
                    flash = 0.6f;
                } else {
                    phase = Phase.OVER;
                    if (score > best) {
                        best = score;
                        prefs().edit().putInt("synth_best", best).apply();
                    }
                }
            }
            if (t.z < 0.5f || (t.hit && t.orb)) it.remove();
        }
    }

    /** World (x, z) on the ground to screen; returns the scale at that depth. */
    private float sx(float x, float z) { return W / 2f + x / z * (W * 0.42f); }

    private float sy(float z) { return horizon + CAM / z * (H * 0.55f); }

    @Override
    protected void render(Canvas c) {
        p.setShader(sky);
        c.drawRect(0, 0, W, horizon, p);
        p.setShader(null);
        p.setColor(0xCCFFFFFF);
        for (int i = 0; i < stars.length; i += 2) c.drawCircle(stars[i], stars[i + 1], (i % 7 == 0 ? 2.2f : 1.2f) * d / 2, p);

        // The striped sun.
        float sr = H * 0.2f;
        glow.setShader(new RadialGradient(W / 2f, horizon - sr * 0.4f, sr * 2.2f, 0x66FF3D7F, 0x00FF3D7F, Shader.TileMode.CLAMP));
        c.drawCircle(W / 2f, horizon - sr * 0.4f, sr * 2.2f, glow);
        glow.setShader(null);
        c.save();
        c.clipRect(0, 0, W, horizon);
        p.setShader(sun);
        c.drawCircle(W / 2f, horizon - sr * 0.15f, sr, p);
        p.setShader(null);
        p.setColor(0xFF8A1C7C);
        for (int i = 0; i < 7; i++) {
            float y = horizon - sr * 0.15f + sr * (0.05f + i * 0.13f) - sr * 0.55f;
            float hgt = 2 * d + i * 1.6f * d;
            if (y > horizon - sr * 1.15f) c.drawRect(0, y, W, y + hgt, p);
        }
        c.restore();

        p.setColor(0xFF25063F);
        c.drawPath(mountainsFar, p);
        p.setColor(0xFF12022A);
        c.drawPath(mountainsNear, p);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(2 * d);
        glow.setColor(0xAA3BF0FF);
        c.drawPath(mountainsNear, glow);

        p.setShader(ground);
        c.drawRect(0, horizon, W, H, p);
        p.setShader(null);

        // Perspective grid: lines across moving towards you, lines along converging on the sun.
        glow.setStrokeWidth(4 * d);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f * d);
        for (float z = 2f - gridOffset; z < FAR; z += 2f) {
            if (z < 0.4f) continue;
            float y = sy(z);
            int a = Math.round(255 * Math.min(1f, 3f / z));
            glow.setColor((a / 4 << 24) | 0xFF2BD6);
            p.setColor((a << 24) | 0xFF2BD6);
            c.drawLine(0, y, W, y, glow);
            c.drawLine(0, y, W, y, p);
        }
        for (int i = -12; i <= 12; i++) {
            float x = (i + 0.5f) * LANE;
            float x0 = sx(x, 0.45f), y0 = sy(0.45f), x1 = sx(x, FAR), y1 = sy(FAR);
            glow.setColor(0x33FF2BD6);
            p.setColor(0xCCFF2BD6);
            c.drawLine(x0, y0, x1, y1, glow);
            c.drawLine(x0, y0, x1, y1, p);
        }
        p.setStyle(Paint.Style.FILL);

        // Obstacles and orbs, far to near.
        things.sort((a, b) -> Float.compare(b.z, a.z));
        for (Thing t : things) {
            if (t.z < CAR_Z - 0.2f) continue;   // passed: drawn after the car
            float x = sx(t.lane * LANE, t.z), y = sy(t.z);
            float s = 1f / t.z * (W * 0.42f);              // pixels per world unit at that depth
            if (t.orb) {
                float r = 0.32f * s, cy = y - 0.5f * s;
                glow.setStyle(Paint.Style.FILL);
                glow.setShader(new RadialGradient(x, cy, r * 2.4f, 0x8829F0FF, 0x0029F0FF, Shader.TileMode.CLAMP));
                c.drawCircle(x, cy, r * 2.4f, glow);
                glow.setShader(null);
                glow.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFB8FBFF);
                c.drawCircle(x, cy, r, p);
            } else {
                drawPyramid(c, t);
            }
        }

        drawCar(c);
        for (Thing t : things) if (t.z < CAR_Z - 0.2f && t.z >= 0.6f && !t.orb) drawPyramid(c, t);

        if (flash > 0) {
            p.setColor(((int) (flash * 90) << 24) | 0x29F0FF);
            c.drawRect(0, 0, W, H, p);
        }

        // HUD
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(15 * d);
        text.setColor(0xB3FFFFFF);
        text.setLetterSpacing(0.25f);
        c.drawText("SYNTHWAVE RUN", 30 * d, 34 * d, text);
        text.setLetterSpacing(0);
        text.setTextSize(36 * d);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(10 * d, 0, 0, 0xFFFF2BD6);
        c.drawText(String.valueOf(score), 30 * d, 74 * d, text);
        text.setShadowLayer(0, 0, 0, 0);
        text.setTextSize(13 * d);
        text.setColor(0xB3FFFFFF);
        c.drawText("BEST " + best + "    ◆ " + orbs + "    " + Math.round(speed * 7) + " KM/H", 30 * d, 96 * d, text);

        if (phase != Phase.RUN) {
            p.setColor(0x99000000);
            c.drawRect(0, 0, W, H, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(52 * d);
            text.setColor(0xFFFFFFFF);
            text.setShadowLayer(16 * d, 0, 0, 0xFFFF2BD6);
            c.drawText(phase == Phase.READY ? "SYNTHWAVE RUN" : "GAME OVER", W / 2f, H * 0.42f, text);
            text.setShadowLayer(0, 0, 0, 0);
            text.setTextSize(16 * d);
            text.setColor(0xE6FFFFFF);
            text.setLetterSpacing(0.15f);
            String sub = phase == Phase.READY ? "◀ ▶  SWITCH LANES   ·   DODGE ▲   ·   GRAB ◆" : "SCORE " + score + (score >= best && score > 0 ? "  ·  NEW BEST!" : "  ·  BEST " + best);
            c.drawText(sub, W / 2f, H * 0.42f + 44 * d, text);
            text.setColor(0xFF3BF0FF);
            c.drawText("PRESS OK TO " + (phase == Phase.READY ? "START" : "RACE AGAIN"), W / 2f, H * 0.42f + 80 * d, text);
            text.setLetterSpacing(0);
        }
    }

    private void drawPyramid(Canvas c, Thing t) {
        float x = sx(t.lane * LANE, t.z), y = sy(t.z);
        float s = 1f / t.z * (W * 0.42f);
        float hw = 0.62f * s, ht = 1.1f * s;
        path.reset();
        path.moveTo(x - hw, y);
        path.lineTo(x, y - ht);
        path.lineTo(x + hw, y);
        path.close();
        p.setColor(0xFF1B0230);
        c.drawPath(path, p);
        glow.setStyle(Paint.Style.STROKE);
        glow.setColor(0x66FFD23F);
        glow.setStrokeWidth(Math.max(2, 0.12f * s));
        c.drawPath(path, glow);
        glow.setColor(0xFFFFD23F);
        glow.setStrokeWidth(Math.max(1.5f, 0.04f * s));
        c.drawPath(path, glow);
        c.drawLine(x, y - ht, x, y, glow);
    }

    private void drawCar(Canvas c) {
        float z = CAR_Z;
        float x = sx(carX * LANE, z), y = sy(z) + 6 * d;
        float s = 1f / z * (W * 0.42f);
        float w = 0.95f * s, h = 0.42f * s;
        // Under-glow.
        glow.setStyle(Paint.Style.FILL);
        glow.setShader(new RadialGradient(x, y, w * 1.1f, 0x6629F0FF, 0x0029F0FF, Shader.TileMode.CLAMP));
        c.drawOval(x - w * 1.1f, y - h * 0.4f, x + w * 1.1f, y + h * 0.5f, glow);
        glow.setShader(null);
        glow.setStyle(Paint.Style.STROKE);
        // Body: a low wedge seen from behind.
        path.reset();
        path.moveTo(x - w / 2, y);
        path.lineTo(x - w / 2 * 0.92f, y - h * 0.62f);
        path.lineTo(x - w / 2 * 0.55f, y - h);
        path.lineTo(x + w / 2 * 0.55f, y - h);
        path.lineTo(x + w / 2 * 0.92f, y - h * 0.62f);
        path.lineTo(x + w / 2, y);
        path.close();
        p.setColor(0xFF140022);
        c.drawPath(path, p);
        glow.setStrokeWidth(2.4f * d);
        glow.setColor(0xFF29F0FF);
        c.drawPath(path, glow);
        // Tail light bar.
        p.setColor(0xFFFF2B6D);
        c.drawRect(x - w * 0.38f, y - h * 0.5f, x + w * 0.38f, y - h * 0.4f, p);
        glow.setColor(0x88FF2B6D);
        glow.setStrokeWidth(6 * d);
        c.drawLine(x - w * 0.38f, y - h * 0.45f, x + w * 0.38f, y - h * 0.45f, glow);
    }
}
