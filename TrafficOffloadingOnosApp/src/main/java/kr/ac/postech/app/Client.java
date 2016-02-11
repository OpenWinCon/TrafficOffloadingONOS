package kr.ac.postech.app;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onlab.packet.MacAddress;
import org.onosproject.store.service.AtomicCounter;
import org.onosproject.store.service.AtomicCounterBuilder;

public class Client implements Comparable<Object> {
	protected static Logger log = LoggerFactory.getLogger(Client.class);

	private final MacAddress hwAddress;
	private InetAddress ipAddress;
	private String app = "trivial";
	private Map<String, String> apMap = new ConcurrentHashMap<String, String>();
	private Map<String, Integer> apSignalLevelMap = new ConcurrentHashMap<String, Integer>();

	private APAgent agent;
	private long connectTime;
	int clientPort;
	private long lastRecvTime = 0;
	private int apScanningTime = 0;

	private DatagramSocket agentSocket;
	private static final int DELAY = 6000; // 6000 milliseconds

	/**
	 * construct a client instance
	 *
	 * @param hwAddress
	 *            Client's hw address
	 * @param ipv4Address
	 *            Client's IPv4 address
	 */

	public Client(String hwAddress, InetAddress ipAddress, int clientPort, DatagramSocket agentSocket) {
		this.hwAddress = MacAddress.valueOf(hwAddress);
		this.ipAddress = ipAddress;
		this.clientPort = clientPort;
		this.agentSocket = agentSocket;

		// initializeClientTimer();
		initConnectTime();
	}

	public void send(String message){
		// send message to agent ap
		byte[] buf = new byte[1200];
		buf = message.getBytes();
				

				DatagramPacket packet = new DatagramPacket(buf, buf.length, this.ipAddress, this.clientPort);
				log.info("packet send to " + this.ipAddress + " " + this.clientPort);
				try {
					DatagramSocket socket = new DatagramSocket();
	                InetAddress clientAddress = InetAddress.getByName("10.1.100.11");
	         
	                DatagramPacket packet2 = new DatagramPacket(buf, buf.length, this.ipAddress, 1622);
	                
                    final byte[] buf2 = new byte[1280];
                   // System.arraycopy("scan".getBytes(), 0, buf2, 0, "scan".getBytes().length);
                    System.arraycopy(buf, 0, buf2, 0, buf.length);
               //     int l = receiveData.length;
                    DatagramPacket packet3 = new DatagramPacket(buf2, buf2.length, clientAddress, 1622);
	             
	                socket.send(packet3);
	                log.info("Client: dump packet send to " + packet2.getAddress() + " " + packet2.getPort());//+"  "+socket.getInetAddress()+":"+socket.getPort());
	                socket.close();
	                //DatagramPacket packet = new DatagramPacket(receivedPacket.getData(), receivedPacket.getLength(), receivedPacket.getAddress(), receivedPacket.getPort());
	                
	                  
				} catch (IOException e) {
					log.error("can not send udp message to agent: " + message);
					e.printStackTrace();
				}

	}

	/**
	 * Set the client's first connecting time
	 */
	public void initConnectTime() {
		this.connectTime = System.currentTimeMillis();
	}

	/**
	 * Get the client's first connecting time
	 * 
	 * @return this.connectTime
	 */
	public long getConnectTime() {
		return this.connectTime;
	}

	/**
	 * Get the client's MAC address.
	 * 
	 * @return
	 */
	public MacAddress getMacAddress() {
		return this.hwAddress;
	}

	/**
	 * Get the client's IP address.
	 * 
	 * @return
	 */
	public InetAddress getIpAddress() {
		return ipAddress;
	}

	/**
	 * Set the client's IP address
	 * 
	 * @param addr
	 */
	public void setIpAddress(InetAddress addr) {
		this.ipAddress = addr;
	}

	public void setIpAddress(String addr) throws UnknownHostException {
		this.ipAddress = InetAddress.getByName(addr);
	}

	public APAgent getAgent() {
		return agent;
	}

	/**
	 * Get the client's AP RSSI.
	 * 
	 * @return Map<String AP's BSSI, Integer RSSI>
	 */
	public Map<String, Integer> getSignalInfo() {
		return apSignalLevelMap;
	}

	public Map<String, String> getAPInfo() {
		return apMap;
	}

	/**
	 * update current record of ap signal levels the input follows this type:
	 * ssid1&bssid1&level1|ssid2&bssid2&level2|...
	 *
	 * @param fields:
	 *            this is the context collect from the client
	 */
	public synchronized void updateSignalInfo(String[] fields) {
		long currTime = System.currentTimeMillis();
		if (lastRecvTime != 0 && currTime - lastRecvTime >= DELAY) {
			apScanningTime = 0;
			apSignalLevelMap.clear();
		}
		lastRecvTime = currTime; // update

		if (apScanningTime == 3) { // clear old data, now the program runs in a
									// simple way
			apSignalLevelMap.clear();
			apScanningTime = 0;
		}

		apScanningTime++; // add one for every time it receive scanning results

		for (int i = 0; i < fields.length; i++) {

			String[] info = fields[i].split("&");
			if (info.length >= 2 && !info[0].equals("")) {

				String ssid = info[0];
				String bssid = info[1].toLowerCase();
				int level = Integer.parseInt(info[2]);

				apMap.put(bssid, ssid);
				apSignalLevelMap.put(bssid, level);

			}
		}

		// log.info("Update signal level info -- time " + apScanningTime);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Client))
			return false;

		if (obj == this)
			return true;

		Client that = (Client) obj;

		return (this.hwAddress.equals(that.getMacAddress()));
	}

	@Override
	public int compareTo(Object o) {
		assert (o instanceof Client);

		if (this.hwAddress.toLong() == ((Client) o).getMacAddress().toLong())
			return 0;

		if (this.hwAddress.toLong() > ((Client) o).getMacAddress().toLong())
			return 1;

		return -1;
	}

}
