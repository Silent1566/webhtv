package com.fongmi.android.tv.smb;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * SMB 凭据的加密存储：AndroidKeyStore 的 AES/GCM 密钥加密后放进独立 SharedPreferences，
 * 结构与 {@code gitcloud.secure.GitCloudTokenStore} 一致，因此无需引入新的加密依赖。
 *
 * <p>没有 AndroidKeyStore 的 provider 时（部分模拟器/精简 ROM）退回到
 * `noBackupFilesDir` 下的随机文件密钥，并用 {@link #LOCAL_PREFIX} 标记：这保证可用性，
 * 不等价于硬件级密钥保护。
 *
 * <p>键是 {@link SmbUriResolver#hostKey(String)}（host + 共享名，host 已小写归一）。
 * 已知限制：同一 host 的同一共享若用不同端口，凭据会共用——端口不进键。
 */
public final class SmbCredentialStore {

    private static final String PREF = "smb_credentials_v1";
    private static final String ALIAS = "webhtv_smb_credential";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String LOCAL_PREFIX = "local-v1:";
    private static final String LOCAL_KEY_FILE = "smb_credential.key";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private SmbCredentialStore() {
    }

    public static void put(String hostKey, SmbCredential credential) throws Exception {
        if (TextUtils.isEmpty(hostKey) || credential == null || credential.isEmpty()) return;
        prefs().edit().putString(hostKey, encrypt(App.get(), SmbCredentialCodec.encode(credential))).apply();
    }

    /** 读不到或解不开都返回 null：浏览层按"需要重新输入账号"处理。 */
    @Nullable
    public static SmbCredential get(String hostKey) {
        if (TextUtils.isEmpty(hostKey)) return null;
        String value = prefs().getString(hostKey, "");
        if (TextUtils.isEmpty(value)) return null;
        try {
            return SmbCredentialCodec.decode(decrypt(App.get(), value));
        } catch (Throwable e) {
            return null;
        }
    }

    public static void remove(String hostKey) {
        if (TextUtils.isEmpty(hostKey)) return;
        prefs().edit().remove(hostKey).apply();
    }

    public static List<String> hostKeys() {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs().getAll().entrySet()) {
            if (!TextUtils.isEmpty(entry.getKey()) && entry.getValue() instanceof String) keys.add(entry.getKey());
        }
        return keys;
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String encrypt(Context context, String text) throws Exception {
        SecretKey secret;
        String prefix = "";
        try {
            secret = key();
        } catch (KeyStoreException error) {
            // 缺少 OEM provider 与"系统密钥存在但不可用"是两件事：后者必须照旧报错。
            if (Security.getProvider(ANDROID_KEYSTORE) != null) throw error;
            secret = localKey(context, true);
            prefix = LOCAL_PREFIX;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        byte[] iv = cipher.getIV();
        byte[] body = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return prefix + Base64.encodeToString(out, Base64.NO_WRAP);
    }

    static String decrypt(Context context, String value) throws Exception {
        boolean local = value.startsWith(LOCAL_PREFIX);
        byte[] input = Base64.decode(local ? value.substring(LOCAL_PREFIX.length()) : value, Base64.DEFAULT);
        if (input.length <= IV_BYTES) return "";
        byte[] iv = new byte[IV_BYTES];
        byte[] body = new byte[input.length - IV_BYTES];
        System.arraycopy(input, 0, iv, 0, IV_BYTES);
        System.arraycopy(input, IV_BYTES, body, 0, body.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, local ? localKey(context, false) : key(), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
    }

    private static synchronized SecretKey localKey(Context context, boolean create) throws Exception {
        File path = new File(context.getNoBackupFilesDir(), LOCAL_KEY_FILE);
        AtomicFile file = new AtomicFile(path);
        byte[] bytes;
        try {
            bytes = file.readFully();
        } catch (FileNotFoundException error) {
            if (!create || path.exists() || new File(path + ".bak").exists()) {
                throw new KeyStoreException("SMB 本地密钥不可用，请重新输入账号", error);
            }
            bytes = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(bytes);
            FileOutputStream output = file.startWrite();
            try {
                output.write(bytes);
                output.getFD().sync();
                file.finishWrite(output);
            } catch (Exception failure) {
                file.failWrite(output);
                throw failure;
            }
            // 只有确认密钥真的落盘，才允许后续写入凭据。
            if (!Arrays.equals(bytes, file.readFully())) throw new KeyStoreException("SMB 本地密钥保存失败");
        }
        if (bytes.length != KEY_BYTES) throw new KeyStoreException("SMB 本地密钥损坏，请重新输入账号");
        return new SecretKeySpec(bytes, "AES");
    }

    private static synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
