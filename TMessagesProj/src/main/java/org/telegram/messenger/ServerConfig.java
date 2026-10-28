package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ServerConfig {

    public static final int DATACENTER_ID = 2;

    private static final String REQUEST_URL = "https://server-worker.pages.dev/getserver?id=%s";
    private static final String KEY_ID = "server_id";
    private static final String KEY_IP = "server_ip";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_PUBKEY = "server_pubkey";
    private static final String KEY_NAME = "server_name";

    public final String id;
    public final String ip;
    public final String name;
    public final int port;
    public final String pubkey;

    private ServerConfig(String id, String ip, String name, int port, String pubkey) {
        this.id = id;
        this.ip = ip;
        this.name = name;
        this.port = port;
        this.pubkey = pubkey;
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(id) && !TextUtils.isEmpty(ip) && port > 0;
    }

    public boolean hasPubKey() {
        return !TextUtils.isEmpty(pubkey) && !"NULL".equals(pubkey);
    }

    public interface Callback {
        void onResult(ServerConfig config);

        void onError(String message);
    }

    public static void fetch(final String id, final Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            ServerConfig config = null;
            String error = null;
            try {
                config = request(id);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final ServerConfig result = config;
            final String message = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (result != null) {
                    callback.onResult(result);
                } else {
                    callback.onError(message != null ? message : "connection failed");
                }
            });
        });
    }

    private static ServerConfig request(String id) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(String.format(REQUEST_URL, URLEncoder.encode(id, "UTF-8")));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder builder = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                }
            }
            JSONObject object = new JSONObject(builder.toString());
            if (object.optInt("code", -1) != 0) {
                throw new Exception(object.optString("msg", "server error"));
            }
            String ip = object.optString("ip");
            int port = object.optInt("port");
            if (TextUtils.isEmpty(ip) || port <= 0) {
                throw new Exception("bad response");
            }
            return new ServerConfig(object.optString("query", id), ip, object.optString("name"), port, object.optString("pubkey"));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void save(int account, ServerConfig config) {
        SharedPreferences.Editor editor = UserConfig.getInstance(account).getPreferences().edit();
        if (config == null) {
            editor.remove(KEY_ID).remove(KEY_IP).remove(KEY_PORT).remove(KEY_PUBKEY).remove(KEY_NAME).apply();
            return;
        }
        editor.putString(KEY_ID, config.id)
                .putString(KEY_IP, config.ip)
                .putInt(KEY_PORT, config.port)
                .putString(KEY_PUBKEY, config.pubkey)
                .putString(KEY_NAME, config.name)
                .apply();
    }

    public static ServerConfig read(int account) {
        SharedPreferences preferences = UserConfig.getInstance(account).getPreferences();
        String id = preferences.getString(KEY_ID, null);
        String ip = preferences.getString(KEY_IP, null);
        int port = preferences.getInt(KEY_PORT, 0);
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(ip) || port <= 0) {
            return null;
        }
        return new ServerConfig(id, ip, preferences.getString(KEY_NAME, ""), port, preferences.getString(KEY_PUBKEY, ""));
    }

    public static void apply(int account, ServerConfig config) {
        if (config == null || !config.isValid()) {
            //fix: keep the address already stored in tgnet, native flag decides if it may connect
            return;
        }
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
        connectionsManager.setServerPublicKey(config.hasPubKey() ? config.pubkey : "");
        connectionsManager.applyDatacenterAddress(DATACENTER_ID, config.ip, config.port);
    }

    public static void applyNewBinding(int account, ServerConfig config) {
        if (config == null || !config.isValid()) {
            return;
        }
        ConnectionsManager.getInstance(account).cleanup(true);
        apply(account, config);
    }
}
