package com.downinglabs.lyriqwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;

/**
 * Draws the LYRIQ in the owner's paint colour from a traced photo shading map, plus the
 * horizontal battery bar used by the "car" and "bar" widget styles. Everything ends up as
 * a bitmap so it can be handed to RemoteViews and recoloured at runtime.
 */
public final class CarRenderer {
    private CarRenderer() {}

    /** Paint presets Cadillac offered on the LYRIQ. name → ARGB. */
    public static final String[] PAINT_NAMES = {
            "Stellar Black", "Argent Silver", "Crystal White", "Emerald Lake",
            "Opulent Blue", "Radiant Red", "Nimbus Gray", "Celestial Blue", "Deep Sea",
    };
    public static final int[] PAINT_COLORS = {
            0xFF141517, 0xFFB7BABF, 0xFFF1F1EC, 0xFF1F4C3F,
            0xFF1C3D72, 0xFF8E1220, 0xFF6D7075, 0xFF8EA4BA, 0xFF14313F,
    };

    /** Traced-photo shading map (see docs/car-art.md). Grayscale luminance; cached per process. */
    private static Bitmap shadeLayer;

    private static synchronized void loadLayer(Context context) {
        if (shadeLayer != null) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        shadeLayer = BitmapFactory.decodeResource(context.getResources(), R.drawable.car_shade, o);
    }

    /**
     * Tints the traced shading map in the owner's paint: each pixel's grayscale value is
     * multiplied by the paint colour (PorterDuffColorFilter, Mode.MULTIPLY), so near-white
     * pixels (paint highlights) show the full colour and near-black pixels (tires, glass,
     * grille, shadows) stay dark regardless of paint colour — no separate held-out layer
     * needed the way the old diff/gloss/rest stack required.
     */
    public static Bitmap car(Context context, int color, boolean charging, int widthPx, int heightPx) {
        loadLayer(context);
        Bitmap bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float iw = shadeLayer.getWidth(), ih = shadeLayer.getHeight();
        float s = Math.min(widthPx / iw, heightPx / ih);
        RectF dst = new RectF(0, 0, iw * s, ih * s);
        dst.offset((widthPx - dst.width()) / 2f, (heightPx - dst.height()) / 2f);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        c.drawBitmap(shadeLayer, null, dst, p);

        // Charge-port bolt on the driver's front fender, just ahead of the door.
        // NOTE: PORT_X/PORT_Y are estimated from the traced image, not pixel-verified on a
        // device (no emulator in this environment, see AGENTS.md). Re-check on real hardware.
        if (charging) {
            Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
            bolt.setColor(context.getColor(R.color.gauge_charging));
            float bx = dst.left + dst.width() * PORT_X, by = dst.top + dst.height() * PORT_Y;
            float u = dst.height() * 0.045f;
            Path path = new Path();
            path.moveTo(bx + u * 0.6f, by - u * 2.4f);
            path.lineTo(bx - u * 1.4f, by + u * 0.4f);
            path.lineTo(bx + u * 0.1f, by + u * 0.4f);
            path.lineTo(bx - u * 0.6f, by + u * 2.4f);
            path.lineTo(bx + u * 1.4f, by - u * 0.4f);
            path.lineTo(bx - u * 0.1f, by - u * 0.4f);
            path.close();
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setColor(Color.argb(230, 255, 255, 255));
            c.drawCircle(bx, by, u * 2.7f, halo);
            c.drawPath(path, bolt);
        }
        return bmp;
    }

    /** Where the charge port lands in the render, as a fraction of the image. Estimated — verify on device. */
    private static final float PORT_X = 0.20f, PORT_Y = 0.50f;

    /** Horizontal battery bar; color follows state like the ring. */
    public static Bitmap bar(Context context, BatterySnapshot s, int widthPx, int heightPx) {
        Bitmap bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = heightPx / 2f;
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setColor(context.getColor(R.color.gauge_track));
        c.drawRoundRect(new RectF(0, 0, widthPx, heightPx), r, r, track);
        if (s.hasData()) {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(s.charging ? context.getColor(R.color.gauge_charging)
                    : WidgetRenderer.isLow(s) ? context.getColor(R.color.gauge_low)
                    : context.getColor(R.color.gauge_fill));
            float w = Math.max(heightPx, widthPx * Math.min(100, s.percent) / 100f);
            c.drawRoundRect(new RectF(0, 0, w, heightPx), r, r, fill);
        }
        return bmp;
    }

    static int blend(int a, int b, float t) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
