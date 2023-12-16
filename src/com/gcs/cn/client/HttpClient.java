package com.gcs.cn.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


import com.gcs.cn.packet.Packet;


public class HttpClient {
	public static final String EOM = "<EOM>";

	static int timeout = 5000;
	static int ackCount = 0;
	private static boolean handshake = false;
	private static int base = 0;
	private static int windowSize = 5;
	private static StringBuffer[] dataReceived = new StringBuffer[1024];

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

	private static void sendUdpMessages(List<String> dataChunks, DatagramSocket conn, InetSocketAddress serverAddress,
			SocketAddress routerAddress, int[] arr, StringBuilder dataString) throws IOException {
		int[] windows = { 0, 0, 0, 0, 0 };

		for (int seqNum = base; seqNum < Math.min(base + windowSize, dataChunks.size()); seqNum++) {
			if (arr != null && arr[seqNum] == 1) {
				windows[seqNum] = 1;
				continue;
			}

			String chunk = dataChunks.get(seqNum);
			Packet p = new Packet(0, seqNum, serverAddress.getAddress(), serverAddress.getPort(), chunk.getBytes());

			byte[] packetBytes = p.toBytes();
			DatagramPacket packet = new DatagramPacket(packetBytes, packetBytes.length, routerAddress);

			try {
				conn.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try{
			while (true) {
				byte[] receiveData = new byte[1024];

				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				conn.setSoTimeout(timeout);
				conn.receive(receivePacket);
				byte[] data = new byte[receivePacket.getLength()];
				System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
						receivePacket.getLength());
				Packet p = Packet.fromBytes(data);

				if (p.getType() == 3) {
					windows[(int) p.getSequenceNumber()] = 1;
					boolean allAcked = true;
					for (int seqNum = base; seqNum < Math.min(base + windowSize, dataChunks.size()); seqNum++) {
						if (windows[seqNum] == 0) {
							allAcked = false;
							break;
						}
					}
					if (allAcked) {
						conn.setSoTimeout(0);
					}

				}
				else{
					int currSeqNum = (int) p.getSequenceNumber();
					String payLoad = new String(p.getPayload(), UTF_8);
					dataReceived[currSeqNum] = new StringBuffer();
					dataReceived[currSeqNum].append(payLoad);


					p = p.toBuilder().setType((byte) 3).setPayload("sending ACK from Client!".getBytes()).create();
					
					byte[] responseBytes = p.toBytes();

					DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length,
							routerAddress);
					
					conn.send(responsePacket);

					boolean allDataReceived = true;


					if (dataReceived[currSeqNum].toString().endsWith(EOM)) {
						for (int i = 0; i <= currSeqNum; i++) {
							dataString.append(dataReceived[i].toString());
							if (dataReceived[i] == null) {
								allDataReceived = false;
								break;
							}
						}
						if (allDataReceived) {
							conn.close();
							break;
						}
					}

				}
			}
		} catch (SocketTimeoutException e) {
			System.out
					.println("[CLIENT] - No response after " + timeout + " for Packet " + windows[windows.length - 1]);
			sendUdpMessages(dataChunks, conn, serverAddress, routerAddress, windows, dataString);
		} catch (IOException e) {
			System.out.println(e.getMessage());
		} finally {
			conn.close();
		}
	}

	private void sendRequestToSocket(String request, String host) {

		// append EOM char to request
		// breakdown request into 1013 bytes packets
		// for all the packets made keep sending packets to the client with incremental
		// sequence number

		if (port == -1) {
			port = PORT;
		}
		try {

			SocketAddress routerAddress = new InetSocketAddress("localhost", 3000);
			InetSocketAddress serverAddress = new InetSocketAddress("localhost", 8080);

			DatagramSocket conn = new DatagramSocket();
			handshake = handshake(routerAddress, serverAddress.getHostName(), serverAddress.getPort(), conn);
			if (handshake) {
				request = request + EOM;


				List<String> dataChunks = new ArrayList<>();
				for (int i = 0; i < request.length(); i += 1013) {
					dataChunks.add(request.substring(i, Math.min(i + 1013, request.length())));
				}
				StringBuilder dataString = new StringBuilder();
				sendUdpMessages(dataChunks, conn, serverAddress, routerAddress, null, dataString);

				System.out.println("***********RESPONSE***********");
				
				String finalDataString = dataString.substring(0,dataString.length()-5);
				String[] lines = finalDataString.toString().split("\r\n\r\n");

				if (redirectionAllowed && HttpcUtil.isRedirect(lines)) {
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

				conn.close();
			} // need to handle if there is no handshake

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

	public static boolean handshake(SocketAddress routerAddress, String serverHost, int serverPort,
			DatagramSocket conn) {

		// Always perform a handshake before the initial request.
		while (!handshake) {
			boolean sendSyn = syn(routerAddress, serverHost, serverPort, "msg", conn);

			// Only return true when the whole thing comes back. Check at each step.
			if (sendSyn) {
				boolean sendAck = ack(routerAddress, serverHost, serverPort, "msg", conn);
				if (sendAck) {
					System.out.println("--------------------HANDSHAKE COMPLETE-----------------");
					handshake = true;
				}
			}
		}

		return true;
	}

	public static boolean ack(SocketAddress routerAddress, String serverAddr, int serverPort, String message,
			DatagramSocket conn) {
		while (true) {
			try {
				InetAddress peerIp = InetAddress.getByName(serverAddr);

				// Packet type 3 (ACK). Have the server recognize the packet_type and return a 2
				// (SYN-ACK)
				Packet p = new Packet(3, 1, peerIp, serverPort, message.getBytes(StandardCharsets.UTF_8));
				System.out.println("[CLIENT] - Sending ACK");
				DatagramPacket sendPacket = new DatagramPacket(p.toBytes(), p.toBytes().length, routerAddress);

				conn.send(sendPacket);

				conn.setSoTimeout(5000); // Set timeout in milliseconds
				// Receive a response within the timeout
				System.out.println("[CLIENT] - Waiting For A Response - (Should be an ACK)");
				byte[] receiveData = new byte[1024];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				conn.receive(receivePacket);
				byte[] data = new byte[receivePacket.getLength()];
				System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
						receivePacket.getLength());
				p = Packet.fromBytes(data);


				System.out.println("[CLIENT] - Response Received. Is it a SYN-ACK? (Packet of Type 3)");
				System.out.println("[CLIENT] - PacketType = " + p.getType());
				if (p.getType() == 3) {
					System.out.println("[CLIENT] - Yes, Got an ACK back. Proceed with request.");
					return true;
				}

			} catch (SocketTimeoutException e) {
				System.out.println("[CLIENT] - No response after 5s");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static boolean syn(SocketAddress routerAddress, String serverAddr, int serverPort, String message,
			DatagramSocket conn) {
		while (true) {
			try {
				InetAddress peerIp = InetAddress.getByName(serverAddr);

				Packet p = new Packet(1, 1, peerIp, serverPort, message.getBytes());
				System.out.println(" \n ");
				System.out.println("-------------------BEGINNING HANDSHAKE-----------------");
				System.out.println("[CLIENT] - Sending SYN - (PacketType = 1)");
				DatagramPacket sendPacket = new DatagramPacket(p.toBytes(), p.toBytes().length, routerAddress);
				conn.send(sendPacket);

				// Receive a response within the timeout
				conn.setSoTimeout(5000);
				System.out.println("[CLIENT] - Waiting For A Response - (Should be an SYN-ACK)");

				byte[] receiveData = new byte[1024];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				conn.receive(receivePacket);
				byte[] data = new byte[receivePacket.getLength()];
				System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
						receivePacket.getLength());
				p = Packet.fromBytes(data);

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
