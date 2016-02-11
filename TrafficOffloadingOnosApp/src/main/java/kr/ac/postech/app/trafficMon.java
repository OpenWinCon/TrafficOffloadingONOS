/*
 * Copyright 2016 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kr.ac.postech.app;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.MacAddress;
import org.onlab.util.SharedExecutors;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
@Service
public class trafficMon implements AppService, TrafficMonService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ThreadPoolExecutor conExecutor ;
    private ConcurrentMap<String, Client> clientsMap; /*<switch(ap) <client, scan>*/
    private ConcurrentMap<String, AP> DeviceAPMap; /*<DeviceId, BSSID(AP)>*/
    DatagramSocket curControllerSocket=null;

    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    private int capacity = 100;      // Mbps

    @Activate
    protected void activate() {
    	
        log.info("Started");
        //executor= SharedExecutors.getPoolThreadExecutor();
        //executor.execute(new TrafficMonitor(this, executor));

        deviceService.addListener(new InnerDeviceListener());
        clientsMap = new ConcurrentHashMap<String, Client>();
        DeviceAPMap = new ConcurrentHashMap<String, AP>();
        
    	enrollAP();
        System.out.println("device number: " + deviceService.getDeviceCount());
        executor.scheduleAtFixedRate(this::trafficMonitoring, 10, 10, TimeUnit.SECONDS); 
        
        conExecutor= (ThreadPoolExecutor)Executors.newCachedThreadPool();//newFixedThreadPool(20);
        conExecutor.execute(new ConListener(this, 1622, conExecutor));
    }
    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
        executor.shutdown();
        if(curControllerSocket!=null&&curControllerSocket.isClosed()==false)
        	curControllerSocket.close();
        conExecutor.shutdown();
        
        //eventHandler.shutdown();
    }

    private void trafficMonitoring() {
    //	log.info("start traffic monitoring");
        Iterable<Device> devices = deviceService.getDevices();
        log.info("start traffic monitoring");
        for (Device device : devices) {
            List<PortStatistics> ports = deviceService.getPortDeltaStatistics(device.id());

            long byteTxSum = 0;
            for (PortStatistics port : ports)
            {
                byteTxSum += port.bytesSent();
            }
            	DeviceAPMap.get(device.id().toString()).setTraffic(byteTxSum);
          //   	System.out.println("deviceID: " + device.id() + "\tbyteTxSum: " + byteTxSum); //DeviceAPMap.get(device.id().toString()).getTraffic());//byteTxSum);   // + "\tdurationSec: " + port.durationSec());
          //  	System.out.println("current traffic: " + (double) byteTxSum * 8 /1024 / 1024 + " (Mbps)\tcapacity: " + capacity);
            if ((double) byteTxSum * 8 /1024 / 1024 < capacity)
            {
         //   	System.out.println("capacity is not exceed.");
            }
            else
            {
           // 	System.out.println("capacity is exceed.");
            	TrafficMonSwitchAP();
            }

            //System.out.println("traffics: " + deviceService.getPortDeltaStatistics(device.id()));
        }

    }
    
    private void enrollAP()
    {
    	String device1="of:0000b827ebf0fd40";
    	String device2="of:0000b827eb248291";
    	DeviceAPMap.put(device1, new AP(device1, "OPENWINCON","00:26:66:4e:df:b5", (long)0) );
    	DeviceAPMap.put(device2, new AP(device1, "MCNLONOS","00:26:66:42:4a:a5",(long)0) );
    }
    private void TrafficMonSwitchAP()
    {
    	log.info("Start TrafficMonSwitchAP()");
    	AP canAPAgent=null;
    	AP curAPAgent=null;
    	//find target AP that have less traffic than cacacity
		for (String deviceId : DeviceAPMap.keySet()) {  //find candidate AP
			curAPAgent = DeviceAPMap.get(deviceId);
	//		System.out.println("find TrafficMon swich AP "+deviceId+", " +curAPAgent.getSSID());
			log.info("find TrafficMon swich AP "+deviceId+", " +curAPAgent.getSSID());
			if(canAPAgent==null)
			{
				if(curAPAgent.getTraffic()* 8 /1024 / 1024 <capacity)
					canAPAgent=curAPAgent;
			}
			else
			{
				if(curAPAgent.getTraffic()<canAPAgent.getTraffic())
					canAPAgent=curAPAgent;
			}
			
	//		String 
		//	print("SSID: " + DeviceAPMap.get(deviceId) + "\tBSSID: " + ap + "\tSignalStrength:" + apMap.get(ap));
		}
		if(curAPAgent==null)
			log.info("select TrafficMon target AP fail");
		if(curAPAgent!=null)
			log.info("select Traffic Offloading AP success");

		if(canAPAgent!=null)
		{
			canAPAgent.getBSSID();
			String clientBSSID = (String) (clientsMap.keySet().toArray())[0];
			log.info("TrafficMon target client  "+clientBSSID);
			if (clientBSSID != null) {
				Client clt = clientsMap.get(clientBSSID);
				Map<String, String> apMap2 = clt.getAPInfo();  //<bssid, ssid>
				String message = "switch"+"|"+canAPAgent.getSSID();
				if (canAPAgent.getBSSID() != null) {
					message = message + "|" + canAPAgent.getBSSID();
				}
				if (canAPAgent.getPW()  != null) {
					message = message + "|" + canAPAgent.getPW();
				}else
				{
					message = message + "| |";
				}
				System.out.println("TrafficMon Offloading from "+ curAPAgent.getSSID() +" to "+canAPAgent.getSSID());
				clt.send(message);
		//		System.out.println("start TrafficMon swich AP "+message);
				log.info("TrafficMon swich AP  "+message);
			}

		}
			
    }

	@Override
	public ConcurrentMap<String, Client> getMap() {
		// TODO Auto-generated method stub
		return clientsMap;
	}
	

    @Override
    public int getCapacity()
    {
        return capacity;
    }

    @Override
    public void setCapacity(int c)
    {
        this.capacity = c;
    }

    // Triggers driver setup when a device is (re)detected.
    private class InnerDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            switch (event.type()) {
                case DEVICE_ADDED:
                case DEVICE_AVAILABILITY_CHANGED:
                    if (deviceService.isAvailable(event.subject().id())) {
                        log.info("Device connected {}", event.subject().id());
                        //if (event.subject().id().equals(deviceId)) {
                        //processIntfFilters(true, interfaceService.getInterfaces());

                            /* For test only - will be removed before Cardinal release
                            delay(1000);
                            FibEntry fibEntry = new FibEntry(Ip4Prefix.valueOf("10.1.0.0/16"),
                                    Ip4Address.valueOf("192.168.10.1"),
                                    MacAddress.valueOf("DE:AD:BE:EF:FE:ED"));
                            FibUpdate fibUpdate = new FibUpdate(FibUpdate.Type.UPDATE, fibEntry);
                            updateFibEntry(Collections.singletonList(fibUpdate));
                            */
                        //}

                        //if (event.subject().id().equals(ctrlDeviceId)) {
                        //    connectivityManager.notifySwitchAvailable();
                        //}
                    }
                    break;

                // TODO other cases
                case DEVICE_UPDATED:
                    break;
                case DEVICE_REMOVED:
                    break;
                case DEVICE_SUSPENDED:
                    break;
                case PORT_ADDED:
                    break;
                case PORT_UPDATED:
                    System.out.println("port updated");
                    System.out.println("traffics: " + deviceService.getPortDeltaStatistics(event.subject().id()));
                    log.info("traffics: " + deviceService.getPortDeltaStatistics(event.subject().id()));
                    //event.subject().id().
                    break;
                case PORT_REMOVED:
                    break;
                default:
                    break;
            }
        }
    }
	public void receiveClientInfo(InetAddress agentAddr, int agentPort, String clientMacAddr, DatagramSocket controllerSocket ) {
		// TODO Auto-generated method stub
		 String clientMac = clientMacAddr.toLowerCase();

	        log.info("Client message from " + agentAddr.getHostAddress() + ": " + agentPort + " - " + clientMac);

	        if (clientsMap.containsKey(clientMac)) {
	            clientsMap.remove(clientMac);
	        }
	        if (!clientsMap.containsKey(clientMac)) {
	            clientsMap.put(clientMac, new Client(clientMacAddr, agentAddr, agentPort, controllerSocket));
	        } 
	        log.info("The map size is : "+clientsMap.size());
	        this.curControllerSocket=controllerSocket;
	}

	public void clientDisconnect(InetAddress agentAddr, String clientEthAddr) {
		// TODO Auto-generated method stub
	}	
	

	public void receiveScanResult(String[] fields) {
		// TODO Auto-generated method stub
        log.info("Received scan result from " + fields[1]);
        MacAddress macAddr = MacAddress.valueOf(fields[1]);
        Client clt = clientsMap.get(macAddr.toString().toLowerCase());
        if (clt == null) {
            log.warn("Request from unknown client " + fields[1] + ", discard it...");
            return;
        }
        clt.updateSignalInfo(Arrays.copyOfRange(fields, 3, fields.length - 1));
	
	}
}
