package com.omarzanji.lyriqwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
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
        int l1 = blend(color, 0xFFFFFFFF, 0.22f);
        int l2 = blend(color, 0xFFFFFFFF, 0.45f);
        int d1 = blend(color, 0xFF000000, 0.25f);
        int d2 = blend(color, 0xFF000000, 0.55f);

        // Soft ground shadow: a radial gradient squashed into an ellipse under the car.
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setShader(new RadialGradient(0, 0, 190,
                new int[]{Color.argb(140, 0, 0, 0), 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.translate(204, 140);
        c.scale(1f, 0.055f);
        c.drawCircle(0, 0, 190, shadow);
        c.restore();

        // Body outline traced over a true side-profile photo of the LYRIQ (400x150 design
        // space): upright nose, long flat hood, fast A-pillar, roof peaking over the front
        // seats, long fastback into a flat spoiler, near-vertical liftgate.
        Path body = new Path();
        body.moveTo(22, 132);
        body.lineTo(18, 102);
        body.cubicTo(17, 84, 20, 72, 30, 66);
        body.cubicTo(38, 62, 60, 61, 90, 61);
        body.cubicTo(110, 60, 130, 60, 148, 59);
        body.cubicTo(160, 49, 176, 36, 196, 29);
        body.cubicTo(212, 23, 236, 22, 260, 24);
        body.cubicTo(290, 26, 318, 33, 342, 42);
        body.cubicTo(356, 47, 370, 48, 384, 47);
        body.lineTo(387, 52);
        body.cubicTo(383, 56, 378, 59, 375, 61);
        body.cubicTo(380, 72, 384, 86, 386, 100);
        body.cubicTo(387, 114, 387, 124, 385, 132);
        body.close();

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.save();
        c.clipPath(body);
        // Studio paint: bright roof, true color at the shoulder, falling into shadow below.
        fill.setShader(new LinearGradient(0, 22, 0, 132,
                new int[]{l2, l1, color, d1, d2}, new float[]{0f, 0.18f, 0.42f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, DW, DH, fill);
        // Horizon reflection across the doors with a crisp lower edge, like a showroom render.
        fill.setShader(new LinearGradient(0, 58, 0, 96,
                new int[]{0x00FFFFFF, 0x61FFFFFF, 0x4DFFFFFF, 0x0FFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.28f, 0.42f, 0.44f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 58, DW, 98, fill);
        // Lower body falls off into darkness.
        fill.setShader(new LinearGradient(0, 104, 0, 132,
                new int[]{0x00000000, 0x73000000, 0xB3000000}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 104, DW, 134, fill);
        // Black wheel-arch liners and the rocker cladding.
        Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        dark.setColor(0xFF0B0D0F);
        c.drawCircle(94, 121, 33, dark);
        c.drawCircle(338, 121, 33, dark);
        Path rocker = new Path();
        rocker.moveTo(40, 122); rocker.lineTo(378, 122); rocker.lineTo(380, 132); rocker.lineTo(30, 132); rocker.close();
        dark.setAlpha(242);
        c.drawPath(rocker, dark);
        c.restore();

        // Glass: dark tint with a diagonal sky reflection, then a top-down specular.
        Path glass = new Path();
        glass.moveTo(156, 66);
        glass.cubicTo(166, 53, 182, 40, 200, 34);
        glass.cubicTo(224, 29, 254, 30, 282, 35);
        glass.cubicTo(308, 39, 330, 46, 348, 52);
        glass.lineTo(354, 56);
        glass.lineTo(318, 60);
        glass.lineTo(158, 68);
        glass.close();
        Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassPaint.setShader(new LinearGradient(150, 30, 330, 70,
                new int[]{0xFF4A5868, 0xFF1A2028, 0xFF2C3644, 0xFF0B0E12},
                new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(glass, glassPaint);
        glassPaint.setShader(new LinearGradient(0, 30, 0, 68,
                new int[]{0x59FFFFFF, 0x0AFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(glass, glassPaint);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        // B-pillar
        line.setColor(0xFF0B0D10); line.setStrokeWidth(3.5f);
        c.drawLine(250, 31, 246, 63, line);
        // Bright window surround
        line.setColor(0x8CC8CCD2); line.setStrokeWidth(0.9f);
        Path surround = new Path();
        surround.moveTo(156, 66);
        surround.cubicTo(166, 53, 182, 40, 200, 34);
        surround.cubicTo(224, 29, 254, 30, 282, 35);
        surround.cubicTo(308, 39, 330, 46, 348, 52);
        surround.lineTo(354, 56);
        c.drawPath(surround, line);
        // Specular rim along the roof
        line.setColor(0x8CFFFFFF); line.setStrokeWidth(1.1f);
        Path roof = new Path();
        roof.moveTo(150, 58);
        roof.cubicTo(162, 48, 178, 36, 198, 30);
        roof.cubicTo(214, 25, 238, 24, 262, 26);
        roof.cubicTo(292, 28, 318, 35, 342, 44);
        c.drawPath(roof, line);
        // Shoulder line: hood edge and the crease running through the door handles
        line.setStrokeWidth(0.8f);
        line.setColor(0x59FFFFFF);
        Path hood = new Path(); hood.moveTo(34, 66); hood.cubicTo(80, 62, 130, 62, 150, 60);
        c.drawPath(hood, line);
        line.setColor(0x47FFFFFF);
        Path crease = new Path(); crease.moveTo(160, 70); crease.cubicTo(230, 66, 300, 62, 372, 62);
        c.drawPath(crease, line);
        // Door seams
        line.setColor(0x59000000); line.setStrokeWidth(0.9f);
        c.drawLine(206, 68, 204, 118, line);
        c.drawLine(298, 62, 296, 118, line);
        // Flush door handles
        Paint chrome = new Paint(Paint.ANTI_ALIAS_FLAG);
        chrome.setColor(0xCCE6E8EA);
        c.drawRoundRect(new RectF(184, 73, 198, 75.2f), 1.1f, 1.1f, chrome);
        c.drawRoundRect(new RectF(276, 69, 290, 71.2f), 1.1f, 1.1f, chrome);
        // Mirror in body color
        Paint mirror = new Paint(Paint.ANTI_ALIAS_FLAG);
        mirror.setColor(color);
        Path m = new Path();
        m.moveTo(148, 62); m.cubicTo(152, 58, 160, 58, 162, 62); m.lineTo(160, 68); m.lineTo(149, 68); m.close();
        c.drawPath(m, mirror);
        // Chrome sill strip along the lower doors
        line.setColor(0xD9DFE2E6); line.setStrokeWidth(1.3f);
        c.drawLine(142, 113, 330, 111, line);

        // Nose: black-crystal grille panel with the vertical LED blade; thin vertical tail lamp.
        Paint lamp = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path grille = new Path();
        grille.moveTo(18, 72); grille.lineTo(30, 70); grille.lineTo(30, 112); grille.lineTo(19, 114); grille.close();
        lamp.setColor(0xD90C0E11);
        c.drawPath(grille, lamp);
        lamp.setColor(0xF2FFF5D6);
        c.drawRoundRect(new RectF(20, 72, 23.2f, 112), 1.6f, 1.6f, lamp);
        c.drawRoundRect(new RectF(24, 70, 32, 72), 1, 1, lamp);
        lamp.setColor(0xF2FF2E2E);
        c.drawRoundRect(new RectF(382.5f, 80, 385.5f, 116), 1.5f, 1.5f, lamp);

        // Faint body edge so light paint survives light widgets and black paint survives dark ones.
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1f);
        edge.setColor(Color.argb(40, Color.red(outline), Color.green(outline), Color.blue(outline)));
        c.drawPath(body, edge);

        drawWheel(c, 94, 121, 29);
        drawWheel(c, 338, 121, 29);

        // Charge-port bolt on the driver's front fender, just ahead of the door.
        if (charging) {
            Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
            bolt.setColor(context.getColor(R.color.gauge_charging));
            float bx = 130, by = 84, u = 4f;
            Path p = new Path();
            p.moveTo(bx + u * 0.6f, by - u * 2.4f);
            p.lineTo(bx - u * 1.4f, by + u * 0.4f);
            p.lineTo(bx + u * 0.1f, by + u * 0.4f);
            p.lineTo(bx - u * 0.6f, by + u * 2.4f);
            p.lineTo(bx + u * 1.4f, by - u * 0.4f);
            p.lineTo(bx - u * 0.1f, by - u * 0.4f);
            p.close();
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setColor(Color.argb(230, 255, 255, 255));
            c.drawCircle(bx, by, u * 2.7f, halo);
            c.drawPath(p, bolt);
        }
        return bmp;
    }

    /** 20-inch ten-spoke alloy: tire with a lit shoulder, brushed rim, dark spokes, chrome cap. */
    private static void drawWheel(Canvas c, float cx, float cy, float r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, r,
                new int[]{0xFF15171A, 0xFF15171A, 0xFF2A2D31, 0xFF101214},
                new float[]{0f, 0.8f, 0.93f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        float rr = r * 0.655f;
        p.setShader(new RadialGradient(cx - rr * 0.24f, cy - rr * 0.3f, rr * 1.5f,
                new int[]{0xFFF2F3F5, 0xFF9EA3AB, 0xFF4C5158},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rr, p);
        Paint spoke = new Paint(Paint.ANTI_ALIAS_FLAG);
        spoke.setColor(0xFF2B2F35);
        float rIn = rr * 0.27f, rOut = rr * 0.975f, w = rr * 0.137f;
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + i * 36);
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            float nx = -dy * w / 2, ny = dx * w / 2;
            Path sp = new Path();
            sp.moveTo(cx + dx * rIn + nx, cy + dy * rIn + ny);
            sp.lineTo(cx + dx * rOut + nx * 0.6f, cy + dy * rOut + ny * 0.6f);
            sp.lineTo(cx + dx * rOut - nx * 0.6f, cy + dy * rOut - ny * 0.6f);
            sp.lineTo(cx + dx * rIn - nx, cy + dy * rIn - ny);
            sp.close();
            c.drawPath(sp, spoke);
        }
        p.setShader(null);
        p.setColor(0xFFD9DCE0);
        c.drawCircle(cx, cy, rr * 0.21f, p);
        p.setColor(0xFF6B7078);
        c.drawCircle(cx, cy, rr * 0.105f, p);
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
