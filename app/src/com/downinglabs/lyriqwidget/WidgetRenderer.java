package com.downinglabs.lyriqwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.format.DateUtils;

import java.util.Locale;

/** Draws the circular charge gauge and formats the widget's text lines. */
public final class WidgetRenderer {
    private WidgetRenderer() {}

    public static boolean isLow(BatterySnapshot s) {
        return s.hasData() && s.percent <= 20 && !s.charging;
    }

    /** Ring gauge; sizePx square. Fill color follows the state: charging, low, or accent. */
    public static Bitmap gauge(Context context, BatterySnapshot s, int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float stroke = sizePx * 0.11f;
        float inset = stroke / 2f + 1f;
        RectF box = new RectF(inset, inset, sizePx - inset, sizePx - inset);

        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(context.getColor(R.color.gauge_track));
        c.drawArc(box, -90, 360, false, track);

        if (s.hasData()) {
            Paint fill = new Paint(track);
            int color = s.charging ? context.getColor(R.color.gauge_charging)
                    : isLow(s) ? context.getColor(R.color.gauge_low)
                    : context.getColor(R.color.gauge_fill);
            fill.setColor(color);
            float sweep = Math.max(2f, 360f * Math.min(100, s.percent) / 100f);
            c.drawArc(box, -90, sweep, false, fill);

            if (s.charging) {
                // small bolt inside the ring's top-right quadrant
                Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
                bolt.setColor(color);
                float cx = sizePx * 0.5f, cy = sizePx * 0.5f, r = sizePx * 0.30f;
                float bx = cx + r * 0.62f, by = cy - r * 0.62f, u = sizePx * 0.035f;
                Path p = new Path();
                p.moveTo(bx + u * 0.6f, by - u * 2.4f);
                p.lineTo(bx - u * 1.4f, by + u * 0.4f);
                p.lineTo(bx + u * 0.1f, by + u * 0.4f);
                p.lineTo(bx - u * 0.6f, by + u * 2.4f);
                p.lineTo(bx + u * 1.4f, by - u * 0.4f);
                p.lineTo(bx - u * 0.1f, by - u * 0.4f);
                p.close();
                c.drawPath(p, bolt);
            }
        }
        return bmp;
    }

    public static String percentText(BatterySnapshot s) {
        return s.hasData() ? s.percent + "%" : "—";
    }

    /** e.g. "226 mi · Charging" / "226 mi · Plugged in" / "226 mi". */
    public static String statusLine(Context context, BatterySnapshot s) {
        if (!s.hasData()) return context.getString(R.string.no_data);
        StringBuilder b = new StringBuilder();
        if (s.rangeMiles >= 0) b.append(String.format(Locale.US, "%.0f mi", s.rangeMiles));
        String state = s.charging ? "Charging" : s.pluggedIn ? "Plugged in" : null;
        if (state != null) {
            if (b.length() > 0) b.append(" · ");
            b.append(state);
        }
        if (b.length() == 0) b.append(context.getString(R.string.vehicle_name_default));
        return b.toString();
    }

    /** "Car reported 12 min ago" or the last error, or "Refreshing…". */
    public static String footer(Context context, Vehicle vehicle, BatterySnapshot s) {
        if (vehicle.isRefreshing()) return context.getString(R.string.refreshing);
        if (s.error != null) return "Couldn't refresh · " + s.error;
        if (s.updatedAt <= 0) return context.getString(R.string.tap_to_refresh);
        long age = System.currentTimeMillis() - s.updatedAt;
        String rel = age < DateUtils.MINUTE_IN_MILLIS ? "just now"
                : DateUtils.getRelativeTimeSpanString(s.updatedAt, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
        // updatedAt is when the car reported the values, not when we fetched them.
        return "Car reported " + rel;
    }

    public static String title(Context context, BatterySnapshot s) {
        return s.vehicleName == null || s.vehicleName.isEmpty() ? context.getString(R.string.vehicle_name_default) : s.vehicleName;
    }
}
