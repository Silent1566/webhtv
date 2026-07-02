package com.fongmi.android.tv.gitcloud.provider;

import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.gitcloud.AccountInfo;
import com.fongmi.android.tv.gitcloud.CreateRepoRequest;
import com.fongmi.android.tv.gitcloud.DownloadRef;
import com.fongmi.android.tv.gitcloud.GitAccount;
import com.fongmi.android.tv.gitcloud.GitBranch;
import com.fongmi.android.tv.gitcloud.GitCloudException;
import com.fongmi.android.tv.gitcloud.GitFile;
import com.fongmi.android.tv.gitcloud.GitFileContent;
import com.fongmi.android.tv.gitcloud.GitHttpException;
import com.fongmi.android.tv.gitcloud.GitProviderType;
import com.fongmi.android.tv.gitcloud.GitRepo;
import com.fongmi.android.tv.gitcloud.ProviderCapabilities;
import com.fongmi.android.tv.gitcloud.SaveOptions;
import com.fongmi.android.tv.gitcloud.SaveResult;
import com.fongmi.android.tv.web.GitRawUrlResolver;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Request;

public class CnbProvider extends BaseGitProvider {

    @Override
    public GitProviderType type() {
        return GitProviderType.CNB;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities()
                .createPrivateRepo(true)
                .contentsWrite(false)
                .releaseAsset(false)
                .archive(true)
                .raw(true)
                .pagination(true)
                .jgitWrite(true);
    }

    @Override
    protected void headers(Request.Builder builder, String token) {
        super.headers(builder, token);
        builder.header("Accept", "application/vnd.cnb.api+json");
    }

    @Override
    public AccountInfo validateToken(GitAccount account, String token) throws GitCloudException {
        JsonObject object = get(api() + "/user", token);
        String username = first(object, "username", "login", "name", "slug");
        return new AccountInfo(first(object, "id", "uid"), username, first(object, "nickname", "displayName", "name"), first(object, "avatar_url", "avatarUrl"), web(account) + "/" + username);
    }

    @Override
    public List<GitRepo> listRepos(GitAccount account, String token) throws GitCloudException {
        List<GitRepo> repos = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            JsonArray array = getArray(api() + "/user/repos?page=" + page + "&page_size=100", token);
            if (array.size() == 0) break;
            for (JsonElement element : array) if (element.isJsonObject()) repos.add(repo(account, element.getAsJsonObject()));
            if (array.size() < 100) break;
        }
        return repos;
    }

    @Override
    public List<GitRepo> searchRepos(GitAccount account, String token, String keyword) throws GitCloudException {
        if (TextUtils.isEmpty(keyword)) throw new GitCloudException("搜索关键词为空");
        if (TextUtils.isEmpty(token)) throw new GitCloudException("CNB 搜索需要 token 权限 repo-basic-info:r");
        List<GitRepo> result = new ArrayList<>();
        JsonArray array = getArray(api() + "/search/public-repos?key=" + enc(keyword) + "&topN=100", token);
        for (JsonElement element : array) if (element.isJsonObject()) result.add(repo(account, element.getAsJsonObject()));
        return result;
    }

    @Override
    public GitRepo getRepo(GitAccount account, String token, String fullName) throws GitCloudException {
        if (TextUtils.isEmpty(fullName)) throw new GitCloudException("仓库地址为空");
        GitRepo request = new GitRepo();
        request.providerType = GitProviderType.CNB;
        request.fullName = fullName.replaceAll("^/+", "").replaceAll("/+$", "");
        int split = request.fullName.indexOf('/');
        request.owner = split > 0 ? request.fullName.substring(0, split) : account.username;
        request.name = split > 0 ? request.fullName.substring(request.fullName.lastIndexOf('/') + 1) : request.fullName;
        try {
            return repo(account, get(repoApi(request), token));
        } catch (GitCloudException e) {
            request.cloneUrl = web(account) + "/" + request.fullName + ".git";
            request.webUrl = web(account) + "/" + request.fullName;
            request.defaultBranch = "main";
            return request;
        }
    }

    @Override
    public GitRepo createRepo(GitAccount account, String token, CreateRepoRequest request) throws GitCloudException {
        if (TextUtils.isEmpty(account.username)) throw new GitCloudException("CNB 创建仓库需要先校验账号");
        JsonObject payload = new JsonObject();
        payload.addProperty("name", request.name);
        payload.addProperty("description", request.description == null ? "" : request.description);
        payload.addProperty("visibility", request.privateRepo ? "private" : "public");
        JsonObject object = post(api() + "/" + encPath(account.username) + "/-/repos", token, payload);
        GitRepo repo = repo(account, object);
        GitRepo found = findCreatedRepo(account, token, request.name);
        if (found != null) repo = found;
        normalizeCreatedRepo(account, request, repo);
        if (TextUtils.isEmpty(repo.defaultBranch)) repo.defaultBranch = "main";
        debug("CNB create parsed keys=" + object.keySet() + " requestName=" + request.name + " owner=" + repo.owner + " name=" + repo.name + " fullName=" + repo.fullName + " branch=" + repo.defaultBranch + " cloneUrl=" + repo.cloneUrl);
        return repo;
    }

    @Override
    public void deleteRepo(GitAccount account, String token, GitRepo repo) throws GitCloudException {
        delete(repoApi(repo), token);
    }

    @Override
    public List<GitBranch> listBranches(GitAccount account, String token, GitRepo repo) throws GitCloudException {
        JsonArray array = getArray(repoApi(repo) + "/-/git/branches?page=1&page_size=100", token);
        List<GitBranch> branches = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String name = first(object, "name", "branch");
            branches.add(new GitBranch(name, first(obj(object, "commit"), "sha", "id"), TextUtils.equals(name, repo.defaultBranch)));
        }
        return branches;
    }

    @Override
    public List<GitFile> listFiles(GitAccount account, String token, GitRepo repo, String ref, String path) throws GitCloudException {
        try {
            String branch = branch(account, token, repo, ref);
            String url = repoApi(repo) + "/-/git/contents";
            if (!TextUtils.isEmpty(path)) url += "/" + encPath(path);
            if (!TextUtils.isEmpty(branch)) url += "?ref=" + enc(branch);
            JsonObject object = get(url, token);
            if (TextUtils.isEmpty(path) && "empty".equals(str(object, "type"))) return new ArrayList<>();
            JsonArray array = "tree".equals(str(object, "type")) ? array(object, "entries") : new JsonArray();
            if (!"tree".equals(str(object, "type"))) array.add(object);
            List<GitFile> files = new ArrayList<>();
            for (JsonElement element : array) if (element.isJsonObject()) files.add(file(account, repo, branch, element.getAsJsonObject()));
            files.sort((a, b) -> a.directory == b.directory ? a.name.compareToIgnoreCase(b.name) : a.directory ? -1 : 1);
            return files;
        } catch (GitHttpException e) {
            if (e.code == 404 && TextUtils.isEmpty(path)) return new ArrayList<>();
            throw e;
        }
    }

    @Override
    public GitFileContent readFile(GitAccount account, String token, GitRepo repo, String ref, String path) throws GitCloudException {
        String branch = branch(account, token, repo, ref);
        String url = repoApi(repo) + "/-/git/contents/" + encPath(path);
        if (!TextUtils.isEmpty(branch)) url += "?ref=" + enc(branch);
        JsonObject object = get(url, token);
        GitFileContent content = new GitFileContent();
        content.file = file(account, repo, branch, object);
        if (TextUtils.isEmpty(content.file.path)) content.file.path = path;
        if (TextUtils.isEmpty(content.file.name)) content.file.name = name(path);
        String encoded = str(object, "content").replace("\n", "");
        String encoding = str(object, "encoding");
        content.data = "base64".equalsIgnoreCase(encoding) && !TextUtils.isEmpty(encoded) ? Base64.decode(encoded, Base64.DEFAULT) : encoded.getBytes(StandardCharsets.UTF_8);
        content.text = new String(content.data, StandardCharsets.UTF_8);
        return content;
    }

    @Override
    public SaveResult saveSmallFile(GitAccount account, String token, GitRepo repo, String branch, String path, byte[] data, SaveOptions options) throws GitCloudException {
        throw new GitCloudException("CNB 文件写入使用同步引擎完成");
    }

    @Override
    public String rawUrl(GitAccount account, GitRepo repo, String ref, String path) {
        String branch = TextUtils.isEmpty(ref) ? repo.defaultBranch : ref;
        return GitRawUrlResolver.cnb(web(account), repo.owner, repo.name, branch, path);
    }

    @Override
    public DownloadRef archiveUrl(GitAccount account, String token, GitRepo repo, String ref, String path) {
        String branch = TextUtils.isEmpty(ref) ? repo.defaultBranch : ref;
        return new DownloadRef(web(account) + "/" + repo.fullName + "/-/archive/" + branch + ".zip", Map.of("Authorization", "Bearer " + token));
    }

    private GitRepo repo(GitAccount account, JsonObject object) {
        GitRepo repo = new GitRepo();
        repo.providerType = GitProviderType.CNB;
        repo.name = first(object, "name", "repo", "repoName");
        repo.fullName = first(object, "full_name", "fullName", "path_with_namespace", "path");
        if (TextUtils.isEmpty(repo.fullName) && !TextUtils.isEmpty(account.username)) repo.fullName = account.username + "/" + repo.name;
        int split = repo.fullName.indexOf('/');
        repo.owner = split > 0 ? repo.fullName.substring(0, split) : account.username;
        repo.cloneUrl = first(object, "clone_url", "cloneUrl", "http_url_to_repo");
        if (TextUtils.isEmpty(repo.cloneUrl)) repo.cloneUrl = web(account) + "/" + repo.fullName + ".git";
        repo.webUrl = first(object, "web_url", "webUrl", "html_url");
        if (TextUtils.isEmpty(repo.webUrl)) repo.webUrl = web(account) + "/" + repo.fullName;
        repo.defaultBranch = first(object, "default_branch", "defaultBranch");
        repo.privateRepo = bool(object, "private") || "private".equalsIgnoreCase(first(object, "visibility", "visibility_level"));
        repo.sizeKb = integer(object, "size");
        return repo;
    }

    private GitRepo findCreatedRepo(GitAccount account, String token, String name) {
        try {
            for (GitRepo item : listRepos(account, token)) {
                if (TextUtils.equals(item.owner, account.username) && TextUtils.equals(item.name, name)) return item;
                if (TextUtils.equals(item.fullName, account.username + "/" + name)) return item;
            }
        } catch (Throwable e) {
            debug("CNB create lookup failed: " + e.getMessage());
        }
        return null;
    }

    private void normalizeCreatedRepo(GitAccount account, CreateRepoRequest request, GitRepo repo) {
        if (TextUtils.isEmpty(repo.name)) repo.name = request.name;
        if (TextUtils.isEmpty(repo.owner)) repo.owner = account.username;
        if (TextUtils.isEmpty(repo.fullName) || repo.fullName.endsWith("/")) repo.fullName = repo.owner + "/" + repo.name;
        if (TextUtils.isEmpty(repo.cloneUrl) || repo.cloneUrl.endsWith("/.git")) repo.cloneUrl = web(account) + "/" + repo.fullName + ".git";
        if (TextUtils.isEmpty(repo.webUrl) || repo.webUrl.endsWith("/")) repo.webUrl = web(account) + "/" + repo.fullName;
        if (TextUtils.isEmpty(repo.defaultBranch)) repo.defaultBranch = "main";
        repo.privateRepo = repo.privateRepo || request.privateRepo;
    }

    private GitFile file(GitAccount account, GitRepo repo, String ref, JsonObject object) {
        GitFile file = new GitFile();
        file.name = first(object, "name", "file_name");
        file.path = first(object, "path", "file_path");
        file.directory = "dir".equals(first(object, "type")) || "tree".equals(first(object, "type")) || bool(object, "directory");
        file.size = integer(object, "size");
        file.sha = first(object, "sha", "id");
        file.downloadUrl = first(object, "download_url", "downloadUrl");
        file.webUrl = first(object, "web_url", "webUrl");
        file.rawUrl = rawUrl(account, repo, ref, file.path);
        return file;
    }

    private String first(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = str(object, key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String name(String path) {
        if (TextUtils.isEmpty(path)) return "";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String branch(GitAccount account, String token, GitRepo repo, String ref) throws GitCloudException {
        if (!TextUtils.isEmpty(ref)) return ref;
        if (!TextUtils.isEmpty(repo.defaultBranch)) return repo.defaultBranch;
        List<GitBranch> branches;
        try {
            branches = listBranches(account, token, repo);
        } catch (GitHttpException e) {
            if (e.code != 404) throw e;
            repo.defaultBranch = "main";
            return repo.defaultBranch;
        }
        if (branches.isEmpty()) {
            repo.defaultBranch = "main";
            return repo.defaultBranch;
        }
        repo.defaultBranch = branches.get(0).name;
        return repo.defaultBranch;
    }

    private String api() {
        return "https://api.cnb.cool";
    }

    private String repoApi(GitRepo repo) {
        return api() + "/" + encPath(repo.fullName);
    }

    private String web(GitAccount account) {
        return TextUtils.isEmpty(account.normalizedBaseUrl()) ? "https://cnb.cool" : account.normalizedBaseUrl();
    }
}
