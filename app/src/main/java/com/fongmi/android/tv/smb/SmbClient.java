package com.fongmi.android.tv.smb;

import androidx.annotation.Nullable;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读的 SMB 浏览客户端：列出共享内的目录，够浏览页与起播用。
 *
 * <p>范围刻意收窄：**不枚举共享列表**（那需要 MSRPC/SRVSVC，是本项目暂不引入的依赖面），
 * 共享名由用户输入或从最近使用里选择；写入/删除/复制同样不在范围内。
 *
 * <p>smbj 的调用是阻塞且不接受超时的，所以每次网络动作都经 {@link SmbTimeout} 有界等待，
 * 超时后关闭连接解除阻塞，再把 {@link java.util.concurrent.TimeoutException} 转成 IOException
 * 交给界面提示。
 */
public final class SmbClient implements Closeable {

    private static final long DEFAULT_TIMEOUT_MS = 8000L;

    /** 目录项；{@code path} 是共享内的相对路径。 */
    public static final class Entry {

        public final String name;
        public final String path;
        public final boolean directory;
        public final long size;

        Entry(String name, String path, boolean directory, long size) {
            this.name = name;
            this.path = path;
            this.directory = directory;
            this.size = size;
        }
    }

    private final String host;
    private final int port;
    private final SmbCredential credential;
    private final Map<String, DiskShare> shares = new HashMap<>();
    private com.hierynomus.smbj.SMBClient smbClient;
    private Connection connection;
    private Session session;
    private boolean closed;

    private SmbClient(String host, int port, SmbCredential credential) {
        this.host = host;
        this.port = port > 0 ? port : 445;
        this.credential = credential;
    }

    /** 建立连接；失败抛 IOException，调用方负责提示。 */
    public static SmbClient connect(String host, int port, @Nullable SmbCredential credential, long timeoutMs) throws IOException {
        String value = host == null ? "" : host.trim();
        if (value.isEmpty()) throw new IOException("SMB 主机地址为空");
        SmbClient client = new SmbClient(value, port, credential);
        try {
            SmbTimeout.call(() -> {
                client.smbClient = new com.hierynomus.smbj.SMBClient();
                client.connection = client.smbClient.connect(value, client.port);
                client.session = client.connection.authenticate(authentication(credential));
                return null;
            }, timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS, client::closeQuietly);
            return client;
        } catch (IOException e) {
            client.closeQuietly();
            throw e;
        } catch (Exception e) {
            client.closeQuietly();
            throw new IOException(messageOf(e), e);
        }
    }

    /** 列出共享内某个目录（空路径即共享根）；结果按"目录优先 + 名称不区分大小写"排序。 */
    public List<Entry> listDir(String shareName, @Nullable String dirPath, long timeoutMs) throws IOException {
        String share = SmbPath.normalize(shareName);
        if (share == null || share.isEmpty()) throw new IOException("SMB 共享名为空");
        String path = SmbPath.normalize(dirPath);
        if (path == null) throw new IOException("SMB 路径越界");
        try {
            return SmbTimeout.call(() -> list(share, path), timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS, this::closeQuietly);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(messageOf(e), e);
        }
    }

    private List<Entry> list(String shareName, String path) throws IOException {
        DiskShare share = share(shareName);
        List<FileIdBothDirectoryInformation> items = share.list(path);
        List<Entry> entries = new ArrayList<>();
        for (FileIdBothDirectoryInformation item : items) {
            String name = item.getFileName();
            if (name == null || ".".equals(name) || "..".equals(name)) continue;
            boolean directory = (item.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
            entries.add(new Entry(name, SmbPath.resolve(path, name), directory, item.getEndOfFile()));
        }
        Collections.sort(entries, (left, right) -> {
            if (left.directory != right.directory) return left.directory ? -1 : 1;
            return left.name.compareToIgnoreCase(right.name);
        });
        return entries;
    }

    private DiskShare share(String shareName) throws IOException {
        DiskShare cached = shares.get(shareName);
        if (cached != null) return cached;
        if (session == null) throw new IOException("SMB 会话已关闭");
        try {
            DiskShare share = (DiskShare) session.connectShare(shareName);
            shares.put(shareName, share);
            return share;
        } catch (Exception e) {
            throw new IOException("无法打开共享 " + shareName + "：" + messageOf(e), e);
        }
    }

    private static AuthenticationContext authentication(@Nullable SmbCredential credential) {
        if (credential == null || credential.isEmpty()) return AuthenticationContext.guest();
        String domain = credential.domain == null || credential.domain.isEmpty() ? null : credential.domain;
        return new AuthenticationContext(credential.username, credential.password.toCharArray(), domain);
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void closeQuietly() {
        close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (DiskShare share : shares.values()) {
            try {
                share.close();
            } catch (Throwable ignored) {
            }
        }
        shares.clear();
        try {
            if (session != null) session.close();
        } catch (Throwable ignored) {
        }
        try {
            if (connection != null) connection.close();
        } catch (Throwable ignored) {
        }
        try {
            if (smbClient != null) smbClient.close();
        } catch (Throwable ignored) {
        }
        session = null;
        connection = null;
        smbClient = null;
    }
}
