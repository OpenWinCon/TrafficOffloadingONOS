package kr.ac.postech.app;

import java.util.concurrent.ConcurrentMap;

import org.onosproject.net.DeviceId;


public interface AppService {
    public ConcurrentMap<String, Client> getMap();
    public ConcurrentMap<String, AP> getDeviceAPMap();
}
