package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * The Jarvis HUD and the screen's backdrop, drawn on its own thread into a full-screen SurfaceView that sits
 * behind the text and posters. An arc-reactor core inside counter-rotating rings, tick marks and segmented arcs,
 * with a waveform ring that breathes with the microphone level. Colour and speed follow the state: idle cyan,
 * listening bright cyan, thinking amber and fast, answering in the platform's colour.
 *
 * Off the UI thread on purpose: on this TV a frame takes longer than a vsync, and drawing it on the main thread
 * starved everything else (key presses, text updates, new intents).
 */
final class JarvisView extends SurfaceView implements SurfaceHolder.Callback {

    enum State { IDLE, LISTENING, THINKING, ANSWER, ERROR }

    private final float d;
    private volatile int targetColor = 0xFF27C4FF;
    private volatile State state = State.IDLE;
    private volatile float levelTarget;
    private volatile float speedTarget = 0.7f;
    private volatile float targetScale = 1f, targetShift;
    private volatile float hudTop, hudSize;

    private Thread thread;
    private volatile boolean running;

    JarvisView(Context c) {
        super(c);
        d = Ui.density(c);
        getHolder().addCallback(this);
    }

    /** Where the HUD sits on screen, in px: top edge and the square's size. */
    void place(float top, float size) {
        hudTop = top;
        hudSize = size;
    }

    /** Compact: smaller and lifted, to make room for posters underneath. */
    void setCompact(boolean on, float liftPx) {
        targetScale = on ? 0.62f : 1f;
        targetShift = on ? -liftPx : 0f;
    }

    void setState(State s, int accent) {
        state = s;
        switch (s) {
            case LISTENING: targetColor = 0xFF3BF0FF; speedTarget = 1.6f; break;
            case THINKING: targetColor = 0xFFFFB020; speedTarget = 4.5f; break;
            case ANSWER: targetColor = accent != 0 ? accent : 0xFF3DDC97; speedTarget = 1.2f; break;
            case ERROR: targetColor = 0xFFFF4D6D; speedTarget = 0.6f; break;
            default: targetColor = 0xFF27C4FF; speedTarget = 0.7f; break;
        }
        if (s != State.LISTENING) levelTarget = s == State.THINKING ? 0.35f : 0f;
    }

    /** RMS from the recogniser, roughly -2..10 dB. */
    void setRms(float db) {
        if (state != State.LISTENING) return;
        levelTarget = Math.max(0f, Math.min(1f, (db + 2f) / 10f));
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        running = true;
        thread = new Thread(new Renderer(h), "jarvis-hud");
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        thread.start();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hgt) {}

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
    }

    /** Everything the render thread owns. */
    private final class Renderer implements Runnable {
        private final SurfaceHolder holder;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path wave = new Path();
        private Bitmap ticks, segments, heavy, dashes, sweep;
        private int maskSize;
        private RadialGradient bloomShader, coreShader, vignette;
        private int shaderColor, shaderLevel = -1, vigW, vigH;
        private float color[] = {0x27, 0xC4, 0xFF};
        private float level, speed = 0.7f, angle, scale = 1f, shift;

        Renderer(SurfaceHolder h) {
            holder = h;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeCap(Paint.Cap.ROUND);
        }

        private int col() { return Color.rgb(Math.round(color[0]), Math.round(color[1]), Math.round(color[2])); }

        private int a(int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (col() & 0xFFFFFF); }

        @Override
        public void run() {
            long last = SystemClock.uptimeMillis();
            while (running) {
                long now = SystemClock.uptimeMillis();
                float dt = Math.min(0.05f, (now - last) / 1000f);
                last = now;
                step(dt);
                Canvas c = null;
                try {
                    c = holder.lockHardwareCanvas();
                    if (c != null) draw(c, now / 1000f);
                } catch (IllegalStateException | IllegalArgumentException ignored) {
                    // surface going away
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c);
                        } catch (IllegalStateException | IllegalArgumentException ignored) {
                        }
                    }
                }
                long spent = SystemClock.uptimeMillis() - now;
                SystemClock.sleep(Math.max(2, 33 - spent));   // ~30 fps is plenty for slow rotations
            }
        }

        private void step(float dt) {
            int tc = targetColor;
            float k = Math.min(1f, dt * 6f);
            color[0] += (((tc >> 16) & 0xFF) - color[0]) * k;
            color[1] += (((tc >> 8) & 0xFF) - color[1]) * k;
            color[2] += ((tc & 0xFF) - color[2]) * k;
            speed += (speedTarget - speed) * Math.min(1f, dt * 3f);
            level += (levelTarget - level) * Math.min(1f, dt * 10f);
            angle = (angle + dt * 30f * speed) % 360f;
            // Critically damped glide for the compact move (springs are a main-thread thing).
            scale += (targetScale - scale) * Math.min(1f, dt * 7f);
            shift += (targetShift - shift) * Math.min(1f, dt * 7f);
        }

        private void buildMasks(int size) {
            maskSize = size;
            float cx = size / 2f, cy = size / 2f, R = size / 2f * 0.86f;
            RectF arc = new RectF();

            ticks = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            Canvas c = new Canvas(ticks);
            for (int i = 0; i < 120; i++) {
                float len = i % 10 == 0 ? 14 * d : i % 5 == 0 ? 8 * d : 4 * d;
                stroke.setStrokeWidth(i % 10 == 0 ? 2f * d : 1f * d);
                stroke.setColor(i % 10 == 0 ? 0x96000000 : 0x64000000);
                double th = Math.toRadians(i * 3);
                c.drawLine(cx + (float) Math.cos(th) * R, cy + (float) Math.sin(th) * R,
                        cx + (float) Math.cos(th) * (R - len), cy + (float) Math.sin(th) * (R - len), stroke);
            }

            segments = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            c = new Canvas(segments);
            float r2 = R * 0.86f;
            arc.set(cx - r2, cy - r2, cx + r2, cy + r2);
            for (int i = 0; i < 6; i++) {
                float start = i * 60f, sw = i % 2 == 0 ? 42f : 24f;
                glow.setStrokeWidth(10 * d);
                glow.setColor(0x30000000);
                c.drawArc(arc, start, sw, false, glow);
                stroke.setStrokeWidth(3 * d);
                stroke.setColor(0xCC000000);
                c.drawArc(arc, start, sw, false, stroke);
            }

            heavy = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            c = new Canvas(heavy);
            float r3 = R * 0.74f;
            arc.set(cx - r3, cy - r3, cx + r3, cy + r3);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            for (float[] hv : new float[][]{{0, 110}, {150, 50}, {230, 18}, {262, 70}}) {
                glow.setStrokeWidth(22 * d);
                glow.setColor(0x2E000000);
                c.drawArc(arc, hv[0], hv[1], false, glow);
                stroke.setStrokeWidth(9 * d);
                stroke.setColor(0xE6000000);
                c.drawArc(arc, hv[0], hv[1], false, stroke);
            }
            stroke.setStrokeCap(Paint.Cap.ROUND);

            dashes = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            c = new Canvas(dashes);
            float r4 = R * 0.62f;
            arc.set(cx - r4, cy - r4, cx + r4, cy + r4);
            stroke.setStrokeWidth(1.5f * d);
            stroke.setColor(0x96000000);
            for (int i = 0; i < 36; i++) c.drawArc(arc, i * 10f, 5f, false, stroke);

            sweep = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            c = new Canvas(sweep);
            Paint wedge = new Paint(Paint.ANTI_ALIAS_FLAG);
            float r5 = R * 0.9f;
            arc.set(cx - r5, cy - r5, cx + r5, cy + r5);
            for (int i = 0; i < 40; i++) {
                wedge.setColor(Math.round(90f * i / 40f) << 24);
                c.drawArc(arc, -60 + i * 1.5f, 1.6f, true, wedge);
            }
        }

        private void drawMask(Canvas c, Bitmap b, float cx, float cy, float rotation, int alpha) {
            c.save();
            c.translate(cx, cy);
            c.rotate(rotation);
            c.scale(scale, scale);
            mask.setColor(a(alpha));
            c.drawBitmap(b, -maskSize / 2f, -maskSize / 2f, mask);
            c.restore();
        }

        private void draw(Canvas c, float t) {
            int w = c.getWidth(), h = c.getHeight();
            int size = Math.round(hudSize > 0 ? hudSize : Math.min(w, h) * 0.6f);
            if (ticks == null || size != maskSize) buildMasks(size);

            // Backdrop: deep navy with a soft pool of the state colour around the HUD.
            if (vignette == null || vigW != w || vigH != h) {
                vigW = w;
                vigH = h;
                vignette = new RadialGradient(w / 2f, h * 0.42f, Math.max(w, h) * 0.6f, 0xFF06202A, 0xFF02060A, Shader.TileMode.CLAMP);
            }
            c.drawColor(0, PorterDuff.Mode.CLEAR);
            fill.setShader(vignette);
            c.drawRect(0, 0, w, h, fill);
            fill.setShader(null);

            float cx = w / 2f;
            float cy = hudTop + size / 2f + shift - (1f - scale) * size / 2f;
            float R = size / 2f * 0.86f * scale;

            int lv = Math.round(level * 8);
            int cc = col();
            if (bloomShader == null || shaderColor != cc || shaderLevel != lv) {
                shaderColor = cc;
                shaderLevel = lv;
                bloomShader = new RadialGradient(0, 0, size / 2f * 0.86f * 1.25f, new int[]{a(70 + lv * 8), a(18), 0},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
                float core = size / 2f * 0.86f * (0.3f + lv / 8f * 0.07f);
                coreShader = new RadialGradient(0, 0, core, new int[]{0xFFFFFFFF, a(255), a(120), 0},
                        new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP);
            }
            c.save();
            c.translate(cx, cy);
            c.scale(scale, scale);
            fill.setShader(bloomShader);
            c.drawCircle(0, 0, size / 2f * 0.86f * 1.25f, fill);
            fill.setShader(null);
            c.restore();

            drawMask(c, ticks, cx, cy, -angle * 0.25f, 255);
            drawMask(c, segments, cx, cy, angle * 0.8f, 255);
            drawMask(c, heavy, cx, cy, -angle * 1.4f, 255);
            drawMask(c, dashes, cx, cy, angle * 2.2f, 255);
            State s = state;
            if (s == State.THINKING || s == State.LISTENING) drawMask(c, sweep, cx, cy, angle * 3f, s == State.THINKING ? 255 : 150);

            float r6 = R * 0.5f;
            wave.reset();
            int n = 72;
            for (int i = 0; i <= n; i++) {
                double th = 2 * Math.PI * i / n;
                float amp = (float) (Math.sin(th * 6 + t * 5) * 0.55 + Math.sin(th * 11 - t * 7) * 0.3 + Math.sin(th * 3 + t * 2.3) * 0.15);
                float r = r6 + amp * (4 * d + level * 26 * d) * scale;
                float x = cx + (float) Math.cos(th) * r, y = cy + (float) Math.sin(th) * r;
                if (i == 0) wave.moveTo(x, y);
                else wave.lineTo(x, y);
            }
            stroke.setStrokeWidth(2.2f * d);
            stroke.setColor(a(255));
            c.drawPath(wave, stroke);

            float core = size / 2f * 0.86f * (0.3f + shaderLevel / 8f * 0.07f);
            c.save();
            c.translate(cx, cy);
            c.scale(scale, scale);
            fill.setShader(coreShader);
            c.drawCircle(0, 0, core, fill);
            fill.setShader(null);
            stroke.setStrokeWidth(1.5f * d);
            stroke.setColor(0xCCFFFFFF);
            c.drawCircle(0, 0, core * 0.55f, stroke);
            c.restore();
        }
    }
}
