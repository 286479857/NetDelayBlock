package com.example.netdelay;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.*;

/** Root runs only in the module process, never inside the hooked app. */
public final class RootFirewall {
    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private RootFirewall() {}
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private static File script(Context context) throws IOException {
        File out = new File(context.getFilesDir(), "firewall-v3.sh");
        if (!out.exists()) {
            InputStream in = context.getAssets().open("firewall-v3.sh");
            File tmp = new File(context.getFilesDir(), "firewall-v3.tmp");
            FileOutputStream stream = new FileOutputStream(tmp);
            try {
                byte[] bytes = new byte[4096]; int n;
                while ((n = in.read(bytes)) != -1) stream.write(bytes,0,n);
                stream.getFD().sync();
            } finally { stream.close(); in.close(); }
            if (!tmp.renameTo(out)) throw new IOException("Cannot install script");
        }
        return out;
    }
    public static synchronized String run(Context context, String mode, int uid, int seconds) {
        if (!("probe".equals(mode) || "clear".equals(mode) || "block".equals(mode) || "status".equals(mode)))
            return "ERROR invalid operation";
        if (uid < 10000 || seconds < 0 || seconds > 300) return "ERROR invalid argument";
        Process process = null;
        Future<String> result = null;
        try {
            String token = Long.toHexString(System.nanoTime() & 0xffffffffL);
            String command = "sh " + quote(script(context).getAbsolutePath()) + " " + mode
                + " " + uid + " " + seconds + " " + token;
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            final Process child = process;
            result = IO.submit(new Callable<String>() {
                public String call() throws Exception {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream()));
                    StringBuilder text = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) {
                        if (text.length() < 8000) text.append(line).append('\n');
                    }
                    int code = child.waitFor();
                    String output = text.toString().trim();
                    if (code != 0) return "ERROR exit=" + code + " " + output;
                    return output;
                }
            });
            // Root grant is requested in the UI first, avoiding a prompt at app startup.
            String output = result.get("probe".equals(mode) ? 25 : 8, TimeUnit.SECONDS);
            record(context, output);
            return output;
        } catch (Exception error) {
            if (process != null) process.destroy();
            if (result != null) result.cancel(true);
            String output = "ERROR root/firewall " + error;
            record(context, output); return output;
        }
    }
    public static void record(Context context, String text) {
        context.getSharedPreferences("settings",0).edit()
            .putString("last_log", "t=" + SystemClock.elapsedRealtime() + " " + text).apply();
    }
}
