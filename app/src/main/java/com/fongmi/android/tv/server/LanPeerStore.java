package com.fongmi.android.tv.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.AtomicFile;
import android.util.Base64;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted storage for the paired-device registry.
 *
 * <p>Follows the account-token store pattern (AndroidKeyStore AES/GCM with a {@code local-v1:}
 * fallback for ROMs without the provider) but uses its own alias and key file, so LAN pairing
 * material never shares key material with cloud account tokens. Persistence itself is delegated
 * to {@link LanPeerPersist}; this class only supplies the two Android-specific seams.
 */
public final class LanPeerStore {

    private static final String PREF = "lan_peer_registry";
    private static final String KEY = "registry";
    private static final String KEY_ENABLED = "control_enabled";
    private static final String KEY_LOCAL_WRITE = "lan_local_write";
    private static final String ALIAS = "webhtv_lan_peer";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String LOCAL_PREFIX = "local-v1:";
    private static final String INHIBIT_FILE = "lan_control.pending";
    private static final String LOCAL_KEY_FILE = "lan_peer.key";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private LanPeerStore() {
    }

    public static Map<String, byte[]> load() {
        return persist().load();
    }

    public static void save(Map<String, byte[]> partners) throws Exception {
        persist().save(partners);
    }

    public static void clear() {
        commit(prefs().edit().remove(KEY));
    }

    /** Raw stored value, for diagnostics and tests; never contains device identifiers. */
    static String inspect() {
        return prefs().getString(KEY, "");
    }

    static void overwrite(String value) {
        commit(prefs().edit().putString(KEY, value));
    }

    public static boolean isControlEnabled() {
        return !controlInhibit().exists() && prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setControlEnabled(boolean enabled) {
        commit(prefs().edit().putBoolean(KEY_ENABLED, enabled));
    }

    /** Sensitive actions (config changes, sync, APK pushes) additionally need this local permission. */
    public static boolean isLocalWriteEnabled() {
        return prefs().getBoolean(KEY_LOCAL_WRITE, false);
    }

    public static void setLocalWriteEnabled(boolean enabled) {
        commit(prefs().edit().putBoolean(KEY_LOCAL_WRITE, enabled));
    }

    static LanPeerPersist persist() {
        LanPeerPersist.SecretBox box = new LanPeerPersist.SecretBox() {
            @Override
            public String seal(String plain) throws Exception {
                return encrypt(plain);
            }

            @Override
            public String open(String sealed) throws Exception {
                return decrypt(sealed);
            }
        };
        LanPeerPersist.ValueStore store = new LanPeerPersist.ValueStore() {
            @Override
            public String read() {
                return inspect();
            }

            @Override
            public void write(String value) {
                overwrite(value);
            }

            @Override
            public boolean isInhibited() {
                return controlInhibit().exists();
            }

            @Override
            public void inhibit() throws Exception {
                controlInhibit().write();
            }

            @Override
            public void clearInhibit() throws Exception {
                controlInhibit().clear();
            }
        };
        return new LanPeerPersist(box, store);
    }

    private static ControlInhibit controlInhibit() {
        return new ControlInhibit(new File(App.get().getNoBackupFilesDir(), INHIBIT_FILE));
    }

    static final class ControlInhibit {
        private final File path;
        private final File pending;
        private final File backup;
        private final AtomicFile file;

        ControlInhibit(File path) {
            this.path = path;
            pending = new File(path + ".new");
            backup = new File(path + ".bak");
            file = new AtomicFile(path);
        }

        boolean exists() {
            try {
                return present(path) || present(pending) || present(backup);
            } catch (ErrnoException unverifiable) {
                return true;
            }
        }

        void write() throws Exception {
            FileOutputStream out = file.startWrite();
            try {
                out.write(1);
                out.getFD().sync();
            } catch (Exception failure) {
                file.failWrite(out);
                throw failure;
            }
            file.finishWrite(out);
            if (!present(path)) throw new IOException("Could not persist LAN control interlock");
            syncDirectory();
        }

        void clear() throws Exception {
            file.delete();
            pending.delete();
            backup.delete();
            if (present(path) || present(pending) || present(backup)) throw new IOException("Could not clear LAN control interlock");
            syncDirectory();
        }

        private static boolean present(File file) throws ErrnoException {
            try {
                Os.stat(file.getPath());
                return true;
            } catch (ErrnoException error) {
                if (error.errno == OsConstants.ENOENT) return false;
                throw error;
            }
        }

        private void syncDirectory() throws ErrnoException {
            FileDescriptor fd = Os.open(path.getParent(), OsConstants.O_RDONLY, 0);
            try {
                Os.fsync(fd);
            } finally {
                Os.close(fd);
            }
        }
    }

    private static void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Could not persist LAN authorization");
    }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String encrypt(String text) throws Exception {
        if (text == null || text.length() > LanPeerRegistry.MAX_PLAIN_CHARS) throw new IllegalArgumentException("LAN payload too large");
        Context context = App.get();
        SecretKey secret;
        String prefix = "";
        try {
            secret = key();
        } catch (KeyStoreException error) {
            // A missing OEM provider is different from an existing, unusable system key.
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

    static String decrypt(String value) throws Exception {
        if (value == null || value.length() > LanPeerRegistry.MAX_SEALED_CHARS) throw new IllegalArgumentException("LAN ciphertext too large");
        Context context = App.get();
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
                throw new KeyStoreException("局域网配对密钥不可用，请重新配对设备", error);
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
            // AtomicFile reports some rename failures only through logs; never store a registry
            // until its key is confirmed on disk.
            if (!Arrays.equals(bytes, file.readFully())) throw new KeyStoreException("局域网配对密钥保存失败");
        }
        if (bytes.length != KEY_BYTES) throw new KeyStoreException("局域网配对密钥损坏，请重新配对设备");
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
