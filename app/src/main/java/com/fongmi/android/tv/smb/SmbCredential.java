package com.fongmi.android.tv.smb;

import android.text.TextUtils;

/**
 * 一组 SMB 登录凭据。按 host + 共享名（{@link SmbUriResolver#hostKey(String)}）保存，
 * 因此同一台 NAS 的不同共享可以各自使用不同账号。
 *
 * <p>不可变值对象；{@link #toString()} 故意不输出密码，便于安全地写日志或异常信息。
 */
public final class SmbCredential {

    public final String username;
    public final String password;
    public final String domain;

    private SmbCredential(String username, String password, String domain) {
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.domain = domain == null ? "" : domain;
    }

    public static SmbCredential of(String username, String password, String domain) {
        return new SmbCredential(username, password, domain);
    }

    /** 无用户名的凭据等同 guest：浏览可以匿名，但保存它没有意义。 */
    public boolean isEmpty() {
        return TextUtils.isEmpty(username.trim());
    }

    @Override
    public String toString() {
        return "SmbCredential{username=" + username + ", domain=" + domain
                + ", password=" + (TextUtils.isEmpty(password) ? "<empty>" : "<redacted>") + "}";
    }
}
