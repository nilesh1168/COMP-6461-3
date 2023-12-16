package com.gcs.cn.server;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


import com.gcs.cn.packet.Packet;

// Packet Type 0 - PACKET
//Packet Type 1 - SYN
//Packet Type 2 - SYN-ACK
//Packet Type 3 - ACK

public class ServerUtil {
	public static final String EOM = "<EOM>";
	private static final int timeout = 5000;
	private static int base = 0;
	private static int windowSize = 5;
	private static StringBuffer[] dataReceived = new StringBuffer[1024];

	public static void displayHelpInfo() {
		String helpInfo = "httpfs is a simple file server.\n" + "\n" + "    Usage:\n"
				+ "        httpfs [-v] [-p PORT] [-d PATH-TO-DIR]\n" + "\n" + "    The commands are:\n"
				+ "        -v      Prints debugging messages.\n"
				+ "        -p      Specifies the port number that the server will listen and serve at. (Default is 8080)\n"
				+ "        -d      Specifies the directory that the server will use to read/write requested files. Default is the current directory when launching the application.";

		System.out.println(helpInfo);

	}

	public static HttpServer optionsParser(List<String> subList) {
		ArrayList<String> args = new ArrayList<>(subList);
		boolean verbose = false;
		int port = 0;
		String directory = "";
		if (args.contains("-v")) {
			args.remove("-v");
			verbose = true;
		}
		if (args.contains("-p")) {
			int index = args.indexOf("-p");
			args.remove("-p");
			String portString = args.get(index);
			port = Integer.parseInt(portString);
			args.remove(portString);
		}
		if (args.contains("-d")) {
			int index = args.indexOf("-d");
			args.remove("-d");
			httpfs.directory = args.get(index);
			args.remove(directory);
		}

		return new HttpServer(verbose, directory, port);
	}


	private static void sendUdpMessages(List<String> dataChunks, DatagramSocket conn, InetSocketAddress serverAddress,
			SocketAddress routerAddress, int[] arr, Packet pkt) throws IOException {
		int[] windows = { 0, 0, 0, 0, 0 };

		for (int seqNum = base; seqNum < Math.min(base + windowSize, dataChunks.size()); seqNum++) {
			if (arr != null && arr[seqNum] == 1) {
				windows[seqNum] = 1;
				continue;
			}

			String chunk = dataChunks.get(seqNum);
			
			Packet p = pkt.toBuilder().setType(0).setSequenceNumber(0).setPayload(chunk.getBytes()).create();

			byte[] packetBytes = p.toBytes();
			DatagramPacket packet = new DatagramPacket(packetBytes, packetBytes.length, routerAddress);

			try {
				conn.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try(DatagramSocket receiveSocket = new DatagramSocket()) {
			while (true) {
				byte[] receiveData = new byte[1024];

				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				receiveSocket.setSoTimeout(timeout);
				receiveSocket.receive(receivePacket);
				byte[] data = new byte[receivePacket.getLength()];
				System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, receivePacket.getLength());


				String requestPayload = "";
				Packet packet = Packet.fromBytes(data);
				requestPayload = new String(packet.getPayload(), UTF_8);

				if (packet.getType() == 1) {
					

				packet = packet.toBuilder().setType(2).setPayload("SYN Recieved!".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);
			}
				if (packet.getType() == 3) {					

					windows[(int) packet.getSequenceNumber()] = 1;
					boolean allAcked = true;

					for (int seqNum = base; seqNum < Math.min(base + windowSize, dataChunks.size()); seqNum++) {
						if (windows[seqNum] == 0) {
							allAcked = false;
							break;
						}
					}
					if (allAcked) {
						receiveSocket.setSoTimeout(0);
					}

				} else if (packet.getType() == 0) { // is a data packet

					int currSeqNum = (int) packet.getSequenceNumber();
					String payLoad = new String(packet.getPayload(), UTF_8);

					dataReceived[currSeqNum] = new StringBuffer();
					dataReceived[currSeqNum].append(payLoad);


					packet = packet.toBuilder().setType((byte) 3).setPayload("Sending ack for payload".getBytes())
							.create();
					byte[] responseBytes = packet.toBytes();

					DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length,routerAddress);
					conn.send(responsePacket);

					StringBuilder dataString = new StringBuilder();
					boolean allDataReceived = true;

					if (dataReceived[currSeqNum].toString().endsWith(EOM)) {
						for (int i = 0; i < currSeqNum; i++) {
							if (dataReceived[i] == null) {
								allDataReceived = false;
								break;
							}
						}
						if (allDataReceived) {
							for (int i = 0; i < dataReceived.length; i++) {
								if(dataReceived[i]!=null)
									dataString.append(dataReceived[i].toString());
							}
							System.out.println(dataString.toString());
						}
					}

				}

			}

		} catch (SocketTimeoutException e) {
			System.out
					.println("[SERVER] - No response after " + timeout + " for Packet " + windows[windows.length - 1]);
			sendUdpMessages(dataChunks, conn, serverAddress, routerAddress, windows,pkt);
		} catch (IOException e) {
			
		} finally {
			conn.close();
		}
	}

	public static Packet getRequest(DatagramSocket conn) throws IOException {
		if (HttpServer.verbose) {
			System.out.println("Verbose: True");
			System.out.println("Working directory: " + httpfs.directory);
		}

		SocketAddress routerAddress = new InetSocketAddress(InetAddress.getLocalHost(), 3000);
		InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
		String response = "";
		Packet packet;
		while (true) {

			byte[] receiveData = new byte[1024];

			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			conn.receive(receivePacket);
			byte[] data = new byte[receivePacket.getLength()];
			System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, receivePacket.getLength());
			
			
			packet = Packet.fromBytes(data);

			int packetType = packet.getType();
			if (packetType == 1) {
				

				packet = packet.toBuilder().setType(2).setPayload("SYN Recieved!".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);
			} else if (packetType == 3) {
				packet = packet.toBuilder().setType(3).setPayload("ACK Recieved!".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);
			} else {
				String payLoad = new String(packet.getPayload(), UTF_8);

				packet = packet.toBuilder().setType((byte) 3).setPayload("Sending ack for payload".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);

				int currSeqNum = (int) packet.getSequenceNumber();
				dataReceived[currSeqNum] = new StringBuffer();
				dataReceived[currSeqNum].append(payLoad);

				StringBuilder dataString = new StringBuilder();
				if (dataReceived[currSeqNum].toString().endsWith(EOM)) {
					boolean allDataReceived = true;
					for (int i = 0; i < currSeqNum; i++) {
						if (dataReceived[i] == null) {
							allDataReceived = false;
							break;
						}
					}
					if (allDataReceived) {
						
						for (int i = 0; i < dataReceived.length; i++) {
							if(dataReceived[i]!=null)
								dataString.append(dataReceived[i].toString());
						}
						String finalDataString = new String();
						if(dataString.toString().endsWith(EOM)) {
							finalDataString = dataString.substring(0,dataString.length()-5); 
						}
						response = ServerUtil.parseAndReturnResponse(finalDataString.toString());
						break;
					}
				}
			}

		}
		response += EOM;
		List<String> dataChunks = new ArrayList<>();
		for (int i = 0; i < response.length(); i += 1013) {
			dataChunks.add(response.substring(i, Math.min(i + 1013, response.length())));
		}
		sendUdpMessages(dataChunks, conn, serverAddress, routerAddress, null, packet);


		return null;
	}


	public static String parseAndReturnResponse(String request) {
		// TODO Auto-generated method stub
		/*
		 * GET /hello.txt? HTTP/1.1 Host: localhost User-Agent: Concordia-HTTP/1.0
		 * Accept: application/xml
		 */
		String[] requestArr = request.split("\r\n\r\n");
		String[] headers = requestArr[0].split("\r\n");

		// GET /hello.txt? HTTP/1.1
		String requestTypeAndUrl = headers[0];
		String[] requestTypeAndUrlArr = requestTypeAndUrl.split(" ");
		String requestType = requestTypeAndUrlArr[0];
		parseUrl(requestTypeAndUrlArr[1]);

		if (requestArr.length > 1) {
			HttpRequestHandler.body = requestArr[1];
		}

		for (String header : headers) {
			if (header.contains("application/json")) {
				HttpRequestHandler.format = "application/json";
				break;
			} else if (header.contains("text/html")) {
				HttpRequestHandler.format = "text/html";
				break;
			} else if (header.contains("application/xml")) {
				HttpRequestHandler.format = "application/xml";
				break;
			} else {
				HttpRequestHandler.format = "text/plain";
			}
		}

		if (requestType.equals("GET")) {
			if (HttpServer.verbose)
				System.out.println("Request-Type: GET");
			HttpRequestHandler.isGet = true;
			HttpRequestHandler.isPost = false;
			return HttpRequestHandler.getHandler();
		} else if (requestType.equals("POST")) {
			if (HttpServer.verbose)
				System.out.println("Request-Type: POST");
			HttpRequestHandler.isPost = true;
			HttpRequestHandler.isGet = false;
			return HttpRequestHandler.postHandler();
		}

		return "Unknown HTTP Method Specified";
	}

	private static void parseUrl(String urlString) {
		if (HttpServer.verbose)
			System.out.println("ServerUtil.parseUrl()");
		String[] urlArr = urlString.split("\\?");
		if (urlArr.length >= 1)
			HttpRequestHandler.path = urlArr[0];
		if (urlArr.length > 1)
			HttpRequestHandler.query = urlArr[1];
	}

	public static void getFiles(String directory, List<File> files, List<String> fileNames) {
		if (HttpServer.verbose)
			System.out.println("ServerUtil.getFiles()");

		File dir = new File(directory);

		File[] fileArr = dir.listFiles();
		if (fileArr != null) {
			files.addAll(List.of(fileArr));
			for (File f : fileArr) {
				fileNames.add(f.getName());
			}
		}

		if (fileArr != null && fileArr.length > 0) {
			for (File file : fileArr) {
				// Check if the file is a directory
				if (file.isDirectory()) {
					getFiles(file.getAbsolutePath(), files, fileNames);
				}
			}
		}
	}

	public static String toJSONString(List<String> fileNames) {
		if (HttpServer.verbose)
			System.out.println("ServerUtil.toJSONString()");

		StringBuilder b = new StringBuilder();
		b.append("[ \"files\":\"");
		for (String file : fileNames) {
			b.append(file);
			b.append("\n");
		}
		b.append("\"]");
		return b.toString();
	}

	public static String responseGenerator(int code, String body, Object contentDisposition) {
		if (HttpServer.verbose)
			System.out.println("ServerUtil.responseGenerator()");

		StringBuilder b = new StringBuilder();
		if (code == 200) {
			b.append("HTTP/1.1 200 OK\r\n");
		} else if (code == 201) {
			b.append("HTTP/1.1 201 Created\r\n");
		} else if (code == 204) {
			b.append("HTTP/1.1 204 No Content\r\n");
		} else if (code == 401) {
			b.append("HTTP/1.1 401 Unauthorized\r\n");
		} else if (code == 404) {
			b.append("HTTP/1.1 404 Not Found\r\n");
		}

		b.append("Date: ").append(LocalDateTime.now()).append("\r\n");
		if (contentDisposition != null) {
			b.append("Content-Type: text/plain\r\n");
			b.append(contentDisposition).append("\r\n");
		} else {
			b.append("Content-Type: text/html\r\n");
		}
		b.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
		b.append("Server: httpfs/1.0\r\n");

		if (code == 201) {
			b.append("Location: ").append(HttpRequestHandler.path).append("\r\n");
		}
		b.append("\r\n\r\n");
		b.append(body);

		return b.toString();
	}

	public static void createAndWriteToFile(String fileName, String fileBody, boolean append) {
		if (HttpServer.verbose)
			System.out.println("ServerUtil.createAndWriteToFile()");

		try {
			Path filePath = Paths.get(httpfs.directory + "/" + fileName);
			Path file;
			if (!Files.exists(filePath)) {
				file = Files.createFile(filePath);
			} else {
				file = filePath;
			}
			System.out.println("Append: "+ append);
			FileWriter fileWriter = new FileWriter(file.toString(), append);
			fileWriter.write(fileBody);
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	static boolean checkPermission(String filePath, boolean isPost) {
		if (HttpServer.verbose)
			System.out.println("ServerUtil.checkPermission()");

		Path p = Paths.get(filePath);
		if (isPost) {
			if (Files.isWritable(p))
				return true;
		} else {
			if (Files.isReadable(p))
				return true;
		}

		return false;
	}

}
