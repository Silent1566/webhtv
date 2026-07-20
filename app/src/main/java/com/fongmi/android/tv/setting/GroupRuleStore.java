package com.fongmi.android.tv.setting;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.GroupRule;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GroupRuleStore {

    public static final String USER_KEY = "user_group_rules";
    public static final String DISABLED_KEY = "disabled_group_rule_ids";

    private static final Type LIST_TYPE = new TypeToken<List<GroupRule>>() {}.getType();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();

    private GroupRuleStore() {
    }

    public static synchronized List<GroupRule> loadUser() {
        String json = Prefers.getString(USER_KEY, "[]");
        try {
            List<GroupRule> rules = App.gson().fromJson(json, LIST_TYPE);
            if (rules == null) return new ArrayList<>();
            for (GroupRule rule : rules) {
                if (rule == null) continue;
                rule.setSource(GroupRule.SOURCE_USER);
            }
            return new ArrayList<>(rules);
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void saveUser(List<GroupRule> rules) {
        Prefers.put(USER_KEY, App.gson().toJson(rules == null ? new ArrayList<>() : rules));
        GroupRuleConfig.invalidate();
    }

    public static synchronized void addUser(GroupRule rule) {
        if (rule == null) return;
        rule.setSource(GroupRule.SOURCE_USER);
        List<GroupRule> rules = loadUser();
        rules.add(rule);
        saveUser(rules);
    }

    public static synchronized void updateUser(GroupRule rule) {
        if (rule == null || TextUtils.isEmpty(rule.getId())) return;
        List<GroupRule> rules = loadUser();
        for (int i = 0; i < rules.size(); i++) {
            if (rule.getId().equals(rules.get(i).getId())) {
                rules.set(i, rule);
                saveUser(rules);
                return;
            }
        }
    }

    public static synchronized void deleteUser(String id) {
        if (TextUtils.isEmpty(id)) return;
        List<GroupRule> rules = loadUser();
        rules.removeIf(item -> id.equals(item.getId()));
        saveUser(rules);
    }

    public static synchronized Map<String, Boolean> loadDisabled() {
        String json = Prefers.getString(DISABLED_KEY, "{}");
        try {
            Map<String, Boolean> value = App.gson().fromJson(json, MAP_TYPE);
            return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        } catch (Throwable e) {
            return new LinkedHashMap<>();
        }
    }

    public static synchronized void setEnabled(String id, boolean enabled) {
        if (TextUtils.isEmpty(id)) return;
        Map<String, Boolean> disabled = loadDisabled();
        if (enabled) disabled.remove(id);
        else disabled.put(id, true);
        Prefers.put(DISABLED_KEY, App.gson().toJson(disabled));
        GroupRuleConfig.invalidate();
    }

    public static boolean isDisabled(String id) {
        return Boolean.TRUE.equals(loadDisabled().get(id));
    }
}
