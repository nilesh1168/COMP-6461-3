package com.gcs.cn.client;

import static java.nio.channels.SelectionKey.OP_READ;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gcs.cn.packet.Packet;

import joptsimple.OptionParser;
import joptsimple.OptionSet;


public class UDPClient {

	private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);
	
	public static void main(String[] args) throws IOException {
		 OptionParser parser = new OptionParser();
	        parser.accepts("router-host", "Router hostname")
	                .withOptionalArg()
	                .defaultsTo("localhost");

	        parser.accepts("router-port", "Router port number")
	                .withOptionalArg()
	                .defaultsTo("3000");

	        parser.accepts("server-host", "EchoServer hostname")
	                .withOptionalArg()
	                .defaultsTo("localhost");

	        parser.accepts("server-port", "EchoServer listening port")
	                .withOptionalArg()
	                .defaultsTo("8007");

	        OptionSet opts = parser.parse(args);

	        // Router address
	        String routerHost = (String) opts.valueOf("router-host");
	        int routerPort = Integer.parseInt((String) opts.valueOf("router-port"));

	        // Server address
	        String serverHost = (String) opts.valueOf("server-host");
	        int serverPort = Integer.parseInt((String) opts.valueOf("server-port"));

	        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
	        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

	        runClient(routerAddress, serverAddress);

	}
	
	private static void runClient(SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
            String msg = "Hello World";
            Packet p = new Packet.Builder()
                    .setType(0)
                    .setSequenceNumber(1L)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(msg.getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);

            logger.info("Sending \"{}\" to router at {}", msg, routerAddr);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            logger.info("Waiting for the response");
            selector.select(5000);

            Set<SelectionKey> keys = selector.selectedKeys();
            if(keys.isEmpty()){
                logger.error("No response after timeout");
                return;
            }

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet resp = Packet.fromBuffer(buf);
            logger.info("Packet: {}", resp);
            logger.info("Router: {}", router);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            logger.info("Payload: {}",  payload);

            keys.clear();
        }
    }
	
	
	
//	private static void srtransfer(DatagramSocket socket, InitiateTransfer initiateTransfer)
//			throws IOException, ClassNotFoundException {
//// Segment Data ~ Packet
//		ArrayList<SegmentData> received = new ArrayList<>();
//		boolean end = false;
//		int waitingFor = 0;
//		byte[] incomingData = new byte[1024];
//
//		ArrayList<SegmentData> buffer = new ArrayList<>();
//
//		while (!end) {
//
//			DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
//			socket.receive(incomingPacket);
//			InetAddress IPAddress = incomingPacket.getAddress();
//			int port = incomingPacket.getPort();
//			byte[] data = incomingPacket.getData();
//			ByteArrayInputStream in = new ByteArrayInputStream(data);
//			ObjectInputStream is = new ObjectInputStream(in);
//			SegmentData segmentData = (SegmentData) is.readObject();
//
//			char ch = segmentData.getPayLoad();
//			int hashCode = ("" + ch).hashCode();
//			boolean checkSum = (hashCode == segmentData.getCheckSum());
//
//			if (segmentData.getSeqNum() == waitingFor && segmentData.isLast() && checkSum) {
//
//				waitingFor++;
//				received.add(segmentData);
//				int value = sendData(segmentData, waitingFor, socket, IPAddress, port, false);
//				if (value < waitingFor) {
//					waitingFor = value;
//					int length = received.size();
//					System.out.println(" \n !!!!!!  Packet " + (waitingFor) + " lost in the Transmission");
//					System.out.println("!!!!!!!!!!!!!!!!!!!!Packet Lost!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! \n");
//					received.remove(length - 1);
//					end = false;
//
//				} else {
//
//					System.out.println("!!!!!!!! Last packet received !!!!!!!!!!! \n");
//					end = true;
//				}
//
//			}
//
//			else if (segmentData.getSeqNum() == waitingFor && checkSum && buffer.size() > 0) {
//
//				received.add(segmentData);
//				waitingFor++;
//				int value = sendData(segmentData, waitingFor, socket, IPAddress, port, false);
//				if (value < waitingFor) {
//					waitingFor = value;
//					int length = received.size();
//					System.out.println(" \n !!!!!!  Packet " + (waitingFor) + " lost in the Transmission");
//					System.out.println("!!!!!!!!!!!!!!!!!!!!Packet Lost!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! \n");
//					received.remove(length - 1);
//
//				} else {
//					ArrayList<SegmentData> temp = new ArrayList<>();
//					temp.addAll(buffer);
//					int count = 0;
//					for (int i = 0; i < temp.size(); i++) {
//						if (!(waitingFor == temp.get(i).getSeqNum())) {
//
//							break;
//
//						} else {
//							waitingFor++;
//							count++;
//							System.out.println(
//									"Packet " + buffer.get(i).getSeqNum() + " delivered to Application From Buffer");
//						}
//					}
//					buffer = new ArrayList<>();
//					for (int j = 0; j < temp.size(); j++) {
//						if (j < count) {
//							continue;
//						}
//						buffer.add(temp.get(j));
//					}
//					if (waitingFor == initiateTransfer.getNumPackets()) {
//						end = true;
//					}
//
//				}
//
//			}
//
//			else if (segmentData.getSeqNum() == waitingFor && checkSum && buffer.size() == 0) {
//				received.add(segmentData);
//				waitingFor++;
//				int value = sendData(segmentData, waitingFor, socket, IPAddress, port, false);
//				if (value < waitingFor) {
//					waitingFor = value;
//					int length = received.size();
//					System.out.println(" \n !!!!!!  Packet " + (waitingFor) + " lost in the Transmission");
//					System.out.println("!!!!!!!!!!!!!!!!!!!!Packet Lost!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! \n");
//					received.remove(length - 1);
//
//				}
//
//				else {
//
//				}
//
//			}
//
//			else if (segmentData.getSeqNum() > waitingFor && checkSum) {
//
//				sendData(segmentData, waitingFor, socket, IPAddress, port, true);
//				System.out.println("!!!!!!!!!!!!!!!!! Packet " + segmentData.getSeqNum()
//						+ " Stored in Buffer  !!!!!!!!!!!!!!!!!!!!!!!!!! \n");
//				buffer.add(segmentData);
//				Collections.sort(buffer);
//
//			}
//
//			else if (segmentData.getSeqNum() < waitingFor && checkSum) {
//				
//				sendData(segmentData, waitingFor, socket, IPAddress, port, true);
//				System.out.println(
//						" !!!!!!!!!!!!!!!!! Packet Already Delivered  Sending Duplicate Ack !!!!!!!!!!!!!!!!!!!!!!!!!!");
//
//			}
//
//			else if (!checkSum) {
//				System.out.println(" \n Packet " + (segmentData.getSeqNum()) + " received");
//				System.out.println("!!!!!!!!!! Checksum Error !!!!!!!!!!!!!!!!!");
//				System.out.println("!!!!!!!!!! Packet " + segmentData.getSeqNum() + " Discarded !!!!!!!!!!!!!!!!! \n");
//				segmentData.setSeqNum(-1000);
//			}
//
//			else {
//
//				System.out.println("!!!!!!!!!! Packet " + segmentData.getSeqNum() + " Discarded !!!!!!!!!!!!!!!!! \n");
//				segmentData.setSeqNum(-1000);
//			}
//
//		}
//
//	}

}
