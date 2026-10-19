package com.racas.black;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

// Asks GitHub for the newest release of this app, off the main thread: at each launch, handing it over when it is
// newer than the one installed and the player has not skipped it, and from the options menu, where the answer is
// given whatever it is.
final class UpdateChecker {
    static final String REPO = "contatg/Black-mobiu";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String TAG = "BlackUpdate";
    private static final int TIMEOUT_MS = 8000;

    private static final String PREFS = "updates";
    private static final String SKIPPED = "skipped_tag";

    static final class Release {
        String tag;      // v1.2.3
        String name;     // Black for Android v1.2.3
        String page;     // the release on GitHub
        String apk;      // the APK's download, or null
        String changes;
        String sha256;
        long size;
    }

    interface Listener {
        // the newer release or null; failed when GitHub could not be asked
        void onResult(Release newer, boolean failed);
    }

    private UpdateChecker() {}

    // manual: asked from the menu, a skipped release counts too; the listener runs on the main thread
    static void check(Context context, boolean manual, Listener listener) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String installed = installedVersion(app);
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Release newer = null;
            boolean failed = false;
            try {
                Release r = fetch();
                boolean skipped = r != null && !manual && r.tag.equals(prefs.getString(SKIPPED, null));
                if (r != null && !skipped && compare(r.tag, installed) > 0) {
                    Log.i(TAG, "update " + r.tag + " over " + installed);
                    newer = r;
                }
            } catch (Exception e) {
                Log.w(TAG, "no update check: " + e);  // offline, rate limited: try again next launch
                failed = true;
            }
            if(!failed)prefs.edit().putLong("last_checked",System.currentTimeMillis()).apply();
            Release found = newer;
            boolean f = failed;
            main.post(() -> listener.onResult(found, f));
        }, "UpdateCheck").start();
    }

    static void skip(Context context, String tag) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(SKIPPED, tag).apply();
    }

    static String installedVersion(Context app) {
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    private static Release fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(LATEST).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Black-Android");
        try {
            int code=c.getResponseCode();
            if(code==404)return null;
            if(code!=200)throw new IOException("GitHub HTTP "+code);
            JSONObject j = new JSONObject(read(c.getInputStream()));
            Release r = new Release();
            r.tag = j.getString("tag_name");
            r.name = j.optString("name", r.tag);
            if (r.name.isEmpty()) r.name = r.tag;
            r.page = j.optString("html_url", "https://github.com/" + REPO + "/releases/latest");
            JSONArray assets = j.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    String url=a.optString("browser_download_url", "");
                    if(!url.startsWith("https://github.com/"+REPO+"/releases/download/"))continue;
                    r.apk=url;r.size=a.optLong("size",0);
                    r.sha256=a.optString("digest","");
                    break;
                }
            }
            r.changes = changes(j.optString("body", ""));
            return r;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    static boolean automatic(Context app){return app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("auto_check",true);}
    static void automatic(Context app,boolean enabled){app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("auto_check",enabled).apply();}

    private static String changes(String body) {
        StringBuilder text=new StringBuilder();int count=0;
        for(String line:body.split("\\r?\\n")){
            line=line.trim();
            if(line.startsWith("## ")&&text.length()>0)break;
            if(!line.startsWith("- ")&&!line.startsWith("* "))continue;
            line=line.substring(2).replaceAll(" by @\\S+ in https://\\S+$","").replace("`","");
            if(++count>10)break;
            text.append("• ").append(line).append('\n');
        }
        if(text.length()==0){
            String clean=body.replaceAll("(?m)^#+\\s*","").replace("`","").trim();
            return clean.length()>1400?clean.substring(0,1400)+"…":clean;
        }
        return text.toString().trim();
    }
    static int compare(String a,String b){return ReleaseVersion.compare(a,b);}
}
