package com.gcs.cn.client;

import static java.nio.channels.SelectionKey.OP_READ;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.gcs.cn.packet.Packet;

public class HttpClient {

	static int timeout = 3000;
	static long sequenceNum = 0;
	static int ackCount = 0;
	private boolean isHandshake;
	
	private String[] headers;
	private boolean verbose;
	private String query;
	private String postBody;
	private String host;
	private String path;
	private int port;
	private String file;
	private String outputFile;
	private boolean isPost;
	private boolean isGet;
	private boolean redirectionAllowed;

	private static final int PORT = 3000;

	private static final int BUFFER_SIZE = 1024;

	public HttpClient(String url, String[] headers, boolean verbose, String postBody, String oPFile,
			boolean redirectionAllowed, String file) {
		this.headers = headers;
		this.verbose = verbose;
		this.postBody = postBody;
		this.outputFile = oPFile;
		this.redirectionAllowed = redirectionAllowed;
		this.file = file;
		parseURL(url);
	}

	public HttpClient(String url, String[] headers, boolean verbose, String opFile, boolean redirectionAllowed) {
		this(url, headers, verbose, null, opFile, redirectionAllowed, null);
	}

	public HttpClient(String url, String[] headers, boolean verbose, String opFile, boolean redirectionAllowed,
			String file) {
		this(url, headers, verbose, null, opFile, redirectionAllowed, file);
	}

	private void parseURL(String url) {
		try {
			URL strURL = new URL(url);
			this.host = strURL.getHost();
			this.query = strURL.getQuery();
			this.path = strURL.getPath();
			this.port = strURL.getPort();

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public void get() {
		isGet = true;
		StringBuilder requestBuilder = new StringBuilder();
		if (this.query == null)
			this.query = "";

		requestBuilder.append("GET ").append(path).append("?").append(query).append(" HTTP/1.1\r\n").append("Host: ")
				.append(host).append("\r\n").append("User-Agent: Concordia-HTTP/1.0\r\n");

		if (headers != null) {
			for (String header : headers) {
				requestBuilder.append(header).append("\r\n");
			}
		}

		requestBuilder.append("\r\n"); // ending the request
		String request = requestBuilder.toString();

		System.out.println("***********GET REQUEST***********");
		System.out.println(request);
		System.out.println("***********RESPONSE***********");
		sendRequestToSocket(request, host);
	}

	private void performRedirection(String[] response) {
		String[] headers = response[0].split("\r\n");
		String locationPrefix = "Location: ";
		for (String headerLine : headers) {
			if (headerLine.contains(locationPrefix)) {
				int locationIndex = headerLine.indexOf(locationPrefix) + locationPrefix.length();
				String newUrl = headerLine.substring(locationIndex);
				// Debug statements
				System.out.println("=============================================");
				System.out.println("Redirection Detected: " + newUrl);
				System.out.println();
				parseURL(newUrl);
				break;
			}
		}

		if (isGet) {
			get();
		} else if (isPost) {
			post();
		}

	}

	private static void resend(DatagramChannel channel, Packet p, SocketAddress routerAddress) throws IOException {
		channel.send(p.toBuffer(), routerAddress);
		System.out.println(new String(p.getPayload()));
		if (new String(p.getPayload()).equals("Got the Message")) {
			ackCount++;
		}
		channel.configureBlocking(false);
		Selector selector = Selector.open();
		channel.register(selector, OP_READ);
		selector.select(timeout);

		Set<SelectionKey> keys = selector.selectedKeys();
		if (keys.isEmpty() && ackCount < 10) {
				System.out.println("Time is over");
				System.out.println("Retrying");
			resend(channel, p, routerAddress);
		} else {
			return;
		}
	}
	
	private void sendRequestToSocket(String request, String host) {
		if (port == -1) {
			port = PORT;
		}
//		SocketAddress endpoint = new InetSocketAddress(host, port);
//		try (SocketChannel socket = SocketChannel.open()) {
		try (DatagramChannel channel = DatagramChannel.open()) {
//			socket.connect(endpoint);
			
			SocketAddress routerAddress = new InetSocketAddress(host, 3000);
			InetSocketAddress serverAddress = new InetSocketAddress(host, port);
			isHandshake = handshake(routerAddress, serverAddress.getHostName(), serverAddress.getPort(), channel);
			sequenceNum++;
			Packet p = new Packet.Builder().setType(0).setSequenceNumber(sequenceNum).setPortNumber(serverAddress.getPort())
					.setPeerAddress(serverAddress.getAddress()).setPayload(request.getBytes())
					.create();

			channel.send(p.toBuffer(), routerAddress);
			channel.configureBlocking(false);
			Selector selector = Selector.open();
			channel.register(selector, OP_READ);
			selector.select(timeout);
			Set<SelectionKey> keys = selector.selectedKeys();
			if (keys.isEmpty()) {
				System.out.println("Time is over");
				System.out.println("Retrying");
				resend(channel, p, routerAddress);
			}

			ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
			SocketAddress router = channel.receive(buf);

			buf.flip();
			Packet resp = Packet.fromBuffer(buf);

			// write the request in
//			byte[] bs = request.getBytes(StandardCharsets.UTF_8);
//			ByteBuffer byteBuffer = ByteBuffer.wrap(bs);
//			socket.write(byteBuffer);

			// read the response from the socket
//			ByteBuffer responseBuff = ByteBuffer.allocate(BUFFER_SIZE);

//			while (socket.read(responseBuff) > 0) {

			// We can trim the array before we decode
//				byte[] responseBuffArr = responseBuff.array();

			// Split response for verbosity
			String line = new String(resp.getPayload(), StandardCharsets.UTF_8).trim();
//				String line = new String(responseBuffArr, StandardCharsets.UTF_8).trim();
			String[] lines = line.split("\r\n\r\n");

			if (HttpcUtil.isRedirect(lines) && redirectionAllowed) {
				performRedirection(lines);
				return;
			}
			if (lines.length >= 2) {
				if (verbose && outputFile != null) {
					StringBuilder builder = new StringBuilder();
					builder.append(lines[0]);
					builder.append("\n").append(lines[1]).append("\n");
					HttpcUtil.saveResponseIntoOutputFile(builder.toString(), outputFile);
				} else if (!verbose && outputFile != null) {
					StringBuilder builder = new StringBuilder();
					builder.append(lines[1]);
					HttpcUtil.saveResponseIntoOutputFile(builder.toString(), outputFile);
				} else if (verbose) {
					System.out.println(lines[0] + "\n" + lines[1]);
				} else
					System.out.println(lines[1]);
			}

//			}

			channel.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	public void post() {
		isPost = true;
		StringBuilder reqBuilder = new StringBuilder();
		if (query == null) {
			query = "";
		}
		reqBuilder.append("POST ").append(path).append("?").append(query).append(" HTTP/1.1\r\n").append("Host: ")
				.append(host).append("\r\n").append("User-Agent: Concordia-HTTP/1.0\r\n");

		if (headers != null) {
			for (String header : headers) {
				reqBuilder.append(header.strip()).append("\r\n");
			}
		}

		if (file != null) {
			Path filePath = Path.of(file);
			try {
				// instead of file content save it back to file
				file = Files.readString(filePath).replace("\n", "");

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (postBody != null) {
			reqBuilder.append("Content-Length: ").append(postBody.length()).append("\r\n");
		}
		if (file != null) {
			reqBuilder.append("Content-Length: ").append(file.length()).append("\r\n");
		}
		// Ending of the request header
		reqBuilder.append("\r\n");

		// Add the postBody
		if (postBody != null) {
			reqBuilder.append(postBody);
		}
		if (file != null) {
			reqBuilder.append(file);
		}

		System.out.println("***********POST REQUEST***********");
		String request = reqBuilder.toString();
		System.out.println(request);
		System.out.println();
		System.out.println("***********RESPONSE***********");
		sendRequestToSocket(request, host);

	}
	
	public static boolean handshake(SocketAddress routerAddress, String serverHost, int serverPort, DatagramChannel channel) {
        boolean handShake = false;

        // Always perform a handshake before the initial request.
        while (!handShake) {
            boolean sendSyn = syn(routerAddress, serverHost, serverPort, "msg", channel);

            // Only return true when the whole thing comes back. Check at each step.
            if (sendSyn) {
                boolean sendAck = ack(routerAddress, serverHost, serverPort, "msg", channel);
                if (sendAck) {
                    System.out.println("--------------------HANDSHAKE COMPLETE-----------------");
                    handShake = true;
                }
            }
        }

        return true;
    }
	
	public static boolean ack(SocketAddress routerAddress, String serverAddr, int serverPort, String message, DatagramChannel channel) {
        while (true) {
            try {
                InetAddress peerIp = InetAddress.getByName(serverAddr);
//                DatagramSocket conn = new DatagramSocket();
                
//                conn.setSoTimeout(5000); // Set timeout in milliseconds

                // Packet type 3 (ACK). Have the server recognize the packet_type and return a 2 (SYN-ACK)
                Packet p = new Packet(3, 1, peerIp, serverPort, message.getBytes(StandardCharsets.UTF_8));
                System.out.println("[CLIENT] - Sending ACK");
//                DatagramPacket sendPacket = new DatagramPacket(p.toBytes(), p.toBytes().length, InetAddress.getByName(routerAddr), routerPort);
                
                channel.send(p.toBuffer(), routerAddress);

                // Receive a response within the timeout
                System.out.println("[CLIENT] - Waiting For A Response - (Should be an ACK)");
//                byte[] receiveData = new byte[1024];
//                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                //conn.receive(receivePacket);
                ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                channel.receive(buf);
                p = Packet.fromBuffer(buf);

                System.out.println("[CLIENT] - Response Received. Is it a SYN-ACK? (Packet of Type 3)");
                System.out.println("[CLIENT] - PacketType = " + p.getType());
                System.out.println("[CLIENT] - Yes, Got an ACK back. Proceed with request.");
                return true;

            } catch (SocketTimeoutException e) {
                System.out.println("[CLIENT] - No response after 5s");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
	
	public static boolean syn(SocketAddress routerAddress, String serverAddr, int serverPort, String message, DatagramChannel channel) {
        while (true) {
            try {
                InetAddress peerIp = InetAddress.getByName(serverAddr);
                //DatagramSocket conn = new DatagramSocket();
                //conn.setSoTimeout(5000); // Set timeout in milliseconds

                Packet p = new Packet(1, 1, peerIp, serverPort, message.getBytes());
                System.out.println(" \n ");
                System.out.println("-------------------BEGINNING HANDSHAKE-----------------");
                System.out.println("[CLIENT] - Sending SYN - (PacketType = 1)");
//                DatagramPacket sendPacket = new DatagramPacket(p.toBytes(), p.toBytes().length, InetAddress.getByName(routerAddress), routerPort);
                channel.send(p.toBuffer(), routerAddress);
                //conn.send(sendPacket);
                

                // Receive a response within the timeout
                //conn.setSoTimeout(5000);
                System.out.println("[CLIENT] - Waiting For A Response - (Should be an SYN-ACK)");
//                byte[] receiveData = new byte[1024];
//                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
//                conn.receive(receivePacket);
                ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                channel.receive(buf);
                p = Packet.fromBuffer(buf);
//                p = Packet.fromBytes(receivePacket.getData());

                System.out.println("[CLIENT] - Response Received. Is it a SYN-ACK? (Packet Type of 2)");
                System.out.println("[CLIENT] - PacketType = " + p.getType());

                if (p.getType() == 2) {
                    System.out.println("[CLIENT] - Yes, Got a SYN-ACK back, send back ACK (Packet Type of 3)");
                    // Just send packet of type 3 here and don't get anything back.
                    return true;
                }

            } catch (SocketTimeoutException e) {
                System.out.println("[CLIENT] - No response after 5s for Packet 1");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



}
