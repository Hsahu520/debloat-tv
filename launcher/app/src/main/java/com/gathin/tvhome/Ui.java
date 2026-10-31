package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Shared look for the For You, Swipe, Assistant and Games screens: liquid glass panels, the launcher's
 * fonts, and springy focus. Everything is drawn with gradients and strokes, no blur, so it stays cheap
 * on the TV's 1.8 GB MediaTek.
 */
final class Ui {
    private Ui() {}

    static float density(Context c) { return c.getResources().getDisplayMetrics().density; }

    static int dp(Context c, float v) { return Math.round(v * density(c)); }

    static Typeface font(Context c, int res) {
        try {
            Typeface t = c.getResources().getFont(res);
            return t != null ? t : Typeface.DEFAULT;
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    static TextView text(Context c, int fontRes, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(font(c, fontRes));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A small caps kicker, like the greeting on Home. */
    static TextView kicker(Context c, String s) {
        TextView t = text(c, R.font.montserrat_bold, 13, 0xB3FFFFFF);
        t.setLetterSpacing(0.24f);
        t.setAllCaps(true);
        t.setText(s);
        return t;
    }

    /** A focusable glass button: frosted at rest, bright white glass with dark text when focused. */
    static TextView glassButton(Context c, String label) {
        TextView b = text(c, R.font.montserrat_bold, 15, 0xFFFFFFFF);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(c, 22), 0, dp(c, 22), 0);
        b.setMinHeight(dp(c, 44));
        LiquidGlass bg = new LiquidGlass(c, 22);
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, f) -> {
            bg.setFocused(f);
            ((TextView) v).setTextColor(f ? 0xFF0B0E16 : 0xFFFFFFFF);
            springScale(v, f ? 1.07f : 1f);
        });
        return b;
    }

    /** Springs a view's scale to the target, the same liquid feel as the Home highlight. */
    static void springScale(View v, float to) {
        for (DynamicAnimation.ViewProperty p : new DynamicAnimation.ViewProperty[]{
                DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y}) {
            SpringAnimation s = new SpringAnimation(v, p, to);
            s.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.62f);
            s.start();
        }
    }

    static void spring(View v, DynamicAnimation.ViewProperty p, float to, float stiffness, float damping) {
        SpringAnimation s = new SpringAnimation(v, p, to);
        s.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        s.start();
    }

    /**
     * iOS 26 style liquid glass: a translucent body that is brighter at the top, a specular sheen across
     * the upper third, a rim that catches light top-left and fades bottom-right, and a soft inner shadow
     * at the bottom edge. Focused, it turns into bright milky glass.
     */
    static final class LiquidGlass extends Drawable {
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float radius;
        private final float d;
        private boolean focused;
        private int tint = 0xFFFFFF;
        private float strength = 1f;

        LiquidGlass(Context c, float radiusDp) {
            d = density(c);
            radius = radiusDp * d;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.2f * d);
            shade.setStyle(Paint.Style.STROKE);
            shade.setStrokeWidth(2f * d);
        }

        /** Tints the glass (0xRRGGBB), for accent panels such as the assistant's answer card. */
        LiquidGlass tint(int rgb, float strength) {
            this.tint = rgb & 0xFFFFFF;
            this.strength = strength;
            onBoundsChange(getBounds());
            invalidateSelf();
            return this;
        }

        void setFocused(boolean f) {
            if (focused == f) return;
            focused = f;
            onBoundsChange(getBounds());
            invalidateSelf();
        }

        private int a(int alpha) { return (Math.min(255, Math.round(alpha * strength)) << 24) | tint; }

        @Override
        protected void onBoundsChange(@NonNull android.graphics.Rect b) {
            float h = Math.max(1, b.height());
            if (focused) {
                body.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                        0xF7FFFFFF, 0xE6EEF2FF, Shader.TileMode.CLAMP));
            } else {
                body.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                        a(0x3A), a(0x12), Shader.TileMode.CLAMP));
            }
            sheen.setShader(new LinearGradient(0, b.top, 0, b.top + h * 0.48f,
                    focused ? 0x66FFFFFF : 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                    new int[]{focused ? 0xFFFFFFFF : 0x99FFFFFF, 0x26FFFFFF, focused ? 0xCCFFFFFF : 0x40FFFFFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            shade.setShader(new LinearGradient(0, b.bottom - h * 0.3f, 0, b.bottom,
                    0x00000000, focused ? 0x14000000 : 0x33000000, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas c) {
            r.set(getBounds());
            float rad = Math.min(radius, r.height() / 2f);
            c.drawRoundRect(r, rad, rad, body);
            RectF top = new RectF(r.left + d, r.top + d, r.right - d, r.top + r.height() * 0.5f);
            c.drawRoundRect(top, rad, rad, sheen);
            RectF in = new RectF(r.left + d, r.top + d, r.right - d, r.bottom - d);
            c.drawRoundRect(in, rad, rad, shade);
            r.inset(0.6f * d, 0.6f * d);
            c.drawRoundRect(r, rad, rad, rim);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
