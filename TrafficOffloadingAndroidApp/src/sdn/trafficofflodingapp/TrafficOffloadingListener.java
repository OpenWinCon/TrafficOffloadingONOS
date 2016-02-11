
package sdn.trafficofflodingapp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;
import sdn.trafficofflodingapp.MainActivity.UDPReceiver;

public class TrafficOffloadingListener extends IntentService {

	private boolean isEnabled = true;
	private DatagramSocket socket = null;
	private int preNetId;
	private WifiScanReceiver wifiScanReceiver; // used for scan wifi ap
    String LOG_TAG = "TrafficOffloading";
	private int scanAPNum;

	// acc sensor
	private boolean enableMotionDetection = false;
	private boolean startScanFlag = false;

	// some defaults
	private String OUT_FILE = "result.txt";
	private int MAX_BUF_LEN = 1024;
	private String UDP_SERVER_PORT_KEY = "recv_udp_port";
	private String UDP_SERVER_PORT_DEFAULT = "1622";// "7755";
	private int UDP_SERVER_PORT = 1622;// 7755;
	private int AGENT_PORT = 6777;
	private String TOKEN = "\\|";
	private long DELAY_TIME_MS = 12000;
	private int DELAY_TIMES = 2;
	private String PREF_MOTION_DETECTION = "pref_motion_detection";
	private String CONTROLLER_ADDRESS = "141.223.107.139";
	private int CONTROLLER_PORT = 1622;
	// Message types
	private final String MSG_SCAN = "scan";
	private final String MSG_SWITCH = "switch";
	private final String MSG_WIFI_OFF = "wifioff";
	
	private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	WifiInfo wifiInfo = null;

	private class WifiScanReceiver extends BroadcastReceiver {
		public int scanRemainingNum = 0;
		public StringBuilder scanResult;
		public boolean isStatic = false;

		public void onReceive(Context c, Intent intent) {
			if (startScanFlag) 
			{
				scanResult = new StringBuilder();
				scanResult.append("scan|");
				WifiManager wifiManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
				String mac = wifiManager.getConnectionInfo().getMacAddress();
				scanResult.append(mac);
				if (isStatic) {
					scanResult.append("|static");
				} else {
					scanResult.append("|other");
				}
				List<ScanResult> scanResultList = wifiManager.getScanResults();
				scanAPNum = 0;
				for (ScanResult r : scanResultList) {
					scanAPNum++;
					scanResult.append("|" + r.SSID + "&" + r.BSSID + "&" + r.level);
				}
				Log.d(LOG_TAG, "scan result message: " + scanResult.toString());
				// send reply
				try {
					InetAddress ipAddr = InetAddress.getByName("141.223.107.139");
					eventSender("send scan result to ONOS controller");
					new TrafficOffloadingSender().execute(scanResult.toString(), ipAddr, 1622);
				} catch (Exception e) {
					e.printStackTrace();
				}
				startScanFlag=false;
			}
		}
	}

	public TrafficOffloadingListener() {
		super("TrafficOffloadingListener");
	}

	private void holepunching()
	{
	
		
	}
	@SuppressLint("DefaultLocale")
	@Override
	protected void onHandleIntent(Intent arg0) {
		String message;
		Log.i(LOG_TAG, "in lintennig module");

		isEnabled = true;
	
		wifiScanReceiver = new WifiScanReceiver();
		registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

		String portString = prefs.getString(UDP_SERVER_PORT_KEY, UDP_SERVER_PORT_DEFAULT);
		int udpServerPort = Integer.parseInt(portString);
		if (udpServerPort < 1024 || udpServerPort > 65535) {
			udpServerPort = UDP_SERVER_PORT;
		}

		try {
			socket = new DatagramSocket(udpServerPort);
			
			
			
			WifiManager wifiManager2 = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
			
			
			String holemsg = "client|" + wifiManager2.getConnectionInfo().getMacAddress() + "|" + getLocalIpAddress(2);
			byte[] buf = holemsg.getBytes();
			/* receive port number를 msg에 첨부하여 보내는 방식으로 추후 수정 */
			
			// new TrafficOffloadingSender().execute(buf.toString(), CONTROLLER_ADDRESS,
			// CONTROLLER_PORT);
			try {
			DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(CONTROLLER_ADDRESS), CONTROLLER_PORT);
			Log.i("TrafficOffloading", "UDP send hole punching : " + holemsg);
				socket.send(packet);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Holepunching holepunching = new Holepunching(socket, this);
		    Timer jobScheduler = new Timer(true);
		    jobScheduler.scheduleAtFixedRate(holepunching, 3000, 3000); 

			eventSender("connect to ONOS controller");

			while (isEnabled) {

				byte[] inBuf = new byte[1280];
				DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);
				socket.receive(inPacket);
				message = new String(inPacket.getData(), inPacket.getOffset(), inPacket.getLength()).trim();
				Log.d("TrafficOffloading", "received packet: " + message);

				String[] fields = message.split(TOKEN);
				String msg_type = fields[0].toLowerCase();

				if (msg_type.equals(MSG_SWITCH)) { // switch to specific access
					eventSender("receive packet ### connection request ###");
					eventSender("start connect to Wifi [" + fields[1] + "]");
					connectToSpecificNetwork2(fields);

				} else if (msg_type.equals(MSG_SCAN)) { // using for ap scanning

					eventSender("receive packet ### scan request ###");

									WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
					startScanFlag=true;
					wifiManager.startScan();

					eventSender("start wifi scanning...");

				} else if (msg_type.equals(MSG_WIFI_OFF)) { // turn off wifi

					WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
					if (wifiManager.isWifiEnabled()) {
						wifiManager.setWifiEnabled(false);
					}
					Log.i(LOG_TAG, "wifi is turned off");
				}
			}
			socket.close();

			// FIXME current thread termination may cause socket exception
			// now we just ignore it
		} catch (SocketException e) {
			e.printStackTrace();
			// e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
			stopSelf();
		}
	}

	public void stopListening() {
		isEnabled = false;
		if (socket != null) {
			socket.close();
			socket = null;
		}
	}

	public static String getLocalIpAddress(int type) {
		try {

			for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = (NetworkInterface) en.nextElement();
				for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						switch (type) {
						case 1/* INET6ADDRESS */:
							if (inetAddress instanceof Inet6Address) {
								return inetAddress.getHostAddress().toString();
							}
							break;
						case 2/* INET4ADDRESS */:
							if (inetAddress instanceof Inet4Address) {
								return inetAddress.getHostAddress().toString();
							}
							break;
						}
					}
				}

			}
		} catch (SocketException ex) {
		}
		return null;
	}

	@Override
	public void onDestroy() {
		stopListening();
		unregisterReceiver(wifiScanReceiver);
		Log.d("UDPListeningService", "UDP receiver successfully stopped.");
		super.onDestroy();
	}

	static final String[] SECURITY_MODES = { "WEP", "WPA", "WPA2", "WPA_EAP", "IEEE8021X" };

	public String getScanResultSecurity(ScanResult scanResult) {
		final String cap = scanResult.capabilities;
		for (int i = SECURITY_MODES.length - 1; i >= 0; i--) {
			Log.i("a", "5");
			if (cap.contains(SECURITY_MODES[i])) {
				return SECURITY_MODES[i];
			}
		}
		return "OPEN";
	}

	private boolean connectToSpecificNetwork2(String[] fields) {
		// String ssid, String bssid, int level, List<ScanResult>
		// scanResultList, String logTag
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		if (wifiManager.isWifiEnabled()) {
			wifiManager.setWifiEnabled(true);
		}

		String networkSSID = fields[1];// ssid;
		String bssid = fields[2];
		String passkey = fields[3];// "mcnl5684";
		// eventSender("start connect to Wifi [" + networkSSID + "]");

		Log.i("connect info", "ssid:" + fields[1] + " bssid:" + fields[2] + " passkey:" + fields[3]);
		Log.i("ap", "* connectToWifi " + networkSSID);
		WifiConfiguration wfc = new WifiConfiguration();
		WifiManager wfMgr = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		List<ScanResult> scanResultList = wfMgr.getScanResults();

		for (ScanResult result : scanResultList) {
			Log.i("", "seraching ap " + result.BSSID);
			if (result.BSSID.equals(bssid)) {
				Log.i("", "find ap " + result.BSSID);

				wfc.SSID = "\"" + result.SSID + "\"";
				wfc.status = WifiConfiguration.Status.DISABLED;
				wfc.priority = 40;

				String securityMode = getScanResultSecurity(result);
				Log.i("mode", securityMode);
				if (securityMode.equalsIgnoreCase("OPEN")) {
					Log.i("", "in open find ap " + result.BSSID);
					wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
					wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
					wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
					wfc.allowedAuthAlgorithms.clear();
					wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
				} else if (securityMode.equalsIgnoreCase("WEP")) {
					wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
					wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
					wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
					wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
					wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
					wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
					wfc.wepKeys[0] = "\"".concat(passkey).concat("\"");
					wfc.wepTxKeyIndex = 0;
				} else {
					wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
					wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
					wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
					wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
					wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
					wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
					wfc.preSharedKey = "\"".concat(passkey).concat("\"");
				}
			}
		}

		int networkId = wfMgr.addNetwork(wfc);
		System.out.println(networkId);
		if (networkId == -1) {
			Log.i("a", "error");
		}
		wfMgr.setWifiEnabled(true);
		WifiInfo wifiInfo = wfMgr.getConnectionInfo();

		wfMgr.enableNetwork(networkId, true);
		return true;
	}

	double getMean(List<Float> values) {
		double sum = 0;
		int size = 0;
		for (double value : values) {
			sum += value;
			size += 1;
		}
		return sum / size;
	}

	double getStdDev(double mean, List<Float> values) {
		double temp = 0;
		int size = 0;
		for (double value : values) {
			temp += (mean - value) * (mean - value);
			size += 1;
		}

		return Math.sqrt(temp / size);
	}

	boolean isStatic() {
		boolean result = false;
		return result;
	}

	public void eventSender(String msg) {
		Intent ScanResultintent = new Intent();
		ScanResultintent.setAction(UDPReceiver.ACTION_RESP);
		ScanResultintent.addCategory(Intent.CATEGORY_DEFAULT);
		ScanResultintent.putExtra("TrafficOffloadingListenr", msg);
		Log.i("eventsender", "eventsender broadcast" + msg);
		sendBroadcast(ScanResultintent);
	}
	
	class Holepunching extends TimerTask {
		
		TrafficOffloadingListener udpListeningService;
		DatagramSocket socket;

		public Holepunching(DatagramSocket DatagramSocket, TrafficOffloadingListener udpListeningService) {
			this.socket=DatagramSocket;
			this.udpListeningService=udpListeningService;
			// TODO Auto-generated constructor stub
		}

		public void run() {
			WifiManager wifiManager2 = (WifiManager)udpListeningService.getSystemService(Context.WIFI_SERVICE);
			String holemsg = "client|" + wifiManager2.getConnectionInfo().getMacAddress() + "|" + getLocalIpAddress(2);
			byte[] buf = holemsg.getBytes();
			/* receive port number를 msg에 첨부하여 보내는 방식으로 추후 수정 */
			
			// new TrafficOffloadingSender().execute(buf.toString(), CONTROLLER_ADDRESS,
			// CONTROLLER_PORT);
			try {
			DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(CONTROLLER_ADDRESS), CONTROLLER_PORT);
			Log.i("TrafficOffloading", "UDP send hole punching : " + holemsg);
				
				socket.send(packet);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		   }
		}


}

