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
import org.onosproject.cli.Comparators;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.Criterion.Type;
import org.onlab.osgi.DefaultServiceDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.concurrent.ThreadPoolExecutor;
import static com.google.common.collect.Lists.newArrayList;


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
    private ConcurrentMap<Client,String> ClienConnetedAPMap; /* SSID of Connected AP, Client*/
    private ConcurrentMap<String, AP> DeviceAPMap; /*<DeviceId, BSSID(AP)>*/
    private ConcurrentMap<String, AP> DeviceApSSIDMap; /*<SSID, AP>*/

    private HashMap<String, Long> ApTrafficMap;
    DatagramSocket curControllerSocket=null;
    
    private static final Predicate<FlowEntry> TRUE_PREDICATE = f -> true;
    private Predicate<FlowEntry> predicate = TRUE_PREDICATE;

    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;
    protected DeviceService deviceServiceForFlow;
    protected CoreService coreService;
    protected FlowRuleService flowRuleService;
    

    private int capacity = 10;      // Mbps

    @Activate
    protected void activate() {
    	
        log.info("Started");

        coreService=DefaultServiceDirectory.getService(CoreService.class);
        flowRuleService=DefaultServiceDirectory.getService(FlowRuleService.class);
        deviceServiceForFlow=DefaultServiceDirectory.getService(DeviceService.class);
        deviceService.addListener(new InnerDeviceListener());

        clientsMap = new ConcurrentHashMap<String, Client>();
        DeviceAPMap = new ConcurrentHashMap<String, AP>();
        ClienConnetedAPMap = new ConcurrentHashMap<Client, String>();
        DeviceApSSIDMap = new ConcurrentHashMap<String, AP>();
        System.out.println("device number: " + deviceService.getDeviceCount());
        executor.scheduleAtFixedRate(this::trafficMonitoring, 1, 5, TimeUnit.SECONDS); 
        
        conExecutor= (ThreadPoolExecutor)Executors.newCachedThreadPool();
        conExecutor.execute(new ConListener(this, 1622, conExecutor));

        
    }
    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
        executor.shutdown();
        if(curControllerSocket!=null&&curControllerSocket.isClosed()==false)
        	curControllerSocket.close();
        conExecutor.shutdown();
        
    }

    private void trafficMonitoring() {
    	//log.info("start traffic monitoring");
        Iterable<Device> devices = deviceService.getDevices();
        ApTrafficMap= new HashMap<String, Long>();

        log.info("start traffic monitoring");
        
        boolean onOffloading=false;
        for (Device device : devices) {
            List<PortStatistics> ports = deviceService.getPortDeltaStatistics(device.id());
                 
            long traffic=0;//(port.bytesSent()+port.bytesReceived())/port.durationSec();
            for (PortStatistics port : ports)
            {
                traffic=(port.bytesReceived())/port.durationSec();
            }
            AP targetAP;
            if(DeviceAPMap.get(device.id().toString())!=null)
            {
            	  targetAP=DeviceAPMap.get(device.id().toString());
            	  targetAP.setTraffic(traffic);
            	  int sizeOfWindow=targetAP.addTrafficSilingWindow(traffic);
                  
                  ApTrafficMap.put(device.id().toString(),targetAP.getAvgTrafficSlidingWindow());
                  
                  System.out.println("deviceID: " + device.id() + "current traffic: " + String.format("%.2f",(double) traffic * 8 /1024 / 1024)+"\taverage traffic: " +  String.format("%.2f",(double) targetAP.getAvgTrafficSlidingWindow() * 8 /1024 / 1024)  + " (Mbps)\tcapacity: " + capacity);

                  if ((double) targetAP.getAvgTrafficSlidingWindow() * 8 /1024 / 1024 > capacity && sizeOfWindow==targetAP.WINDOW_SIZE)
                  {
                  	System.out.println("capacity is exceed.");
                  	onOffloading=true;
                  }
            }

        }
        System.out.println("");
        if(onOffloading==true)
        	TrafficMonSwitchAP();
        onOffloading=false;
    }
       
    /**
     * Returns the list of devices sorted using the device ID URIs.
     *
     * @param deviceService device service
     * @param service flow rule service
     * @return sorted device list
     */
    protected SortedMap<Device, List<FlowEntry>> getSortedFlows(DeviceService deviceService,
                                                          FlowRuleService service) {
        SortedMap<Device, List<FlowEntry>> flows = new TreeMap<>(Comparators.ELEMENT_COMPARATOR);
        List<FlowEntry> rules;

        Iterable<Device> devices = null;
        devices = deviceService.getDevices();
         
        for (Device d : devices) {
            if (predicate.equals(TRUE_PREDICATE)) {
                rules = newArrayList(service.getFlowEntries(d.id()));
            } else {
                rules = newArrayList();
                for (FlowEntry f : service.getFlowEntries(d.id())) {
                    if (predicate.test(f)) {
                        rules.add(f);
                    }
                }
            }
            rules.sort(Comparators.FLOW_RULE_COMPARATOR);
            flows.put(d, rules);
        }
        return flows;
    }
    
    protected void printFlows(Device d, List<FlowEntry> flows,
            CoreService coreService) {
    	boolean empty = flows == null || flows.isEmpty();
    	System.out.println("deviceId="+d.id());
    	if (empty) {
    		return;
    	}

    	for (FlowEntry f : flows) {
    		if (true) {
    			String flowInfo="byte: "+f.bytes()+ ", packets: "+ f.packets()+", "+f.selector().criteria();
    			DeviceAPMap.get(d.id().toString()).parsingClientsInfo(flowInfo);
    		}
    	}
    }
    public void parsingTrafficStatics(Set<Criterion> set )
    {
    	String tmp=set.toString();
 
        for (Criterion c : set) {
        	System.out.println(c.type());//+" "+((TrafficSelector) set).getCriterion(c.type()));
       
        }   	
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> 
    sortByValue( Map<K, V> map )
	{
	    List<Map.Entry<K, V>> list =
	        new LinkedList<>( map.entrySet() );
	    Collections.sort( list, new Comparator<Map.Entry<K, V>>()
	    {
	        @Override
	        public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
	        {
	            return ( o2.getValue() ).compareTo( o1.getValue() );
	        }
	    } );
	
	    Map<K, V> result = new LinkedHashMap<>();
	    for (Map.Entry<K, V> entry : list)
	    {
	        result.put( entry.getKey(), entry.getValue() );
	    }
	    return result;
	}

    
    private AP ContainAP(String BSSID)
    {
    	for (String deviceId : DeviceAPMap.keySet())
    	{
    		if(DeviceAPMap.get(deviceId).getBSSID().equals(BSSID))
    		{
    			
    			System.out.println(deviceId+ " "+DeviceAPMap.get(deviceId).getBSSID()+ " "+BSSID);
    			return DeviceAPMap.get(deviceId);
    		}
    	}
    		
    	return null;
    }

    
    private void TrafficMonSwitchAP()
    {
    	log.info("Start TrafficMonSwitchAP()");
    	AP canAPAgent=null;
    	AP curAPAgent=null;
    	long curAPTraffic=0;
    	boolean onOffloading=false;
    	
    	//decsort by avg slicing window bandwidth
    	ApTrafficMap=(HashMap<String, Long>) sortByValue(ApTrafficMap);  	
    	for (String deviceId : ApTrafficMap.keySet()) {  //find candidate AP
    		curAPTraffic=ApTrafficMap.get(deviceId);
			curAPAgent = DeviceAPMap.get(deviceId);
			for(Client client : ClienConnetedAPMap.keySet())
			{
				if(ClienConnetedAPMap.get(client).equals(curAPAgent.getSSID()))
				{
					System.out.println("\n****************** find current ap "+ClienConnetedAPMap.get(client)+ " "+curAPAgent.getSSID() );
					Client targetClinet = client;
					Map<String/*BSSID*/, Integer> clientSignal =targetClinet.getSignalInfo();
					for (String candidateAPId/*BSSID*/ : clientSignal.keySet())
					{				
						String canAPbssid = candidateAPId.trim().toLowerCase();
						canAPAgent=null;
						canAPAgent=ContainAP(canAPbssid);
						
						Map<String, String> apMap2 = targetClinet.getAPInfo(); /*bssid, ssid*/
						if(!ClienConnetedAPMap.get(client).equals(apMap2.get(canAPbssid))&&clientSignal.get(candidateAPId)>-80&&canAPAgent!=null&&canAPAgent!=curAPAgent)
						{
							//System.out.println(ClienConnetedAPMap.get(client)+" "+apMap2.get(canAPbssid)+" "+canAPAgent.getBSSID());
					        //System.out.println("****************** find target client and ap "+apMap2.get(candidateAPId)+" "+candidateAPId +" !!!"+canAPAgent.getSSID());
					       				       
							if(canAPAgent.getAvgTrafficSlidingWindow()* 8 /1024 / 1024 < capacity)
							{
								String message = "switch"+"|"+apMap2.get(candidateAPId)/*SSID*/;
								if (candidateAPId != null) {
									message = message + "|" +canAPAgent.getBSSID()/*BSSID*/+"| |";// canAPAgent.getBSSID();//canAPAgent.getBSSID();
								}
								System.out.println("TrafficMon Offloading from "+ curAPAgent.getBSSID() +" to "+candidateAPId);
								System.out.println(message);
								targetClinet.send(message);
								log.info("TrafficMon swich AP  "+message);
								onOffloading=true;
								/*********************** reset sliding window********************/
								curAPAgent.resetTrafficSilingWindow();

							}
						}
						if(onOffloading==true)
							break;
					}
				}
				if(onOffloading==true)
					break;
			}
			if(onOffloading==true)
			{
				break;
			}		
		}
    }

	@Override
	public ConcurrentMap<String, Client> getMap() {
		// TODO Auto-generated method stub
		return clientsMap;
	}
	@Override
	public ConcurrentMap<String, AP> getDeviceAPMap() {
		// TODO Auto-generated method stub
		return DeviceAPMap;
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

	public void receiveClientInfo(InetAddress agentAddr, int agentPort, String clientMacAddr, String ConnectedAP, DatagramSocket controllerSocket ) {
		// TODO Auto-generated method stub
		 String clientMac = clientMacAddr.toLowerCase();

	        log.info("Client message from " + agentAddr.getHostAddress() + ": " + agentPort + " - " + clientMac);

	        if (clientsMap.containsKey(clientMac)) {
	            clientsMap.get(clientMac).setIpAddress(agentAddr, agentPort, controllerSocket);
	            ClienConnetedAPMap.put(clientsMap.get(clientMac),ConnectedAP);
	        }
	        if (!clientsMap.containsKey(clientMac)) {
	            clientsMap.put(clientMac, new Client(clientMacAddr, agentAddr, agentPort, controllerSocket));
	            ClienConnetedAPMap.put(clientsMap.get(clientMac), ConnectedAP);
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
