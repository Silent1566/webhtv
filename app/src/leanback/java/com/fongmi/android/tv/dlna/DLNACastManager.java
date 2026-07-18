package com.fongmi.android.tv.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.service.DLNACastService;

import org.jupnp.android.AndroidUpnpService;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.message.header.STAllHeader;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;

import java.util.List;
import java.util.stream.Collectors;

public class DLNACastManager extends DefaultRegistryListener implements ServiceConnection {

    private static final UDADeviceType RENDERER_TYPE = new UDADeviceType("MediaRenderer", 1);
    private static final UDAServiceType AVT_TYPE = new UDAServiceType("AVTransport", 1);

    private AndroidUpnpService upnpService;
    private DeviceListener deviceListener;

    public static DLNACastManager get() {
        return Loader.INSTANCE;
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        if (device.getType().implementsVersion(RENDERER_TYPE)) notifyAdded(Device.get(device));
    }

    @Override
    public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
    }

    @Override
    public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
        if (device.getType().implementsVersion(RENDERER_TYPE)) notifyRemoved(Device.get(device));
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        upnpService = (AndroidUpnpService) binder;
        upnpService.getRegistry().addListener(this);
        search();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        upnpService = null;
    }

    public void setDeviceListener(DeviceListener listener) {
        deviceListener = listener;
    }

    private void notifyAdded(Device bean) {
        if (deviceListener != null) App.post(() -> deviceListener.onDeviceAdded(bean));
    }

    private void notifyRemoved(Device bean) {
        if (deviceListener != null) App.post(() -> deviceListener.onDeviceRemoved(bean));
    }

    public void init(Context context) {
        context.bindService(new Intent(context, DLNACastService.class), this, Context.BIND_AUTO_CREATE);
    }

    public void search() {
        if (upnpService != null) upnpService.getControlPoint().search(new STAllHeader());
    }

    public List<Device> getRegistered() {
        if (upnpService == null) return List.of();
        return upnpService.getRegistry().getDevices(RENDERER_TYPE).stream().map(d -> Device.get((RemoteDevice) d)).collect(Collectors.toList());
    }

    public RemoteDevice findDevice(Device bean) {
        if (upnpService == null) return null;
        return upnpService.getRegistry().getDevices(RENDERER_TYPE).stream().filter(d -> d.getIdentity().getUdn().getIdentifierString().equals(bean.getUuid())).map(d -> (RemoteDevice) d).findFirst().orElse(null);
    }

    public RemoteService findAVTransport(Device bean) {
        RemoteDevice rd = findDevice(bean);
        return rd != null ? rd.findService(AVT_TYPE) : null;
    }

    public ControlPoint getControlPoint() {
        return upnpService != null ? upnpService.getControlPoint() : null;
    }

    public void release(Context context) {
        if (upnpService == null) return;
        upnpService.getRegistry().removeListener(this);
        context.unbindService(this);
        upnpService = null;
    }

    public interface DeviceListener {

        void onDeviceAdded(Device device);

        void onDeviceRemoved(Device device);
    }

    private static class Loader {
        static final DLNACastManager INSTANCE = new DLNACastManager();
    }
}
