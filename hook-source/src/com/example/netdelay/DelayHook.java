package com.example.netdelay;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed detects startup; all packet filtering runs in the module's Root helper. */
public class DelayHook implements IXposedHookLoadPackage {
    private Context appContext;
    private boolean foreground = false;
    private int startedActivities;
    private long stoppedAt;
    private void applyFirewall(boolean reopened) {
        if (appContext == null) return;
        try {
            Bundle reply = appContext.getContentResolver().call(Uri.parse("content://com.example.netdelay.settings"),
                "blockSelf", reopened ? "foreground" : "startup", null);
            XposedBridge.log("NetDelayRoot: " + (reply == null ? "ERROR no provider response" : reply.getString("status")));
        } catch (Throwable error) { XposedBridge.log("NetDelayRoot: ERROR provider call " + error); }
    }
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (lp.appInfo == null || lp.appInfo.uid % 100000 < 10000 || "android".equals(lp.packageName)
            || "com.example.netdelay".equals(lp.packageName)) return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) {
                appContext = (Context)p.args[0];
                Cursor c = null;
                try {
                    c = appContext.getContentResolver().query(Uri.parse("content://com.example.netdelay.settings/config"),null,null,null,null);
                    if (c != null && c.moveToFirst()) foreground = c.getInt(c.getColumnIndexOrThrow("foreground")) != 0;
                } catch (Throwable e) { XposedBridge.log("NetDelayRoot: config read error " + e); }
                finally { if (c != null) c.close(); }
                applyFirewall(false);
            }
        });
        XposedBridge.hookAllMethods(Activity.class,"performStart",new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) {
                boolean reopen = false;
                synchronized (DelayHook.this) {
                    long now = SystemClock.elapsedRealtime();
                    if (foreground && startedActivities == 0 && stoppedAt > 0 && now-stoppedAt >= 1000L) reopen = true;
                    startedActivities++;
                }
                if (reopen) applyFirewall(true);
            }
        });
        XposedBridge.hookAllMethods(Activity.class,"performStop",new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam p) {
                synchronized (DelayHook.this) {
                    startedActivities = Math.max(0,startedActivities-1);
                    if (startedActivities == 0) stoppedAt = SystemClock.elapsedRealtime();
                }
            }
        });
        XposedBridge.log("NetDelayRoot: startup detector installed " + lp.processName);
    }
}
