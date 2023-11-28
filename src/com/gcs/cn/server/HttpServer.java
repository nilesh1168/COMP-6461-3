package com.gcs.cn.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class HttpServer implements Runnable{
    static int port = 80;
    static boolean verbose;
    String directory;
    Object lock = new Object();


//    private ServerSocketChannel socket;
    private DatagramChannel channel;
    public HttpServer(boolean verbose, String directory, int port) {
    	if(port > 0)
    		HttpServer.port = port;
    		
        HttpServer.verbose = verbose;
        this.directory = directory;
    }


	@Override
    public void run() {
        try {
        	channel = DatagramChannel.open();
//            socket = ServerSocketChannel.open();
            System.out.println("Starting HTTPServer at localhost:" + port);
            channel.bind(new InetSocketAddress(port));
//            socket.bind(new InetSocketAddress(port));
//            while(true){
//                SocketChannel connection = socket.accept();
//                Thread t = new Thread(new HttpRequestHandler(connection, lock));
                Thread t = new Thread(new HttpRequestHandler(channel, lock));
                t.start();
//            }
        }catch (IOException exception){
            System.out.println(exception.getMessage());
        }

    }
}
