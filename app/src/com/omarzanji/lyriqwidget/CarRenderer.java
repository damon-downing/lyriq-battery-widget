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
        shadow.setColor(Color.argb(72, 0, 0, 0));
        c.drawOval(new RectF(24, 134, 376, 148), shadow);

        int lighter = blend(color, 0xFFFFFFFF, 0.16f);
        int darker = blend(color, 0xFF000000, 0.36f);

        // Body, traced against the LYRIQ's proportions (196.7 in long, 121.8 in wheelbase,
        // 63.9 in tall): upright nose, long hood, cab-forward raked windshield, arcing roof
        // into a fastback, jutting roof spoiler, liftgate tucked under it.
        Path body = new Path();
        body.moveTo(18, 128);
        body.lineTo(16, 96);
        body.cubicTo(16, 80, 22, 70, 40, 66);       // nose
        body.cubicTo(64, 62, 102, 60, 140, 58);     // hood
        body.cubicTo(150, 50, 158, 40, 172, 32);    // windshield
        body.cubicTo(190, 22, 215, 20, 232, 21);    // roof crest
        body.cubicTo(270, 22, 300, 28, 326, 42);    // roof into fastback
        body.cubicTo(338, 49, 348, 52, 360, 51);    // spoiler
        body.lineTo(362, 55);
        body.cubicTo(359, 58, 356, 60, 355, 62);    // spoiler underside
        body.cubicTo(364, 70, 373, 80, 377, 92);    // liftgate glass
        body.cubicTo(382, 104, 384, 116, 382, 128); // rear bumper
        body.close();

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 20, 0, 128,
                new int[]{lighter, color, darker}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(body, fill);

        // Subtle outline so white cars survive light widgets and black cars survive dark ones.
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2.2f);
        edge.setColor(Color.argb(66, Color.red(outline), Color.green(outline), Color.blue(outline)));
        c.drawPath(body, edge);

        // Side glass: rising beltline, pointed quarter window, thick body-color C-pillar behind it.
        Path glass = new Path();
        glass.moveTo(150, 63);
        glass.cubicTo(160, 50, 172, 40, 190, 34);
        glass.cubicTo(220, 30, 262, 32, 296, 42);
        glass.cubicTo(310, 46, 322, 50, 330, 52);
        glass.lineTo(318, 58);
        glass.lineTo(150, 65);
        glass.close();
        Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassPaint.setShader(new LinearGradient(0, 28, 0, 66, 0xFF2B3644, 0xFF0C1016, Shader.TileMode.CLAMP));
        c.drawPath(glass, glassPaint);

        // B-pillar, character line, door seams, lower sill
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(darker);
        line.setStrokeWidth(4);
        c.drawLine(248, 32, 245, 63, line);
        Path character = new Path();
        character.moveTo(44, 88);
        character.cubicTo(120, 82, 250, 80, 374, 84);
        line.setStrokeWidth(2);
        line.setColor(Color.argb(50, 255, 255, 255));
        c.drawPath(character, line);
        line.setStrokeWidth(1.6f);
        line.setColor(Color.argb(72, 0, 0, 0));
        c.drawLine(202, 66, 200, 124, line);
        c.drawLine(302, 60, 300, 122, line);
        Paint sill = new Paint(Paint.ANTI_ALIAS_FLAG);
        sill.setColor(Color.argb(90, 0, 0, 0));
        c.drawRect(new RectF(58, 124, 370, 128), sill);

        // Vertical LED blades: headlamp at the front corner, tail lamp at the rear
        Paint lamp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lamp.setColor(Color.argb(235, 255, 244, 214));
        c.drawRoundRect(new RectF(19, 74, 25, 112), 3, 3, lamp);
        lamp.setColor(Color.argb(230, 255, 60, 60));
        c.drawRoundRect(new RectF(374, 86, 381, 116), 3, 3, lamp);

        // Wheels: 20-inch, wheelbase ~61% of length
        drawWheel(c, 92, 115, 31, darker);
        drawWheel(c, 336, 115, 31, darker);

        // Charge-port bolt on the front fender (LYRIQ's port sits ahead of the driver's door).
        if (charging) {
            Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
            bolt.setColor(context.getColor(R.color.gauge_charging));
            float bx = 58, by = 100, u = 5f;
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
        c.drawCircle(cx, cy, r + 6, p);           // wheel arch
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
