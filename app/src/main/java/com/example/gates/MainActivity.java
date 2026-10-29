package com.example.gates;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static class Gate {
        String id = java.util.UUID.randomUUID().toString();
        String name = "";
        String address = "";
        String in = "";
        String out = "";
    }

    private static final int REQ_PERMS = 1;
    private static final String PRIVACY_URL = "https://olegk3.github.io/shlagbaumy/privacy.html";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hangup = this::endCallNow;

    private final List<Gate> gates = new ArrayList<>();
    private int hangupSec = 5;
    private String simKey = "";
    private boolean editing = false;
    private int confirmDelete = -1;
    private String pendingNumber = null;

    private ScrollView scroll;
    private LinearLayout list;
    private LinearLayout settingsBox;
    private TextView hint;
    private TextView emptyNote;
    private Button editBtn;
    private Button addBtn;
    private Button saveBtn;
    private EditText secField;
    private RadioGroup simGroup;
    private TextView simNote;
    private final List<EditText> nameF = new ArrayList<>();
    private final List<EditText> addrF = new ArrayList<>();
    private final List<EditText> inF = new ArrayList<>();
    private final List<EditText> outF = new ArrayList<>();

    private int cBg, cCard, cInk, cMuted, cLine, cGreen, cGreenInk, cOut, cOutInk, cField, cOff, cOffInk, cRed;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        initColors();
        prefs = getSharedPreferences("gates", MODE_PRIVATE);
        GateStore.ensureIds(this);
        load();
        buildUi();

        boolean anyNumber = false;
        for (Gate g : gates) {
            if (!g.in.isEmpty() || !g.out.isEmpty()) anyNumber = true;
        }
        editing = !anyNumber && !prefs.contains("gates_json");
        applyMode();
        askPermissions();
    }

    // ---------- данные ----------

    private void load() {
        gates.clear();
        String json = prefs.getString("gates_json", null);
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Gate g = new Gate();
                    String savedId = o.optString("id", "");
                    if (!savedId.isEmpty()) g.id = savedId;
                    g.name = o.optString("name", "");
                    g.address = o.optString("address", "");
                    g.in = o.optString("in", "");
                    g.out = o.optString("out", "");
                    gates.add(g);
                }
            } catch (JSONException ignored) {
            }
        } else {
            // перенос данных из первой версии (5 шлагбаумов)
            for (int i = 0; i < 5; i++) {
                Gate g = new Gate();
                g.name = prefs.getString("n" + i, "");
                g.in = prefs.getString("i" + i, "");
                g.out = prefs.getString("o" + i, "");
                if (!g.name.isEmpty() || !g.in.isEmpty() || !g.out.isEmpty()) gates.add(g);
            }
            if (gates.isEmpty()) gates.add(new Gate());
        }
        hangupSec = prefs.getInt("sec", 5);
        simKey = prefs.getString("sim", "");
    }

    /** Переносит введённое в поля обратно в список шлагбаумов. */
    private void collectFields() {
        if (!editing) return;
        for (int i = 0; i < gates.size() && i < nameF.size(); i++) {
            Gate g = gates.get(i);
            g.name = nameF.get(i).getText().toString().trim();
            g.address = addrF.get(i).getText().toString().trim();
            g.in = inF.get(i).getText().toString();
            g.out = outF.get(i).getText().toString();
        }
    }

    private void saveAll() {
        collectFields();
        for (Gate g : gates) {
            g.in = cleanNumber(g.in);
            g.out = cleanNumber(g.out);
        }
        gates.removeIf(g -> g.name.isEmpty() && g.address.isEmpty() && g.in.isEmpty() && g.out.isEmpty());

        JSONArray a = new JSONArray();
        for (Gate g : gates) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", g.id);
                o.put("name", g.name);
                o.put("address", g.address);
                o.put("in", g.in);
                o.put("out", g.out);
            } catch (JSONException ignored) {
            }
            a.put(o);
        }

        int sec = 5;
        try {
            sec = Integer.parseInt(secField.getText().toString().trim());
        } catch (NumberFormatException ignored) {
        }
        if (sec < 0) sec = 0;
        if (sec > 60) sec = 60;
        hangupSec = sec;

        RadioButton checked = simGroup.findViewById(simGroup.getCheckedRadioButtonId());
        if (checked != null) simKey = (String) checked.getTag();

        prefs.edit()
                .putString("gates_json", a.toString())
                .putInt("sec", sec)
                .putString("sim", simKey)
                .apply();
        ShlagbaumWidget.refreshAll(this);
    }

    // ---------- номера ----------

    private static String ruDigits(String v) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        String d = sb.toString();
        if (d.isEmpty()) return "";
        if (d.length() >= 12 && (d.startsWith("78") || d.startsWith("77"))) {
            d = "7" + d.substring(2);
        }
        if (d.charAt(0) == '8') {
            d = "7" + d.substring(1);
        } else if (d.charAt(0) != '7') {
            d = "7" + d;
        }
        return d.length() > 11 ? d.substring(0, 11) : d;
    }

    private static String fmtRu(String v) {
        String d = ruDigits(v);
        if (d.isEmpty()) return "";
        String r = d.substring(1);
        StringBuilder o = new StringBuilder("+7 ");
        o.append(r, 0, Math.min(3, r.length()));
        if (r.length() > 3) o.append(' ').append(r, 3, Math.min(6, r.length()));
        if (r.length() > 6) o.append('-').append(r, 6, Math.min(8, r.length()));
        if (r.length() > 8) o.append('-').append(r, 8, Math.min(10, r.length()));
        return o.toString();
    }

    /** Пустая строка, если номер неполный. */
    private static String cleanNumber(String v) {
        String d = ruDigits(v);
        return d.length() == 11 ? fmtRu(d) : "";
    }

    private static String dialNumber(String v) {
        String d = ruDigits(v);
        return d.length() == 11 ? "+" + d : "";
    }

    // ---------- звонок ----------

    private void askPermissions() {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_PHONE_STATE}, REQ_PERMS);
        }
    }

    private static String keyOf(PhoneAccountHandle h) {
        return h.getComponentName().flattenToString() + "|" + h.getId();
    }

    private List<PhoneAccountHandle> simAccounts() {
        List<PhoneAccountHandle> res = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return res;
        }
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm != null) res.addAll(tm.getCallCapablePhoneAccounts());
        } catch (SecurityException ignored) {
        }
        return res;
    }

    private PhoneAccountHandle chosenSim() {
        if (simKey.isEmpty()) return null;
        for (PhoneAccountHandle h : simAccounts()) {
            if (keyOf(h).equals(simKey)) return h;
        }
        return null;
    }

    private void addSimOption(String title, String key) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(title);
        rb.setTextColor(cInk);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rb.setTag(key);
        simGroup.addView(rb, new RadioGroup.LayoutParams(MATCH, WRAP));
    }

    private void buildSimGroup() {
        simGroup.removeAllViews();
        List<PhoneAccountHandle> accounts = simAccounts();
        TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
        addSimOption("По умолчанию, как настроено в телефоне", "");
        for (int k = 0; k < accounts.size(); k++) {
            PhoneAccountHandle h = accounts.get(k);
            String label = "";
            try {
                PhoneAccount acc = tm == null ? null : tm.getPhoneAccount(h);
                if (acc != null && acc.getLabel() != null) label = acc.getLabel().toString();
            } catch (SecurityException ignored) {
            }
            addSimOption("SIM " + (k + 1) + (label.isEmpty() ? "" : " — " + label), keyOf(h));
        }
        boolean found = false;
        for (int k = 0; k < simGroup.getChildCount(); k++) {
            RadioButton rb = (RadioButton) simGroup.getChildAt(k);
            if (simKey.equals(rb.getTag())) {
                rb.setChecked(true);
                found = true;
            }
        }
        if (!found) ((RadioButton) simGroup.getChildAt(0)).setChecked(true);
        simNote.setText(accounts.isEmpty()
                ? "Список SIM пуст. Разрешите приложению доступ к телефону в настройках Android."
                : "");
    }

    private void call(String stored) {
        String number = dialNumber(stored);
        if (number.isEmpty()) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number;
            askPermissions();
            return;
        }
        startCall(number);
    }

    private void startCall(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            PhoneAccountHandle sim = chosenSim();
            if (sim != null) intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim);
            startActivity(intent);
        } catch (SecurityException | android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Не удалось позвонить. Проверьте разрешение на звонки.", Toast.LENGTH_LONG).show();
            return;
        }
        handler.removeCallbacks(hangup);
        if (hangupSec > 0) {
            handler.postDelayed(hangup, hangupSec * 1000L);
        }
    }

    private void endCallNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm != null) tm.endCall();
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS) return;
        if (editing) buildSimGroup();
        String number = pendingNumber;
        pendingNumber = null;
        if (number != null
                && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startCall(number);
        }
    }

    // ---------- оформление ----------

    private void initColors() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        cRed = Color.parseColor("#D8381F");
        if (dark) {
            cBg = Color.parseColor("#111412");
            cCard = Color.parseColor("#1A1E1B");
            cInk = Color.parseColor("#EEF1EE");
            cMuted = Color.parseColor("#9AA39C");
            cLine = Color.parseColor("#2C322E");
            cGreen = Color.parseColor("#2E9D66");
            cGreenInk = Color.parseColor("#07140D");
            cOut = Color.parseColor("#EEF1EE");
            cOutInk = Color.parseColor("#111412");
            cField = Color.parseColor("#111412");
            cOff = Color.parseColor("#252A26");
            cOffInk = Color.parseColor("#78807A");
        } else {
            cBg = Color.parseColor("#E9EBE8");
            cCard = Color.parseColor("#F7F8F6");
            cInk = Color.parseColor("#1A1D1B");
            cMuted = Color.parseColor("#5D645F");
            cLine = Color.parseColor("#CFD4CF");
            cGreen = Color.parseColor("#1C7A4B");
            cGreenInk = Color.WHITE;
            cOut = Color.parseColor("#1A1D1B");
            cOutInk = Color.WHITE;
            cField = Color.WHITE;
            cOff = Color.parseColor("#DDE1DC");
            cOffInk = Color.parseColor("#7B837D");
        }
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    private GradientDrawable rect(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius((float) dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText field(String value, String hintText, boolean phone) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hintText);
        e.setHintTextColor(cMuted);
        e.setTextColor(cInk);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setBackground(rect(cField, cLine, 8));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (phone) {
            e.setInputType(InputType.TYPE_CLASS_PHONE);
            e.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String now = s.toString();
                    String f = fmtRu(now);
                    if (!f.equals(now)) {
                        s.replace(0, s.length(), f);
                        Selection.setSelection(s, s.length());
                    }
                }
            });
            e.setOnFocusChangeListener((v, hasFocus) -> {
                EditText et = (EditText) v;
                if (hasFocus && et.getText().length() == 0) {
                    et.setText("+7 ");
                    et.setSelection(et.getText().length());
                } else if (!hasFocus && ruDigits(et.getText().toString()).length() <= 1) {
                    et.setText("");
                }
            });
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return e;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.topMargin = top;
        return p;
    }

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ШЛАГБАУМЫ", 26, cInk, true);
        title.setLetterSpacing(0.06f);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        editBtn = new Button(this);
        editBtn.setAllCaps(false);
        editBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        editBtn.setTextColor(cInk);
        editBtn.setBackground(rect(Color.TRANSPARENT, cInk, 8));
        editBtn.setStateListAnimator(null);
        editBtn.setOnClickListener(v -> {
            if (editing) {
                load(); // «Отмена»: вернуть сохранённое
                editing = false;
            } else {
                editing = true;
            }
            confirmDelete = -1;
            applyMode();
        });
        header.addView(editBtn, new LinearLayout.LayoutParams(WRAP, dp(44)));
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        hint = text("", 14, cMuted, false);
        root.addView(hint, lp(MATCH, WRAP, 0f, dp(8)));

        settingsBox = new LinearLayout(this);
        settingsBox.setOrientation(LinearLayout.VERTICAL);
        settingsBox.addView(text("СЕКУНД ДО СБРОСА ЗВОНКА (0 — НЕ СБРАСЫВАТЬ)", 12, cMuted, true));
        secField = field(String.valueOf(hangupSec), "5", false);
        secField.setInputType(InputType.TYPE_CLASS_NUMBER);
        settingsBox.addView(secField, lp(MATCH, WRAP, 0f, dp(4)));
        settingsBox.addView(text("SIM-КАРТА ДЛЯ ЗВОНКОВ", 12, cMuted, true), lp(MATCH, WRAP, 0f, dp(14)));
        simGroup = new RadioGroup(this);
        simGroup.setOrientation(RadioGroup.VERTICAL);
        settingsBox.addView(simGroup, lp(MATCH, WRAP, 0f, dp(4)));
        simNote = text("", 13, cMuted, false);
        settingsBox.addView(simNote, lp(MATCH, WRAP, 0f, dp(4)));
        root.addView(settingsBox, lp(MATCH, WRAP, 0f, dp(12)));

        emptyNote = text("Шлагбаумов пока нет. Нажмите «Добавить шлагбаум» ниже.", 15, cMuted, false);
        root.addView(emptyNote, lp(MATCH, WRAP, 0f, dp(16)));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, lp(MATCH, WRAP, 0f, dp(4)));

        addBtn = new Button(this);
        addBtn.setText("+ Добавить шлагбаум");
        addBtn.setAllCaps(false);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        addBtn.setTextColor(cInk);
        addBtn.setBackground(rect(Color.TRANSPARENT, cInk, 10));
        addBtn.setStateListAnimator(null);
        addBtn.setOnClickListener(v -> {
            collectFields();
            gates.add(new Gate());
            confirmDelete = -1;
            if (!editing) {
                editing = true;
                applyMode();
            } else {
                renderList();
            }
            if (!nameF.isEmpty()) nameF.get(nameF.size() - 1).requestFocus();
        });
        root.addView(addBtn, lp(MATCH, dp(52), 0f, dp(14)));

        saveBtn = new Button(this);
        saveBtn.setText("СОХРАНИТЬ");
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        saveBtn.setTextColor(cGreenInk);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setBackground(rect(cGreen, 0, 10));
        saveBtn.setStateListAnimator(null);
        saveBtn.setOnClickListener(v -> {
            saveAll();
            editing = false;
            confirmDelete = -1;
            applyMode();
        });
        root.addView(saveBtn, lp(MATCH, dp(54), 0f, dp(10)));

        TextView foot = text("Чтобы звонок сбрасывался сам, разрешите приложению звонки и управление вызовами. Данные хранятся на этом телефоне.", 13, cMuted, false);
        root.addView(foot, lp(MATCH, WRAP, 0f, dp(16)));

        TextView privacy = text("Политика конфиденциальности", 14, cMuted, false);
        privacy.setPaintFlags(privacy.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        privacy.setPadding(0, dp(10), 0, dp(10));
        privacy.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)));
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(this, "Не найден браузер для открытия страницы", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(privacy, lp(WRAP, WRAP, 0f, dp(6)));

        setContentView(scroll);
    }

    private void applyMode() {
        editBtn.setText(editing ? "Отмена" : "Изменить");
        hint.setText(editing
                ? "Вводите номер с любой цифры, «+7» подставится сам. Например: 900 123-45-67."
                : "Нажмите «Въезд» или «Выезд», телефон позвонит на номер шлагбаума.");
        settingsBox.setVisibility(editing ? View.VISIBLE : View.GONE);
        saveBtn.setVisibility(editing ? View.VISIBLE : View.GONE);
        if (editing) {
            secField.setText(String.valueOf(hangupSec));
            buildSimGroup();
        }
        renderList();
    }

    private void renderList() {
        nameF.clear();
        addrF.clear();
        inF.clear();
        outF.clear();
        list.removeAllViews();
        emptyNote.setVisibility(gates.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < gates.size(); i++) {
            list.addView(gateCard(i), lp(MATCH, WRAP, 0f, dp(14)));
        }
    }

    private View gateCard(int i) {
        final int idx = i;
        Gate g = gates.get(i);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rect(cCard, cLine, 10));

        // полоса-стрела шлагбаума
        LinearLayout arm = new LinearLayout(this);
        arm.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < 16; k++) {
            View seg = new View(this);
            seg.setBackgroundColor(k % 2 == 0 ? cRed : cCard);
            arm.addView(seg, new LinearLayout.LayoutParams(0, MATCH, 1f));
        }
        card.addView(arm, new LinearLayout.LayoutParams(MATCH, dp(10)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.addView(body, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text(String.valueOf(i + 1), 26, cInk, true));
        if (editing) {
            TextView lbl = text("ШЛАГБАУМ", 16, cMuted, true);
            lbl.setPadding(dp(10), 0, 0, 0);
            titleRow.addView(lbl);
        } else if (!g.name.isEmpty() || !g.address.isEmpty()) {
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);
            if (!g.name.isEmpty()) {
                info.addView(text(g.name.toUpperCase(), 18, cInk, true));
            }
            if (!g.address.isEmpty()) {
                info.addView(text(g.address, 14, cMuted, false));
            }
            titleRow.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        body.addView(titleRow, new LinearLayout.LayoutParams(MATCH, WRAP));

        if (editing) {
            body.addView(text("НАЗВАНИЕ", 12, cMuted, true), lp(MATCH, WRAP, 0f, dp(10)));
            EditText n = field(g.name, "Например, Главный", false);
            nameF.add(n);
            body.addView(n, lp(MATCH, WRAP, 0f, dp(4)));

            body.addView(text("АДРЕС", 12, cMuted, true), lp(MATCH, WRAP, 0f, dp(10)));
            EditText a = field(g.address, "Например, ул. Ленина, 5", false);
            addrF.add(a);
            body.addView(a, lp(MATCH, WRAP, 0f, dp(4)));

            body.addView(text("НОМЕР ВЪЕЗДА", 12, cMuted, true), lp(MATCH, WRAP, 0f, dp(10)));
            EditText fi = field(g.in, "+7 900 123-45-67", true);
            inF.add(fi);
            body.addView(fi, lp(MATCH, WRAP, 0f, dp(4)));

            body.addView(text("НОМЕР ВЫЕЗДА", 12, cMuted, true), lp(MATCH, WRAP, 0f, dp(10)));
            EditText fo = field(g.out, "+7 900 123-45-67", true);
            outF.add(fo);
            body.addView(fo, lp(MATCH, WRAP, 0f, dp(4)));

            Button del = new Button(this);
            del.setText(confirmDelete == idx ? "Точно удалить? Нажмите ещё раз" : "Удалить шлагбаум");
            del.setAllCaps(false);
            del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            del.setTextColor(cRed);
            del.setBackground(rect(Color.TRANSPARENT, cRed, 8));
            del.setStateListAnimator(null);
            del.setOnClickListener(v -> {
                collectFields();
                if (confirmDelete == idx) {
                    gates.remove(idx);
                    confirmDelete = -1;
                } else {
                    confirmDelete = idx;
                }
                renderList();
            });
            body.addView(del, lp(MATCH, dp(46), 0f, dp(14)));
        } else {
            LinearLayout pair = new LinearLayout(this);
            pair.setOrientation(LinearLayout.HORIZONTAL);
            pair.addView(side("ВЪЕЗД", g.in, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
            View gap = new View(this);
            pair.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
            pair.addView(side("ВЫЕЗД", g.out, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
            body.addView(pair, lp(MATCH, WRAP, 0f, dp(12)));
        }
        return card;
    }

    private View side(String label, String number, boolean isIn) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        boolean has = !dialNumber(number).isEmpty();
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(true);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        if (has) {
            b.setTextColor(isIn ? cGreenInk : cOutInk);
            b.setBackground(rect(isIn ? cGreen : cOut, 0, 8));
            b.setOnClickListener(v -> call(number));
        } else {
            b.setTextColor(cOffInk);
            b.setBackground(rect(cOff, 0, 8));
            b.setEnabled(false);
        }
        col.addView(b, new LinearLayout.LayoutParams(MATCH, dp(76)));

        TextView n = text(has ? number : "номер не задан", 13, cMuted, false);
        n.setGravity(Gravity.CENTER);
        n.setTypeface(Typeface.MONOSPACE);
        col.addView(n, lp(MATCH, WRAP, 0f, dp(6)));
        return col;
    }
}
