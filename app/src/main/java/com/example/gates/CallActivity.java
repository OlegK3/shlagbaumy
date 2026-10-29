package com.example.gates;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * Невидимый экран: звонит по кнопке виджета и сбрасывает звонок через заданное время.
 * Сразу закрывается, поверх остаётся экран звонка.
 */
public class CallActivity extends Activity {

    static final String EXTRA_GATE = "gate";
    static final String EXTRA_DIR = "dir";

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable pendingHangup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        JSONObject g = GateStore.find(this, in.getStringExtra(EXTRA_GATE));
        String dir = "out".equals(in.getStringExtra(EXTRA_DIR)) ? "out" : "in";
        String number = g == null ? "" : GateStore.dial(g.optString(dir, ""));

        if (number.isEmpty()) {
            Toast.makeText(this, "Номер не задан. Откройте приложение и проверьте шлагбаум.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Разрешите приложению звонки", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }

        try {
            Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            PhoneAccountHandle sim = chosenSim();
            if (sim != null) call.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim);
            startActivity(call);
        } catch (SecurityException | android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Не удалось позвонить. Проверьте разрешение на звонки.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int sec = GateStore.prefs(this).getInt("sec", 5);
        scheduleHangup(getApplicationContext(), sec);
        finish();
    }

    private PhoneAccountHandle chosenSim() {
        String key = GateStore.prefs(this).getString("sim", "");
        if (key.isEmpty()) return null;
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (tm == null) return null;
            for (PhoneAccountHandle h : tm.getCallCapablePhoneAccounts()) {
                String k = h.getComponentName().flattenToString() + "|" + h.getId();
                if (k.equals(key)) return h;
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private static void scheduleHangup(Context app, int sec) {
        if (pendingHangup != null) H.removeCallbacks(pendingHangup);
        pendingHangup = null;
        if (sec <= 0) return;
        pendingHangup = () -> endCall(app);
        H.postDelayed(pendingHangup, sec * 1000L);
    }

    private static void endCall(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        if (app.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            TelecomManager tm = (TelecomManager) app.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) tm.endCall();
        } catch (SecurityException ignored) {
        }
    }
}
