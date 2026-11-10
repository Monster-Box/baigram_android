package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Dynamic WebAuthn relying-party config, served per server id by the worker:
// https://server-worker.pages.dev/{server_id}/passkey.json
// The APK ships only the fallback values below; the worker stays authoritative.
public class PasskeyConfig {

    public static final String DEFAULT_RP_ID = "server-worker.pages.dev";
    public static final String DEFAULT_ORIGIN = "https://server-worker.pages.dev";

    private static final String REQUEST_URL = "https://server-worker.pages.dev/%s/passkey.json";
    private static final long CACHE_TTL = 60 * 60 * 1000L;

    private static final String KEY_SERVER = "passkey_server_id";
    private static final String KEY_RP_ID = "passkey_rp_id";
    private static final String KEY_ORIGIN = "passkey_origin";
    private static final String KEY_TIME = "passkey_fetch_time";

    public final String serverId;
    public final String rpId;
    public final String origin;

    private PasskeyConfig(String serverId, String rpId, String origin) {
        this.serverId = serverId;
        this.rpId = rpId;
        this.origin = origin;
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(rpId) && !TextUtils.isEmpty(origin);
    }

    public static PasskeyConfig defaults(String serverId) {
        return new PasskeyConfig(serverId, DEFAULT_RP_ID, DEFAULT_ORIGIN);
    }

    // Blocking; call from Utilities.globalQueue.
    public static PasskeyConfig load(int account) {
        final String serverId = currentServerId(account);
        if (TextUtils.isEmpty(serverId)) {
            return defaults("");
        }
        final PasskeyConfig cached = cached(serverId);
        if (cached != null) {
            return cached;
        }
        final PasskeyConfig fresh = request(serverId);
        if (fresh != null) {
            save(fresh);
            return fresh;
        }
        return defaults(serverId);
    }

    private static String currentServerId(int account) {
        ServerConfig server = ServerConfig.read(account);
        return server != null ? server.id : null;
    }

    private static PasskeyConfig cached(String serverId) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (!serverId.equals(preferences.getString(KEY_SERVER, null))) {
            return null;
        }
        if (System.currentTimeMillis() - preferences.getLong(KEY_TIME, 0) >= CACHE_TTL) {
            return null;
        }
        String rpId = preferences.getString(KEY_RP_ID, null);
        String origin = preferences.getString(KEY_ORIGIN, null);
        if (TextUtils.isEmpty(rpId) || TextUtils.isEmpty(origin)) {
            return null;
        }
        return new PasskeyConfig(serverId, rpId, origin);
    }

    private static PasskeyConfig request(String serverId) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(String.format(REQUEST_URL, URLEncoder.encode(serverId, "UTF-8")));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            try (InputStream stream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            JSONObject object = new JSONObject(builder.toString());
            if (object.optInt("code", -1) != 0) {
                return null;
            }
            String rpId = object.optString("rp_id");
            String origin = object.optString("origin");
            if (TextUtils.isEmpty(rpId) || TextUtils.isEmpty(origin)) {
                return null;
            }
            return new PasskeyConfig(serverId, rpId, origin);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void save(PasskeyConfig config) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_SERVER, config.serverId)
                .putString(KEY_RP_ID, config.rpId)
                .putString(KEY_ORIGIN, config.origin)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .apply();
    }
}
