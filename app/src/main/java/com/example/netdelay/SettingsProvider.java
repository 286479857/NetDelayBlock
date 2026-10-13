package com.example.netdelay;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import java.util.HashMap;

/** Public calls can only block the CALLER'S UID; no client-supplied UID accepted. */
public class SettingsProvider extends ContentProvider {
    private final HashMap<Integer,Long> until = new HashMap<Integer,Long>();
    public boolean onCreate() { return true; }
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (!"/config".equals(uri.getPath())) throw new IllegalArgumentException("Unknown URI");
        MatrixCursor c = new MatrixCursor(new String[]{"seconds", "foreground"});
        c.addRow(new Object[]{getContext().getSharedPreferences("settings",0).getInt("seconds",5),
            getContext().getSharedPreferences("settings",0).getBoolean("foreground",false) ? 1 : 0});
        return c;
    }
    public synchronized Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        final int caller = Binder.getCallingUid();
        if (!"blockSelf".equals(method) || caller % 100000 < 10000 || caller == Process.myUid()) {
            result.putString("status","ERROR unsupported caller/method"); return result;
        }
        android.content.SharedPreferences settings = getContext().getSharedPreferences("settings",0);
        if (!settings.getBoolean("armed",false)) {
            result.putString("status","ERROR Root firewall not enabled; open module and authorize first"); return result;
        }
        int seconds = Math.max(0,Math.min(300,settings.getInt("seconds",5)));
        if (seconds == 0) { result.putString("status","OK disabled (0 seconds)"); return result; }
        long now = SystemClock.elapsedRealtime();
        Long deadline = until.get(caller);
        if (!"foreground".equals(arg) && deadline != null && now < deadline.longValue()) {
            result.putString("status","OK existing firewall window uid="+caller); return result;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            String status = RootFirewall.run(getContext(),"block",caller,seconds);
            if (status.startsWith("OK blocked")) until.put(caller,SystemClock.elapsedRealtime()+seconds*1000L);
            result.putString("status",status);
        } finally { Binder.restoreCallingIdentity(identity); }
        return result;
    }
    public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.netdelay.config"; }
    public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Read only"); }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new SecurityException("Read only"); }
    public int delete(Uri uri, String selection, String[] args) { throw new SecurityException("Read only"); }
}
