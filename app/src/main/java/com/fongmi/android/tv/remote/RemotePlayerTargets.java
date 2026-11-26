package com.fongmi.android.tv.remote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 遥控面板的目标解析：从已绑定的 profile 里挑出“另一台设备”。
 *
 * <p>纯逻辑，不依赖 Android，便于单测。规则：排除自己、排除空 deviceId、同一个 deviceId
 * 只保留首次出现的分组、在线设备排在离线设备前面（各组内保持原有顺序）。
 */
public final class RemotePlayerTargets {

    private RemotePlayerTargets() {
    }

    public static final class Target {

        public final RemoteModels.RemoteGroup group;
        public final RemoteModels.RemoteDevice device;

        Target(RemoteModels.RemoteGroup group, RemoteModels.RemoteDevice device) {
            this.group = group;
            this.device = device;
        }
    }

    public static List<Target> peers(RemoteModels.RemoteProfile profile) {
        List<Target> online = new ArrayList<>();
        List<Target> offline = new ArrayList<>();
        if (profile == null || profile.groups == null) return online;
        Map<String, Target> unique = new LinkedHashMap<>();
        for (RemoteModels.RemoteGroup group : profile.groups) {
            if (group == null || group.devices == null) continue;
            for (RemoteModels.RemoteDevice device : group.devices) {
                if (device == null) continue;
                String id = device.deviceId == null ? "" : device.deviceId.trim();
                if (id.isEmpty() || id.equals(profile.deviceId)) continue;
                if (unique.containsKey(id)) continue;
                unique.put(id, new Target(group, device));
            }
        }
        for (Target target : unique.values()) (target.device.online ? online : offline).add(target);
        online.addAll(offline);
        return online;
    }

    public static String label(Target target) {
        String name = target.device.name == null ? "" : target.device.name.trim();
        return name.isEmpty() ? target.device.deviceId : name;
    }
}
