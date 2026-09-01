package com.omarzanji.lyriqwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Draws a side-profile LYRIQ silhouette in the owner's paint color, plus the horizontal
 * battery bar used by the "car" and "bar" widget styles. Everything is Canvas-drawn so
 * it can be handed to RemoteViews as a bitmap and recolored at runtime.
 */
public final class CarRenderer {
    private CarRenderer() {}

    /** Design-space size of the car path; scaled to whatever bitmap size is requested. */
    private static final float DW = 400f, DH = 150f;

    /** Paint presets Cadillac offered on the LYRIQ. name → ARGB. */
    public static final String[] PAINT_NAMES = {
            "Stellar Black", "Argent Silver", "Crystal White", "Emerald Lake",
            "Opulent Blue", "Radiant Red", "Nimbus Gray", "Celestial Blue", "Deep Sea",
    };
    public static final int[] PAINT_COLORS = {
            0xFF141517, 0xFFB7BABF, 0xFFF1F1EC, 0xFF1F4C3F,
            0xFF1C3D72, 0xFF8E1220, 0xFF6D7075, 0xFF8EA4BA, 0xFF14313F,
    };

    public static Bitmap car(Context context, int color, boolean charging, int widthPx, int heightPx) {
        Bitmap bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = Math.min(widthPx / DW, heightPx / DH);
        c.translate((widthPx - DW * s) / 2f, (heightPx - DH * s) / 2f);
        c.scale(s, s);

        int outline = context.getColor(R.color.widget_text);

        // Ground shadow
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Color.argb(64, 0, 0, 0));
        c.drawOval(new RectF(28, 129, 372, 145), shadow);

        int lighter = blend(color, 0xFFFFFFFF, 0.18f);
        int darker = blend(color, 0xFF000000, 0.32f);

        // Body: crossover fastback, nose on the left.
        Path body = new Path();
        body.moveTo(20, 112);
        body.lineTo(20, 92);
        body.cubicTo(20, 78, 30, 70, 50, 66);       // nose / bumper
        body.cubicTo(72, 62, 100, 58, 130, 56);     // hood
        body.cubicTo(148, 40, 170, 26, 210, 24);    // A-pillar into roof
        body.cubicTo(250, 22, 292, 26, 322, 42);    // roofline sweeping into fastback
        body.cubicTo(338, 50, 350, 58, 356, 66);    // rear glass
        body.cubicTo(366, 70, 376, 76, 378, 90);    // upright tailgate
        body.lineTo(378, 112);
        body.close();

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 20, 0, 115, lighter, darker, Shader.TileMode.CLAMP));
        c.drawPath(body, fill);

        // Subtle outline so white cars survive light widgets and black cars survive dark ones.
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2.2f);
        edge.setColor(Color.argb(70, Color.red(outline), Color.green(outline), Color.blue(outline)));
        c.drawPath(body, edge);

        // Glasshouse
        Path glass = new Path();
        glass.moveTo(146, 58);
        glass.cubicTo(162, 44, 184, 32, 214, 31);
        glass.cubicTo(250, 30, 288, 33, 316, 47);
        glass.cubicTo(328, 53, 338, 60, 344, 66);
        glass.lineTo(146, 60);
        glass.close();
        Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassPaint.setShader(new LinearGradient(0, 30, 0, 70, 0xFF2A3440, 0xFF0E1218, Shader.TileMode.CLAMP));
        c.drawPath(glass, glassPaint);

        // B-pillar, shoulder line, door seam
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(darker);
        line.setStrokeWidth(4);
        c.drawLine(238, 31, 234, 60, line);
        Path shoulder = new Path();
        shoulder.moveTo(56, 74);
        shoulder.cubicTo(130, 66, 260, 66, 362, 76);
        line.setStrokeWidth(2);
        line.setColor(Color.argb(46, 255, 255, 255));
        c.drawPath(shoulder, line);
        line.setColor(Color.argb(64, 0, 0, 0));
        c.drawLine(196, 64, 194, 108, line);

        // Vertical LED headlight signature and tail lamp
        Paint lamp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lamp.setColor(Color.argb(230, 255, 244, 214));
        c.drawRoundRect(new RectF(27, 80, 33, 100), 3, 3, lamp);
        lamp.setColor(Color.argb(220, 255, 64, 64));
        c.drawRoundRect(new RectF(367, 88, 375, 100), 2, 2, lamp);

        // Wheels
        drawWheel(c, 100, 116, 30, darker);
        drawWheel(c, 300, 116, 30, darker);

        // Charge-port bolt on the front fender (LYRIQ's port sits ahead of the driver's door).
        if (charging) {
            Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
            bolt.setColor(context.getColor(R.color.gauge_charging));
            float bx = 62, by = 88, u = 5f;
            Path p = new Path();
            p.moveTo(bx + u * 0.6f, by - u * 2.4f);
            p.lineTo(bx - u * 1.4f, by + u * 0.4f);
            p.lineTo(bx + u * 0.1f, by + u * 0.4f);
            p.lineTo(bx - u * 0.6f, by + u * 2.4f);
            p.lineTo(bx + u * 1.4f, by - u * 0.4f);
            p.lineTo(bx - u * 0.1f, by - u * 0.4f);
            p.close();
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setColor(Color.argb(200, 255, 255, 255));
            c.drawCircle(bx, by, u * 3.2f, halo);
            c.drawPath(p, bolt);
        }
        return bmp;
    }

    private static void drawWheel(Canvas c, float cx, float cy, float r, int arch) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(arch);
        c.drawCircle(cx, cy, r + 5, p);           // wheel arch
        p.setColor(0xFF15171A);
        c.drawCircle(cx, cy, r, p);               // tire
        p.setColor(0xFF9A9EA5);
        c.drawCircle(cx, cy, r * 0.58f, p);       // rim
        p.setColor(0xFF3A3D42);
        c.drawCircle(cx, cy, r * 0.16f, p);       // hub
        Paint spoke = new Paint(Paint.ANTI_ALIAS_FLAG);
        spoke.setColor(0xFF3A3D42);
        spoke.setStrokeWidth(r * 0.12f);
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(90 + i * 72);
            c.drawLine(cx, cy, (float) (cx + Math.cos(a) * r * 0.55f), (float) (cy + Math.sin(a) * r * 0.55f), spoke);
        }
    }

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
