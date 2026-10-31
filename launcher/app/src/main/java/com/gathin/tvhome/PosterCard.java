package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * A 2:3 poster with rounded corners. Focused, it lifts on a spring and gets a liquid glass rim with a light
 * sweep across its top, like a tvOS poster tilting towards you.
 */
final class PosterCard extends FrameLayout {

    final ImageView image;
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float radius;
    private boolean lit;
    Catalog.Title title;

    PosterCard(Context c, int widthDp) {
        super(c);
        float d = Ui.density(c);
        radius = 12 * d;
        int w = Math.round(widthDp * d), h = Math.round(widthDp * 1.5f * d);
        setLayoutParams(new LayoutParams(w, h));
        setFocusable(true);
        setClickable(true);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        setBackgroundColor(0xFF11141C);
        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2f * d);
        setWillNotDraw(false);
        setElevation(2 * d);
    }

    void bind(Catalog.Title t) {
        title = t;
        setContentDescription(t.name);
        Posters.into(image, t);
    }

    void setLit(boolean on) {
        lit = on;
        Ui.springScale(this, on ? 1.12f : 1f);
        setElevation((on ? 16 : 2) * Ui.density(getContext()));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rim.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFFFFFFFF, 0x55FFFFFF, 0xCCFFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, 0, w * 0.6f, h * 0.45f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (!lit) return;
        c.drawRect(0, 0, getWidth(), getHeight(), sheen);
        float in = rim.getStrokeWidth() / 2f;
        r.set(in, in, getWidth() - in, getHeight() - in);
        c.drawRoundRect(r, radius, radius, rim);
    }
}
