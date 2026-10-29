package com.example.gates;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/** Выбор шлагбаума при добавлении виджета на экран. */
public class WidgetConfigActivity extends Activity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Intent in = getIntent();
        if (in.getExtras() != null) {
            widgetId = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        density = getResources().getDisplayMetrics().density;
        GateStore.ensureIds(this);

        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = Color.parseColor(dark ? "#111412" : "#E9EBE8");
        int card = Color.parseColor(dark ? "#1A1E1B" : "#F7F8F6");
        int line = Color.parseColor(dark ? "#2C322E" : "#CFD4CF");
        int ink = Color.parseColor(dark ? "#EEF1EE" : "#1A1D1B");
        int muted = Color.parseColor(dark ? "#9AA39C" : "#5D645F");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Какой шлагбаум вывести на экран?");
        title.setTextColor(ink);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        List<JSONObject> gates = GateStore.all(this);
        if (gates.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Сначала добавьте шлагбаумы в приложении, потом добавьте виджет снова.");
            none.setTextColor(muted);
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            none.setPadding(0, dp(12), 0, dp(12));
            root.addView(none);
            Button open = new Button(this);
            open.setText("Открыть приложение");
            open.setAllCaps(false);
            open.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
            root.addView(open);
        }

        for (JSONObject g : gates) {
            final String id = g.optString("id", "");
            String address = g.optString("address", "");
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            b.setTextColor(ink);
            b.setText(GateStore.title(g) + (address.isEmpty() || address.equals(GateStore.title(g))
                    ? "" : "\n" + address));
            GradientDrawable d = new GradientDrawable();
            d.setColor(card);
            d.setCornerRadius(dp(10));
            d.setStroke(dp(1), line);
            b.setBackground(d);
            b.setStateListAnimator(null);
            b.setPadding(dp(16), dp(10), dp(16), dp(10));
            b.setOnClickListener(v -> choose(id));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(12);
            root.addView(b, p);
        }
        setContentView(scroll);
    }

    private void choose(String gateId) {
        GateStore.prefs(this).edit().putString(ShlagbaumWidget.PREF_PREFIX + widgetId, gateId).apply();
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        mgr.updateAppWidget(widgetId, ShlagbaumWidget.build(this, widgetId));
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }
}
