package kr.ac.postech.app;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




class APManager1 implements Runnable {

	private final Logger log = LoggerFactory.getLogger(getClass());
    // Message types
    private final String MSG_CLIENT_INFO = "client";
    private final String MSG_CLIENT_DISCONNECT = "clientdisconnect";
    private final String MSG_CLIENT_SCAN = "scan";
    
    private final int SERVER_PORT;
    InetAddress ClientIP;
    int clientPort;
    private ServerSocket controllerSocket;
    private final ExecutorService executor;
    private final trafficMon master;
    boolean ServerStarted;


    public APManager1 (trafficMon m, int port, ExecutorService executor) {
        this.master = m;
        this.SERVER_PORT = port;
        this.executor = executor;
        ServerStarted=true;
       // this.controllerSocket=controllerSocket;
        
    }

    @Override
    public void run() {
        try {
            controllerSocket = new ServerSocket(SERVER_PORT);

        } catch (IOException e) {
        	log.info("create new controllerSocket fail: " + SERVER_PORT);
            e.printStackTrace();
        }

        log.info("--------------Start APManger--------------");
        while(!Thread.interrupted()){
            try {
                final byte[] receiveData = new byte[1280]; // probably this could be smaller
                DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
                
                Socket socket = controllerSocket.accept();
                BufferedReader br;
                BufferedWriter bw;
                br=new BufferedReader(new InputStreamReader(socket.getInputStream()));
                bw=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                String receivedData= br.readLine();
                log.info("controllerSocket.receive " + receivedData);
                //controllerSocket.receive(receivedPacket);
                bw.write(receivedData); bw.flush();
                br.close();
                bw.close();
                socket.close();
               
     
            }
            catch (Exception e) {
          
            //    controllerSocket.close();
            	log.info("controllerSocket.accept() failed: " + SERVER_PORT);
            //    endConnection();
             //   controllerSocket.close();
                e.printStackTrace();
            
            } 
        }
    }
}