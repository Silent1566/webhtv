package com.fongmi.android.tv.remote;

import com.fongmi.android.tv.remote.RemoteModels.RemoteDevice;
import com.fongmi.android.tv.remote.RemoteModels.RemoteGroup;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemotePlayerTargetsTest {

    private static RemoteDevice device(String id, String name, boolean online) {
        RemoteDevice device = new RemoteDevice();
        device.deviceId = id;
        device.name = name;
        device.online = online;
        return device;
    }

    private static RemoteGroup group(String id, RemoteDevice... devices) {
        RemoteGroup group = new RemoteGroup();
        group.groupId = id;
        for (RemoteDevice device : devices) group.devices.add(device);
        return group;
    }

    private static RemoteProfile profile(String selfId, RemoteGroup... groups) {
        RemoteProfile profile = new RemoteProfile();
        profile.deviceId = selfId;
        for (RemoteGroup group : groups) profile.groups.add(group);
        return profile;
    }

    private static String[] ids(List<RemotePlayerTargets.Target> targets) {
        String[] ids = new String[targets.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = targets.get(i).device.deviceId;
        return ids;
    }

    @Test
    public void skipsSelfBlankAndMissingEntries() {
        RemoteGroup broken = group("g1", null, device("", "blank", true), device("  ", "spaces", true));
        RemoteGroup real = group("g2", device("tv-1", "客厅", true));
        List<RemotePlayerTargets.Target> targets = RemotePlayerTargets.peers(profile("phone", broken, null, real));
        assertEquals(1, targets.size());
        assertEquals("tv-1", targets.get(0).device.deviceId);
        assertEquals("g2", targets.get(0).group.groupId);
    }

    @Test
    public void keepsOnlyTheFirstGroupForADeviceSeenTwice() {
        RemoteGroup first = group("g1", device("tv-1", "客厅", true));
        RemoteGroup second = group("g2", device("tv-1", "客厅", true), device("tv-2", "卧室", false));
        List<RemotePlayerTargets.Target> targets = RemotePlayerTargets.peers(profile("phone", first, second));
        assertEquals(2, targets.size());
        assertEquals("g1", targets.get(0).group.groupId);
        assertEquals("tv-2", targets.get(1).device.deviceId);
    }

    @Test
    public void putsOnlineDevicesFirstWithoutReorderingWithinAGroup() {
        RemoteGroup group = group("g1",
                device("off-1", "离线1", false),
                device("on-1", "在线1", true),
                device("off-2", "离线2", false),
                device("on-2", "在线2", true));
        List<RemotePlayerTargets.Target> targets = RemotePlayerTargets.peers(profile("phone", group));
        assertEquals("[on-1, on-2, off-1, off-2]", String.join(", ", ids(targets)).replaceFirst("^", "[").replaceFirst("$", "]"));
    }

    @Test
    public void returnsAnEmptyListWhenNothingIsBound() {
        assertTrue(RemotePlayerTargets.peers(null).isEmpty());
        assertTrue(RemotePlayerTargets.peers(profile("phone")).isEmpty());
    }

    @Test
    public void labelsFallBackToTheDeviceId() {
        RemotePlayerTargets.Target named = RemotePlayerTargets.peers(profile("phone", group("g1", device("tv-1", " 客厅 ", true)))).get(0);
        RemotePlayerTargets.Target unnamed = RemotePlayerTargets.peers(profile("phone", group("g1", device("tv-2", "  ", true)))).get(0);
        assertEquals("客厅", RemotePlayerTargets.label(named));
        assertEquals("tv-2", RemotePlayerTargets.label(unnamed));
    }
}
