package com.fongmi.android.tv.smb;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 凭据的存储载荷编解码。只承担"字符串 ↔ 凭据"的转换，加密由
 * {@link SmbCredentialStore} 负责；单独拆出来是为了能在纯 JVM 单测里覆盖。
 */
public final class SmbCredentialCodec {

    private SmbCredentialCodec() {
    }

    public static String encode(SmbCredential credential) {
        JsonObject object = new JsonObject();
        object.addProperty("username", credential.username);
        object.addProperty("password", credential.password);
        object.addProperty("domain", credential.domain);
        return object.toString();
    }

    /** 解析失败或缺用户名时返回 null；调用方按"没有可用凭据"处理。 */
    @Nullable
    public static SmbCredential decode(String payload) {
        if (payload == null || payload.trim().isEmpty()) return null;
        try {
            JsonObject object = JsonParser.parseString(payload).getAsJsonObject();
            String username = object.has("username") ? object.get("username").getAsString().trim() : "";
            if (username.isEmpty()) return null;
            String password = object.has("password") ? object.get("password").getAsString() : "";
            String domain = object.has("domain") ? object.get("domain").getAsString() : "";
            return SmbCredential.of(username, password, domain);
        } catch (Throwable e) {
            return null;
        }
    }
}
