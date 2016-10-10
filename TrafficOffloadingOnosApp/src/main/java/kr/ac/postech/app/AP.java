package kr.ac.postech.app;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class AP {
	private long traffic;
	private String SSID;
	private String BSSID;
	private String DeviceID;
	private Map<String, Long> conncetedClients;  //BSSID of Client Objecet, cumbyte
	private Queue<Long> slicingWindow;
	private List<Object> slicingWindowList;  
    private long startTime;
    
    public int WINDOW_SIZE=3;


	private long avgTraffic;
	Long totalCumByte;
	String fileName;
	public AP(String DeviceID, String SSID, String BSSID, long capacity) {
		this.DeviceID = DeviceID;
		this.traffic = capacity;
		this.SSID = SSID;
		this.BSSID = BSSID;
		fileName="/home/offloading/hyunmin/"+BSSID+".txt";
		conncetedClients = new ConcurrentHashMap<String/*client's BSSID*/, Long/* Client Object*/>();
		totalCumByte=(long) 0;
		slicingWindow = new LinkedList<Long>();
		slicingWindowList= new ArrayList<>();
		avgTraffic=0;
	    startTime=System.currentTimeMillis();

	}
	
	public void setTraffic(long byteTxSum)
	{
		traffic=byteTxSum;
	}
	 
	public long getTraffic()
	{
		return traffic;
	}
	public int addTrafficSilingWindow(long traffic)
	{
		long sumOfTraffic=0;
		if(slicingWindowList.size()==WINDOW_SIZE)
			slicingWindowList.remove(0);
		slicingWindowList.add(traffic);
		
		for(int i=0;i<slicingWindowList.size();i++)
		{
			sumOfTraffic=sumOfTraffic+(long)slicingWindowList.get(i);
		}
		if(sumOfTraffic==0)
			avgTraffic=0;
		else
			avgTraffic=sumOfTraffic/(long)slicingWindowList.size();
		
		String trafficInfo=null;
		trafficInfo="Time: "+(System.currentTimeMillis()-startTime)/1000.0f+"\tcurTraffic: "+(double)traffic * 8 /1024 / 1024+"Mbps"+"\tavgWindowTraffic: "+(double)avgTraffic* 8 /1024 / 1024+"Mbps";
		writeTraffic(trafficInfo);		
		
		return slicingWindowList.size();
	}
	
	public long getAvgTrafficSlidingWindow()
	{
		return avgTraffic;
		
	}
	

	public String getBSSID()
	{
		return BSSID;
	}
	public String getSSID()
	{
		return SSID;
	}
	public void addClient(String clientBSSID, long receivedByte)
	{
		;//conncetedClients.put(clientBSSID, receivedByte);
	}
	public int  getNumClients()
	{
		return conncetedClients.size();
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

	public HashMap<String, Long> sortedConntedClients()
	{		
		return (HashMap<String, Long>) (sortByValue(conncetedClients));
	}
	public long getConnetedClientTraffic(String clientBSSID)
	{
		return conncetedClients.get(clientBSSID);
	}

	public Object getPW() {
		// TODO Auto-generated method stub
		return null;
	}
	public long getTotalPortCumByte(){
		Long sumTraffic=(long) 0;
		for (String clientId : conncetedClients.keySet())
		{
			sumTraffic=sumTraffic+conncetedClients.get(clientId);
		}
		Long disbyte=sumTraffic-totalCumByte;
		totalCumByte=sumTraffic;
		return disbyte;
	}
	 
	public void parsingClientsInfo(String info)
	{
		String msg = info.trim().toLowerCase();

		msg = msg.replace("[", "");
		msg = msg.replace("]", "");

        String[] fields = msg.split(",");
        String msg_type = fields[0];
        String temp="";
        
        String clientBSSID = null;
        long cumByte = 0;
        
        for(int i=0; i<fields.length;i++)
        {
        	if(fields[i].matches(".*byte:.*"))
        	{
        		cumByte=Long.parseLong(fields[i].replace("byte:", "").trim());
        	}
    
        	else if(fields[i].matches(".*eth_dst:.*"))
        	{
        		clientBSSID=fields[i].replace("eth_dst:", "").trim();
        		//System.out.println("find client mac "+clientBSSID);
        	}
        	temp=temp+"|"+fields[i];
        }
        //System.out.println(temp);
        if(clientBSSID!=null)
        {
        	String trafficInfo=null;
        	if(conncetedClients.containsKey(clientBSSID)&&conncetedClients.get(clientBSSID)!=0)
        	{
        		trafficInfo="Mobile device: "+clientBSSID+" cumByte : "+cumByte+" MBps : "+(double)(cumByte-conncetedClients.get(clientBSSID))/1024 / 1024 /10;
        		System.out.println("Mobile device: "+clientBSSID+" cumByte : "+cumByte+" MBps : "+(double)(cumByte-conncetedClients.get(clientBSSID))/1024 / 1024 /10); //* 8 /1024 / 1024
        	}
        	else
        	{
        		trafficInfo="Mobile device: "+clientBSSID+" cumByte : "+cumByte+" MBps : "+(double)(cumByte)/1024 / 1024 /10;
        		System.out.println("Mobile device: "+clientBSSID+" cumByte : "+cumByte+" MBps : "+(double)(cumByte)/1024 / 1024 /10);
        	}
        		
        	if(trafficInfo!=null)
        		writeTraffic(trafficInfo);

        	conncetedClients.put(clientBSSID, cumByte);
        }
      
	}
	public void updateTraffic()
	{
	
	}
	
	public void writeTraffic(String traffic)
	{
		File file=new File(fileName);
		FileWriter fw;
		try {
			fw = new FileWriter(file, true);
			//fw.write(traffic+"\n");
			fw.flush();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public void resetTrafficSilingWindow() {
		// TODO Auto-generated method stub
		slicingWindowList= new ArrayList<>();
		avgTraffic=0;
		
	}

}