package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A game screen drawn on its own thread (like the Jarvis HUD), so a busy TV never makes the remote feel laggy.
 * Keys arrive on the UI thread and are queued for the game thread; update(dt) and render(canvas) run there.
 */
abstract class GameSurface extends SurfaceView implements SurfaceHolder.Callback {

    protected final float d;
    private final ConcurrentLinkedQueue<Integer> keys = new ConcurrentLinkedQueue<>();
    private Thread thread;
    private volatile boolean running;
    protected int W, H;

    GameSurface(Context c) {
        super(c);
        d = Ui.density(c);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    /** From the activity's onKeyDown. */
    void key(int keyCode) { keys.add(keyCode); }

    protected abstract void onKey(int keyCode);

    protected abstract void update(float dt);

    protected abstract void render(Canvas c);

    protected void onSize(int w, int h) {}

    protected SharedPreferences prefs() { return getContext().getSharedPreferences("games", Context.MODE_PRIVATE); }

    @Override public void surfaceCreated(SurfaceHolder h) {
        running = true;
        thread = new Thread(() -> loop(h), "game");
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

    private void loop(SurfaceHolder holder) {
        long last = SystemClock.uptimeMillis();
        while (running) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - last) / 1000f);
            last = now;
            for (Integer k; (k = keys.poll()) != null; ) onKey(k);
            update(dt);
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
                if (c != null) {
                    if (c.getWidth() != W || c.getHeight() != H) {
                        W = c.getWidth();
                        H = c.getHeight();
                        onSize(W, H);
                    }
                    render(c);
                }
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
            SystemClock.sleep(Math.max(2, 28 - spent));
        }
    }
}
