package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import com.github.catvod.utils.Auth;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final ConcurrentHashMap<String, String> userMap;

    public AuthInterceptor() {
        userMap = new ConcurrentHashMap<>();
    }

    public void clear() {
        userMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String injected = original.url().uri().getUserInfo();
        Response response = chain.proceed(injected == null ? original : check(original));
        if (response.code() != 401) return response;
        // 重定向在 chain.proceed 内部完成：挑战来自最终响应请求，不是这里收到的原始请求。
        Request challenged = response.request();
        // 调用方自己带的凭据属于它的保护空间，不拿记住的凭据覆盖。
        if (injected == null && challenged.header(HttpHeaders.AUTHORIZATION) != null) return response;
        String user = challenged.url().uri().getUserInfo();
        if (user == null) user = userMap.get(origin(challenged.url()));
        if (user == null) return response;
        response.close();
        String header = response.header(HttpHeaders.WWW_AUTHENTICATE);
        String auth = digest(header) ? Auth.digest(user, header, challenged) : Auth.basic(user);
        return chain.proceed(challenged.newBuilder().header(HttpHeaders.AUTHORIZATION, auth).build());
    }

    /** 认证作用域是来源（协议 + 主机 + 有效端口），不是主机名：不同端口/协议是不同保护空间。 */
    private static String origin(HttpUrl url) {
        return url.scheme() + "://" + url.host() + ":" + url.port();
    }

    private boolean digest(String header) {
        return header != null && header.startsWith("Digest");
    }

    private Request check(Request request) {
        URI uri = request.url().uri();
        if (uri.getUserInfo() == null) return request;
        userMap.put(origin(request.url()), uri.getUserInfo());
        return request.newBuilder().header(HttpHeaders.AUTHORIZATION, Auth.basic(uri.getUserInfo())).build();
    }
}
