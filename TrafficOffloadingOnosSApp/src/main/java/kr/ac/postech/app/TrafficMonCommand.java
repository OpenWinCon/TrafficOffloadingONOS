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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;


import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Sample Apache Karaf CLI command
 */
@Command(scope = "onos", name = "offloading", description = "Sample Apache Karaf CLI command")

public class TrafficMonCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "trafficMonCommand", description = "trafficMonCommand", required = false, multiValued = false)
    private String trafficMonCommand = null;

    //@Argument(index = 1, name = "APID", description = "Device ID of switch", required = false, multiValued = false)
    //private String APID = null;

    @Argument(index = 1, name = "AP capacity or arg1", description = "Capacity of AP", required = false, multiValued = false)
    private String arg1 = null; 
    @Argument(index = 2, name = "APId", description = "APt ID to connect", required = false, multiValued = false)
	private String APId = null;
	@Argument(index = 3, name = "APpw", description = "APt PW to connect", required = false, multiValued = false)
	private String APpw = null;


    private TrafficMonService service;
	private AppService conService;
	private ConcurrentMap<String, Client> map;
    private int capacity;

    @Override
    protected void execute() {
        service=get(TrafficMonService.class);
        capacity = service.getCapacity();
        
        conService = get(AppService.class);
		map = conService.getMap();
		if (trafficMonCommand.equals("client")) {
			for (String devId : map.keySet()) {
				print(devId);
			}

		} else if (trafficMonCommand.equals("scan")) {
			if (arg1 != null) {
				if (!map.containsKey(arg1)) {
					print("No client");
					return;
				}
				map.get(arg1).send("scan");

				// if arg1 don't match any registered Clients then print all registered Clients
			} else {
				for (String devId : map.keySet()) {
					print(devId);
					// a client
				}
			}
		} else if (trafficMonCommand.equals("output")) {
			if (arg1 != null) {
				if (!map.containsKey(arg1)) {
					print("No client");
					return;
				}

				Client clt = map.get(arg1);
				Map<String, Integer> apMap = clt.getSignalInfo();
				Map<String, String> apMap2 = clt.getAPInfo();

				for (String ap : apMap.keySet()) {
					print("SSID: " + apMap2.get(ap) + "\tBSSID: " + ap + "\tSignalStrength:" + apMap.get(ap));
				}
			} else {
				print("fail to find client");
			}
		} else if (trafficMonCommand.equals("connect")) {
			if (arg1 != null) {
				if (!map.containsKey(arg1)) {
					print("fail to find clien");
					return;
				}
				Client clt = map.get(arg1);
				Map<String, String> apMap2 = clt.getAPInfo();
				String message = "switch"+"|"+apMap2.get(APId);
				if (APId != null) {
					message = message + "|" + APId;
				}
				if (APpw != null) {
					message = message + "|" + APpw;
				}else
				{
					message = message + "| |";
				}
				clt.send(message);
				//print("Send messente to client to swich AP  "+message);
			}

		} else if (trafficMonCommand.equals("disconnect")) {
			if (arg1 != null) {
				if (!map.containsKey(arg1)) {
					print("fail to find client");
					return;    
				}
				else
				{	
					Client clt = map.get(arg1);
					String message = "wifioff";
						clt.send(message);
				//	print("Send to client to disconnect AP to "+message);
				}
			}

		}
        if(trafficMonCommand.equals("get")){
        	if(arg1==null)
        		capacity=0;
            print("capacity: " + capacity);
        }
        else if (trafficMonCommand.equals("set")){
        	capacity=Integer.parseInt(arg1);
            service.setCapacity(capacity);
        }

    }

}
