package kr.ac.postech.app;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class APAgent {
	private long traffic;
	private String SSID;
	private String BSSID;
	private String DeviceID;
	private Map<String, Client> clientMap;  //BSSID and Client Objecet 
	public APAgent(String DeviceID, String SSID, String BSSID, long capacity) {
		this.traffic = capacity;
		this.SSID = SSID;
		this.BSSID = BSSID;
		this.DeviceID = DeviceID;
		clientMap = new ConcurrentHashMap<String/*client's BSSID*/, Client/* Client Object*/>();
	}
	
	public void setTraffic(long byteTxSum)
	{
		traffic=byteTxSum;
	}
	
	public long getTraffic()
	{
		return traffic;
	}
	

	public String getBSSID()
	{
		return BSSID;
	}
	public String getSSID()
	{
		return SSID;
	}
	public void setClient(Client client)
	{
		;//clientMap.put(client, client);
	}

	public Object getPW() {
		// TODO Auto-generated method stub
		return null;
	}
	
}