package com.example.gates;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Общий доступ к списку шлагбаумов для приложения, виджета и экрана звонка. */
final class GateStore {
    static final String PREFS = "gates";
    static final String KEY = "gates_json";

    private GateStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Выдаёт постоянный id тем шлагбаумам, у которых его ещё нет, и сохраняет. */
    static void ensureIds(Context c) {
        SharedPreferences p = prefs(c);
        String json = p.getString(KEY, null);
        if (json == null) return;
        try {
            JSONArray a = new JSONArray(json);
            boolean changed = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optString("id", "").isEmpty()) {
                    o.put("id", UUID.randomUUID().toString());
                    changed = true;
                }
            }
            if (changed) p.edit().putString(KEY, a.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static List<JSONObject> all(Context c) {
        List<JSONObject> res = new ArrayList<>();
        String json = prefs(c).getString(KEY, null);
        if (json == null) return res;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) res.add(a.getJSONObject(i));
        } catch (JSONException ignored) {
        }
        return res;
    }

    /** Шлагбаум по id или null, если его удалили. */
    static JSONObject find(Context c, String id) {
        if (id == null || id.isEmpty()) return null;
        for (JSONObject o : all(c)) {
            if (id.equals(o.optString("id", ""))) return o;
        }
        return null;
    }

    /** Название для показа: имя, иначе адрес, иначе «Шлагбаум». */
    static String title(JSONObject g) {
        String n = g.optString("name", "");
        if (n.isEmpty()) n = g.optString("address", "");
        return n.isEmpty() ? "Шлагбаум" : n;
    }

    /** Номер для набора вида +7900..., пустая строка, если номер не задан. */
    static String dial(String stored) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < stored.length(); k++) {
            char ch = stored.charAt(k);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.length() == 11 ? "+" + sb : "";
    }
}
