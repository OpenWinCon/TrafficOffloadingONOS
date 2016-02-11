package sdn.trafficofflodingapp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import android.os.AsyncTask;
import android.util.Log;


class TrafficOffloadingSender extends AsyncTask<Object, Void, Void> {
    String LOG_TAG = "TrafficOffloading";

    @Override
    protected Void doInBackground(Object... params) {
        String message = (String)params[0];
        InetAddress ip = (InetAddress)params[1];
        int port = (Integer)params[2];
        byte[] buf = message.getBytes();

        try {  
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, ip, port);
            socket.send(packet);
            Log.e(LOG_TAG,"send packet:"+packet.getData().toString() );
            socket.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to send udp packet");
            e.printStackTrace();
        }
        return null;
    }

}
