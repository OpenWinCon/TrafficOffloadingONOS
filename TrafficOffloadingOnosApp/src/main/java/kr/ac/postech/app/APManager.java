package kr.ac.postech.app;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




class APManager implements Runnable {

	private final Logger log = LoggerFactory.getLogger(getClass());
    // Message types
    private final String MSG_CLIENT_INFO = "client";
    private final String MSG_CLIENT_DISCONNECT = "clientdisconnect";
    private final String MSG_CLIENT_SCAN = "scan";

    private final int SERVER_PORT;
    InetAddress ClientIP;
    int clientPort;
    private DatagramSocket controllerSocket;
    private final ExecutorService executor;
    private final trafficMon master;
    boolean ServerStarted;


    public APManager (trafficMon m, int port, ExecutorService executor) {
        this.master = m;
        this.SERVER_PORT = port;
        this.executor = executor;
        ServerStarted=true;
       // this.controllerSocket=controllerSocket;
        
    }

    @Override
    public void run() {
        try {
        	controllerSocket = new DatagramSocket(SERVER_PORT);
            //controllerSocket = new DatagramSocket(null);
            //InetSocketAddress address= new InetSocketAddress("141.223.107.139", SERVER_PORT);
            //controllerSocket.bind(address);
        } catch (IOException e) {
        	log.info("create new controllerSocket fail: " + SERVER_PORT);
            e.printStackTrace();
        }

        log.info("--------------Start APManger--------------");
        while(ServerStarted/*!Thread.interrupted()*/){
            try {
                final byte[] receiveData = new byte[1280]; // probably this could be smaller
                DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
                controllerSocket.receive(receivedPacket);
                log.info("packet receive from "+ receivedPacket.getAddress());
                //log.info("packet receive send "+ receivedPacket.getAddress()+" port:"+receivedPacket.getPort());
                //DatagramPacket tsendPacket = new DatagramPacket(receivedPacket.getData(), receivedPacket.getLength(), receivedPacket.getAddress(), receivedPacket.getPort());
                //controllerSocket.send(tsendPacket);
                //10.1.100.11 1622

                
                	//	 DatagramSocket socket = new DatagramSocket(4444);
                         //InetSocketAddress address= new InetSocketAddress("141.223.107.139", 1623);
                         //socket.bind(address);
                      //   InetAddress clientAddress = InetAddress.getByName("10.1.100.11");
                         
                         
                         /*
                         final byte[] buf = new byte[1280];
                         System.arraycopy("scan".getBytes(), 0, buf, 0, "scan".getBytes().length);
                   
                         DatagramPacket packet = new DatagramPacket(buf, receiveData.length, clientAddress, 1622);
                         socket.send(packet);
                         
         				log.info("apmanger: packet send to " + receivedPacket.getAddress() + " " + receivedPacket.getPort()+" size: "+receiveData.length);
         				socket.close();
         				*/
         			//	DatagramSocket socket2 = new DatagramSocket(5555);
         			//	DatagramPacket packet2 = new DatagramPacket(receivedPacket.getData(), receiveData.length, clientAddress, 1622);
                     //DatagramPacket packet = new DatagramPacket(receivedPacket.getData(), receivedPacket.getLength(), receivedPacket.getAddress(), receivedPacket.getPort());
                    // socket2.send(packet2);
                     
     				//log.info("apmanger: packet send to " + receivedPacket.getAddress() + " " + receivedPacket.getPort());
     				//socket2.close();
             
                executor.execute(new ConnectionHandler(receivedPacket));
                //String message = new String(receivedPacket.getData()).trim();
                receivedPacket.getAddress();
                
                /* ********************************************************** */
             //   controllerSocket.close();
                /* ********************************************************** */
            }
            catch (Exception e) {
          
            //    controllerSocket.close();
            	log.info("controllerSocket.accept() failed: " + SERVER_PORT);
            //    endConnection();
             //   controllerSocket.close();
            	ServerStarted=false;
                e.printStackTrace();
            
            } 
        }
        controllerSocket.close();
    }
    
    public void endConnection()
    {
    	ServerStarted=false;
    }
    

    /** Protocol handlers **/

    private void receiveClientInfo(final InetAddress agentAddr,
            final int agentPort, final String clientMacAddr) {
            master.receiveClientInfo(agentAddr, agentPort, clientMacAddr, controllerSocket);
    }


    private void clientDisconnect(final InetAddress agentAddr,
            final String clientEthAddr) {
        master.clientDisconnect(agentAddr, clientEthAddr);
    }

    private void receiveScanResult(String[] fields) {
        master.receiveScanResult(fields);
    }

    private class ConnectionHandler implements Runnable {
        final DatagramPacket receivedPacket;

        public ConnectionHandler(final DatagramPacket dp) {
            receivedPacket = dp;
        }

        // AP Agent message handler
        public void run() {
            final String msg = new String(receivedPacket.getData()).trim().toLowerCase();
            final String[] fields = msg.split("\\|");
            final String msg_type = fields[0];
            final InetAddress agentAddr = receivedPacket.getAddress();
            log.info("handler get data"+msg);
            if (msg_type.equals(MSG_CLIENT_INFO)) {
                final int agentPort = receivedPacket.getPort() /*fields[1]*/;
                final String clientMacAddr = fields[1];
                String clientIP = fields[2];
                log.info("find public ap/private ap(0)="+agentAddr+", "+fields[2]);
                
                String[] ipfields = clientIP.split("\\.");
                String[] ipAPfields = agentAddr.getHostAddress().split("\\.");
                log.info("find public ap/private ap="+ipfields[1]);
                if(ipAPfields[0].equals("/192"))// && ipfields[1].equals("168") && ipfields[2].equals("100"))
                {
                	InetAddress clip = null;
					try {
						clip = InetAddress.getByName(clientIP);
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                	receiveClientInfo(clip, agentPort, clientMacAddr);
                }
                else
                	receiveClientInfo(agentAddr, agentPort, clientMacAddr);
                log.info("receive client info");
                
            } else if (msg_type.equals(MSG_CLIENT_DISCONNECT)) {
                final String clientEthAddr = fields[1];
                clientDisconnect(agentAddr, clientEthAddr);
                
            } else if (msg_type.equals(MSG_CLIENT_SCAN)) {
            	log.info("receive AP singnal from "+ fields[1]);
            	
                System.out.println("\n");
            	 for (int i = 0; i < fields.length; i++) {
                     String[] info = fields[i].split("&");
                     log.info("info length: "+ info.length);
                     if(info.length>=2&&!info[0].equals("")){
                     String ssid = info[0];
                     String bssid = info[1].toLowerCase();
                     int level = Integer.parseInt(info[2]);
                    System.out.println("SSID: "+ ssid+"\tBSSID: "+bssid+"\tSignalStrength:"+level);
                     }
                    
            	 }
                receiveScanResult(fields);
            } 

        }
    }

}