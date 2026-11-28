package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.UserAdRule;
import com.github.catvod.net.OkHttp;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 去广告规则跨设备迁移：导出为 JSON 文本（可进剪贴板/分享/托管），导入支持粘贴 JSON 或 http(s) 链接。
 * 用于把 TV 端积累的多条规则同步到手机端（或反向）。
 */
public final class AdRuleTransfer {

    private AdRuleTransfer() {
    }

    /** 导出全部用户规则为 JSON 文本。 */
    public static String export() {
        return App.gson().toJson(UserAdRuleStore.load());
    }

    /** 导出为带包头的 JSON 文本，便于识别与扩展。 */
    public static String exportPackage() {
        return App.gson().toJson(new Package(UserAdRuleStore.load()));
    }

    /**
     * 从 JSON 文本或 http(s) 链接导入规则（按 id 去重、跳过空规则），返回实际导入条数。
     *
     * @throws Exception 链接下载失败或格式无法解析时抛出
     */
    public static int importFrom(String payload) throws Exception {
        String text = payload == null ? "" : payload.trim();
        if (text.isEmpty()) throw new Exception("empty input");
        if (text.startsWith("http://") || text.startsWith("https://")) {
            text = OkHttp.string(text);
            if (text == null || text.trim().isEmpty()) throw new Exception("download failed");
            text = text.trim();
        }
        if (text.startsWith("{")) {
            Package pkg = App.gson().fromJson(text, Package.class);
            text = pkg != null && pkg.rules != null ? App.gson().toJson(pkg.rules) : text;
        }
        List<UserAdRule> incoming = App.gson().fromJson(text, new TypeToken<List<UserAdRule>>() {}.getType());
        if (incoming == null) throw new Exception("invalid format");
        return merge(incoming);
    }

    private static int merge(List<UserAdRule> incoming) throws Exception {
        List<UserAdRule> rules = UserAdRuleStore.load();
        Set<String> ids = rules.stream().map(UserAdRule::getId).collect(Collectors.toSet());
        int count = 0;
        for (UserAdRule rule : incoming) {
            if (rule == null) continue;
            if (rule.getHosts() == null) rule.setHosts(new ArrayList<>());
            if (rule.getRegex() == null) rule.setRegex(new ArrayList<>());
            if (rule.getExclude() == null) rule.setExclude(new ArrayList<>());
            if (rule.getHosts().isEmpty() && rule.getRegex().isEmpty() && rule.getExclude().isEmpty()) continue;
            if (rule.getId() != null && ids.contains(rule.getId())) continue;
            if (rule.getId() == null || rule.getId().isEmpty()) rule.setId(UUID.randomUUID().toString());
            rules.add(rule);
            ids.add(rule.getId());
            count++;
        }
        if (count > 0) UserAdRuleStore.save(rules);
        return count;
    }

    private static class Package {
        private final String type = "honey_ad_rules";
        private final List<UserAdRule> rules;

        private Package(List<UserAdRule> rules) {
            this.rules = rules;
        }
    }
}
