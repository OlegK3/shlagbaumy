package com.example.gates;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONObject;

public class ShlagbaumWidget extends AppWidgetProvider {

    static final String PREF_PREFIX = "w_";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx, id));
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = GateStore.prefs(ctx).edit();
        for (int id : ids) e.remove(PREF_PREFIX + id);
        e.apply();
    }

    /** Перерисовывает все виджеты (после сохранения списка шлагбаумов). */
    static void refreshAll(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, ShlagbaumWidget.class));
        for (int id : ids) mgr.updateAppWidget(id, build(app, id));
        ShlagbaumWidget2.refreshAll(app);
    }

    static RemoteViews build(Context ctx, int widgetId) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        String gateId = GateStore.prefs(ctx).getString(PREF_PREFIX + widgetId, "");
        JSONObject g = GateStore.find(ctx, gateId);

        if (g == null) {
            rv.setTextViewText(R.id.w_name, "Шлагбаум не выбран или удалён");
            disable(rv, R.id.w_in);
            disable(rv, R.id.w_out);
            PendingIntent open = PendingIntent.getActivity(ctx, widgetId * 3,
                    new Intent(ctx, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.w_root, open);
            return rv;
        }

        rv.setTextViewText(R.id.w_name, GateStore.title(g));
        bindButton(ctx, rv, widgetId, gateId, "in", R.id.w_in, 1, g);
        bindButton(ctx, rv, widgetId, gateId, "out", R.id.w_out, 2, g);
        return rv;
    }

    static void bindButton(Context ctx, RemoteViews rv, int widgetId, String gateId,
                                   String dir, int viewId, int slot, JSONObject g) {
        if (GateStore.dial(g.optString(dir, "")).isEmpty()) {
            disable(rv, viewId);
            return;
        }
        Intent i = new Intent(ctx, CallActivity.class);
        i.setData(Uri.parse("shlagbaum://call/" + widgetId + "/" + slot + "/" + dir));
        i.putExtra(CallActivity.EXTRA_GATE, gateId);
        i.putExtra(CallActivity.EXTRA_DIR, dir);
        PendingIntent pi = PendingIntent.getActivity(ctx, widgetId * 3 + slot, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(viewId, pi);
    }

    static void disable(RemoteViews rv, int viewId) {
        rv.setInt(viewId, "setBackgroundResource", R.drawable.widget_btn_off);
        rv.setTextColor(viewId, android.graphics.Color.parseColor("#7B837D"));
    }
}
