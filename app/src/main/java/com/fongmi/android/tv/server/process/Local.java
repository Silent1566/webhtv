package com.fongmi.android.tv.server.process;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;
import static fi.iki.elonen.NanoHTTPD.getMimeTypeForFile;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.SafeZip;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.utils.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.zip.CRC32;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Local implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/file") || url.startsWith("/upload") || url.startsWith("/newFolder") || url.startsWith("/delFolder") || url.startsWith("/delFile");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (url.startsWith("/file")) return getFile(session, url);
        if (url.startsWith("/upload")) return upload(session.getParms(), files);
        if (url.startsWith("/newFolder")) return newFolder(session.getParms());
        if (url.startsWith("/delFolder") || url.startsWith("/delFile")) return delete(session.getParms());
        return null;
    }

    private Response getFile(IHTTPSession session, String url) {
        Map<String, String> headers = session.getHeaders();
        try {
            File file = resolveReadable(session, url.substring(5));
            if (file.isDirectory()) return getFolder(file);
            if (file.isFile()) return getFile(headers, session.getParms(), file, getMimeTypeForFile(url));
            return Nano.error(Status.NOT_FOUND, "File not found");
        } catch (SecurityException e) {
            return Nano.error(Status.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    /**
     * 只有真实回环连接可读共享根之外的内部缓存文件；其他对端必须落在共享根内。
     * 对端地址取自连接本身，不采信任何转发请求头。
     */
    private static File resolveReadable(IHTTPSession session, String requested) throws IOException {
        File rootDir = Path.root().getCanonicalFile();
        if (requested.indexOf(58) < 0) {
            String name = requested;
            if (name.startsWith("/")) name = name.substring(1);
            File shared = contained(rootDir, new File(rootDir, name));
            if (shared != null) return shared;
        }
        File external = Path.local(requested).getCanonicalFile();
        if (isLoopback(session)) return external;
        throw new SecurityException("Path outside shared storage");
    }

    /** 存在时用真实路径比对，能识别符号链接越界；不存在时退回 canonical 字面判定。 */
    private static File contained(File root, File candidate) throws IOException {
        File resolved = candidate.exists() ? candidate.toPath().toRealPath().toFile() : candidate.getCanonicalFile();
        String rootPath = root.toPath().toRealPath().toString();
        String target = resolved.getPath();
        return target.equals(rootPath) || target.startsWith(rootPath + File.separator) ? resolved : null;
    }

    private static boolean isLoopback(IHTTPSession session) {
        String address = session.getRemoteIpAddress();
        if (address == null) return false;
        // 只接受 IP 字面量，避免 localhost 之类的主机名经 DNS 解析获得回环权限。
        if (!address.matches("[0-9A-Fa-f:.\\[\\]]+")) return false;
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWithinRoot(File file) throws IOException {
        String root = Path.root().getCanonicalPath();
        String target = file.getCanonicalPath();
        return target.equals(root) || target.startsWith(root + File.separator);
    }

    private Response getFile(Map<String, String> headers, Map<String, String> params, File file, String mime) throws IOException {
        Response response = getFile(headers, file, mime);
        if (isDownload(params)) addDownloadHeader(response, file.getName());
        return response;
    }

    private Response upload(Map<String, String> params, Map<String, String> files) {
        try {
            File folder = uploadFolder(params.get("path"));
            for (String k : files.keySet()) {
                File target = contained(folder, params.get(k), "file name");
                File temp = new File(files.get(k));
                if (target.getName().toLowerCase().endsWith(".zip")) SafeZip.extractInto(temp, folder);
                else Path.copy(temp, target);
            }
            return Nano.ok();
        } catch (Exception e) {
            return Nano.error(Status.BAD_REQUEST, e.getMessage());
        }
    }

    private Response newFolder(Map<String, String> params) {
        try {
            File parent = contained(Path.root(), params.get("path"), "path");
            contained(parent, params.get("name"), "folder name").mkdirs();
            return Nano.ok();
        } catch (Exception e) {
            return Nano.error(Status.BAD_REQUEST, e.getMessage());
        }
    }

    private Response delete(Map<String, String> params) {
        try {
            File root = Path.root();
            File target = contained(root, params.get("path"), "path");
            if (target.equals(root.getCanonicalFile())) throw new IOException("Refusing to delete the shared root");
            Path.clear(target);
            return Nano.ok();
        } catch (Exception e) {
            return Nano.error(Status.BAD_REQUEST, e.getMessage());
        }
    }

    private static File uploadFolder(String path) throws IOException {
        File folder = contained(Path.root(), path, "path");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create upload folder");
        return folder;
    }

    /**
     * 所有本地端点参数都必须解析到共享目录内部：空值或缺失按“目录本身”处理，
     * 因此这里显式拒绝空名，避免把根目录当成删除或写入目标。
     */
    private static File contained(File root, String name, String label) throws IOException {
        if (name == null || name.trim().isEmpty()) throw new IOException("Missing " + label);
        File canonicalRoot = root.getCanonicalFile();
        File target = new File(canonicalRoot, name).getCanonicalFile();
        if (!target.equals(canonicalRoot) && !target.getPath().startsWith(canonicalRoot.getPath() + File.separator))
            throw new IOException(label + " escapes the shared root");
        return target;
    }

    private Response getFolder(File dir) {
        File rootDir = Path.root();
        String rootPath = rootDir.getAbsolutePath();
        JsonArray files = new JsonArray();
        for (File file : Path.list(dir)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", file.getName());
            obj.addProperty("path", relativeTo(file, rootPath));
            obj.addProperty("time", Formatters.LOCAL_DATETIME.format(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault())));
            obj.addProperty("dir", file.isDirectory() ? 1 : 0);
            obj.addProperty("size", file.isDirectory() ? 0 : file.length());
            files.add(obj);
        }
        JsonObject info = new JsonObject();
        info.addProperty("parent", parentOf(dir, rootDir, rootPath));
        info.add("files", files);
        return Nano.ok(info.toString());
    }

    private Response getFile(Map<String, String> headers, File file, String mime) throws IOException {
        long fileLen = file.length();
        String etag = etag(file, fileLen);
        String ifNoneMatch = headers.get("if-none-match");
        if (ifNoneMatch != null && (ifNoneMatch.equals("*") || ifNoneMatch.equals(etag))) {
            return newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
        }
        HttpRange range = HttpRange.from(fileLen, headers, etag);
        if (!range.valid()) return createRangeNotSatisfiableResponse(fileLen);
        FileInputStream fis = new FileInputStream(file);
        skip(fis, range.start);
        Response res;
        if (range.isPartial(fileLen)) {
            res = newFixedLengthResponse(Status.PARTIAL_CONTENT, mime, fis, range.length);
            res.addHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileLen);
        } else {
            res = newFixedLengthResponse(Status.OK, mime, fis, range.length);
        }
        res.addHeader("Content-Length", String.valueOf(range.length));
        res.addHeader("Accept-Ranges", "bytes");
        res.addHeader("ETag", etag);
        return res;
    }

    private boolean isDownload(Map<String, String> params) {
        String value = params.get("download");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private void addDownloadHeader(Response response, String name) {
        String fallback = name.replaceAll("[\\\\\"\\r\\n]", "_");
        String encoded;
        try {
            encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            encoded = fallback;
        }
        response.addHeader("Content-Disposition", "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded);
        response.addHeader("X-Content-Type-Options", "nosniff");
    }

    private String etag(File file, long fileLen) {
        CRC32 crc = new CRC32();
        crc.update((file.getAbsolutePath() + file.lastModified() + fileLen).getBytes());
        return Long.toHexString(crc.getValue());
    }

    private Response createRangeNotSatisfiableResponse(long fileLen) {
        Response res = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
        res.addHeader("Content-Range", "bytes */" + fileLen);
        return res;
    }

    private void skip(InputStream is, long bytesToSkip) throws IOException {
        if (bytesToSkip <= 0) return;
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = is.skip(remaining);
            if (skipped <= 0) throw new IOException("Failed to skip desired number of bytes");
            remaining -= skipped;
        }
    }

    private static String relativeTo(File file, String rootPath) {
        String path = file.getAbsolutePath();
        return path.startsWith(rootPath) ? path.substring(rootPath.length()) : path;
    }

    private static String parentOf(File dir, File rootDir, String rootPath) {
        if (dir.equals(rootDir)) return ".";
        File parent = dir.getParentFile();
        if (parent == null || parent.equals(rootDir)) return "";
        return relativeTo(parent, rootPath);
    }

    private record HttpRange(long start, long end, long length, boolean valid) {

        public boolean isPartial(long total) {
            return length < total;
        }

        public static HttpRange invalid() {
            return new HttpRange(0, 0, 0, false);
        }

        public static HttpRange from(long fileLen, Map<String, String> headers, String etag) {
            long start = 0;
            long end = fileLen - 1;
            String rangeHeader = headers.get("range");
            String ifRange = headers.get("if-range");
            if (ifRange != null && !ifRange.equals(etag)) rangeHeader = null;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                try {
                    String[] parts = rangeHeader.substring(6).split("-", 2);
                    if (!parts[0].isEmpty()) start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
                    if (start >= fileLen || start > end) return invalid();
                } catch (NumberFormatException e) {
                    return invalid();
                }
            }
            if (end >= fileLen) end = fileLen - 1;
            return new HttpRange(start, end, end - start + 1, true);
        }
    }
}
