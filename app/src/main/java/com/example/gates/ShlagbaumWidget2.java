package com.example.gates;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** Большой виджет 3×2: два шлагбаума, у каждого свои кнопки. */
public class ShlagbaumWidget2 extends AppWidgetProvider {

    static final String PREF_FIRST = "w_";
    static final String PREF_SECOND = "w2_";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx, id));
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = GateStore.prefs(ctx).edit();
        for (int id : ids) {
            e.remove(PREF_FIRST + id);
            e.remove(PREF_SECOND + id);
        }
        e.apply();
    }

    static void refreshAll(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, ShlagbaumWidget2.class));
        for (int id : ids) mgr.updateAppWidget(id, build(app, id));
    }

    static RemoteViews build(Context ctx, int widgetId) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget2);
        SharedPreferences p = GateStore.prefs(ctx);
        String first = p.getString(PREF_FIRST + widgetId, "");
        String second = p.getString(PREF_SECOND + widgetId, "");
        JSONObject g1 = GateStore.find(ctx, first);
        JSONObject g2 = GateStore.find(ctx, second);

        fillRow(ctx, rv, widgetId, first, g1, 1, R.id.w_name, R.id.w_in, R.id.w_out);
        fillRow(ctx, rv, widgetId, second, g2, 3, R.id.w_name2, R.id.w_in2, R.id.w_out2);

        if (g1 == null && g2 == null) {
            PendingIntent open = PendingIntent.getActivity(ctx, widgetId * 3,
                    new Intent(ctx, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.w_root, open);
        }
        return rv;
    }

    private static void fillRow(Context ctx, RemoteViews rv, int widgetId, String gateId,
                                JSONObject g, int slot, int nameId, int inId, int outId) {
        if (g == null) {
            rv.setTextViewText(nameId, gateId.isEmpty() ? "Шлагбаум не выбран" : "Шлагбаум удалён");
            ShlagbaumWidget.disable(rv, inId);
            ShlagbaumWidget.disable(rv, outId);
            return;
        }
        rv.setTextViewText(nameId, GateStore.title(g));
        ShlagbaumWidget.bindButton(ctx, rv, widgetId, gateId, "in", inId, slot, g);
        ShlagbaumWidget.bindButton(ctx, rv, widgetId, gateId, "out", outId, slot + 1, g);
    }
}
