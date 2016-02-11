package kr.ac.postech.app;

import java.util.concurrent.ExecutorService;

import static org.onlab.util.Tools.delay;




/**
 * Created by offloading on 16. 2. 5.
 */
public class TrafficMonitor implements Runnable {


    private final ExecutorService executor;
    private final trafficMon master;

    public TrafficMonitor (trafficMon m, ExecutorService executor) {
        this.master = m;
        this.executor = executor;
    }


    @Override
    public void run() {


        while(true)	{


            System.out.println("aaa");

            delay(5000);
        }


    }
}



