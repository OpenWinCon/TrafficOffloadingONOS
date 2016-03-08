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
	private Map<String, String> apMap = new ConcurrentHashMap<String, String>();
	private Map<String, Integer> apSignalLevelMap = new ConcurrentHashMap<String, Integer>();

	private AP agent;
	private long connectTime;
	int clientPort;
	private long lastRecvTime = 0;
	private int apScanningTime = 0;
	private int numOfAp = 0;
	private DatagramSocket clientSocket;

	
	public Client(String hwAddress, InetAddress ipAddress, int clientPort, DatagramSocket clientSocket) {
		this.hwAddress = MacAddress.valueOf(hwAddress);
		this.ipAddress = ipAddress;
		this.clientPort = clientPort;
		this.clientSocket = clientSocket;


	}

	public void send(String message){
		// send message to agent ap
		byte[] buf = new byte[1200];
		buf = message.getBytes();
				

				DatagramPacket packet = new DatagramPacket(buf, buf.length, this.ipAddress, this.clientPort);
				log.info("packet send to " + this.ipAddress + " " + this.clientPort);
				try {
					DatagramSocket socket = new DatagramSocket();
	         
	                
                    final byte[] buf2 = new byte[1280];
                    System.arraycopy(buf, 0, buf2, 0, buf.length);
                    DatagramPacket packet3 = new DatagramPacket(buf2, buf2.length, this.ipAddress, 1622);
	             
                    clientSocket.send(packet3);
	                log.info("send to " + packet3.getAddress() + " " + packet3.getPort());
	                socket.close();
	                //DatagramPacket packet = new DatagramPacket(receivedPacket.getData(), receivedPacket.getLength(), receivedPacket.getAddress(), receivedPacket.getPort());
	                
	                  
				} catch (IOException e) {
					log.error("can not send udp message to agent: " + message);
					e.printStackTrace();
				}

	}


	public void setIpAddress(InetAddress ipAddress, int clientPort, DatagramSocket clientSocket) {
		this.ipAddress = ipAddress;
		this.clientPort = clientPort;
		this.clientSocket = clientSocket;
	}

	public void setIpAddress(String addr) throws UnknownHostException {
		this.ipAddress = InetAddress.getByName(addr);
	}

	public AP getAgent() {
		return agent;
	}

	public Map<String, Integer> getSignalInfo() {
		return apSignalLevelMap;
	}

	public Map<String, String> getAPInfo() {
		return apMap;
	}
	public int getNumOfAP() {
		return numOfAp;
	}

	public synchronized void updateSignalInfo(String[] fields) {
		numOfAp=fields.length;
	
		for (int i = 0; i < fields.length; i++) {

			String[] info = fields[i].split("&");
			if (info.length >= 2 && !info[0].equals("")) {
				String ssid = info[0];
				String bssid = info[1].toLowerCase();
				int level = Integer.parseInt(info[2]);
				apMap.put(bssid, ssid);
				apSignalLevelMap.put(bssid, level);
				//System.out.println(apMap.get(bssid)+" "+apSignalLevelMap.get(bssid));

			}
		}

	}

	@Override
	public int compareTo(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}


}
