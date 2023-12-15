package com.gcs.cn.server;

import static java.nio.channels.SelectionKey.OP_READ;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import javax.swing.plaf.synth.SynthScrollPaneUI;

import com.gcs.cn.packet.Packet;
import com.gcs.cn.packet.Packet.Builder;

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

//	public static String getRequest(SocketChannel connection) throws IOException {
//		if(HttpServer.verbose) {
//			System.out.println("Verbose: True");
//			System.out.println("Working directory: "+httpfs.directory);
//		}
//		
//		StringBuilder b = new StringBuilder();
//        Charset utf8 = StandardCharsets.UTF_8;
//        ByteBuffer buf = ByteBuffer.allocate(1024);
//        int count = 0;
//        for (; ; ) {
//            if(count > 0)
//                break;
//            int nr = connection.read(buf);
//            if (nr == -1)
//                break;
//
//            if (nr > 0) {
//                // ByteBuffer is tricky, you have to flip when switch from read to write, or vice-versa
//                buf.flip();
//                b.append(utf8.decode(buf));
//                count++;
//                buf.clear();
//            }
//        }
//        return b.toString();
//	}

	private static void sendUdpMessages(List<String> dataChunks, DatagramSocket conn, InetSocketAddress serverAddress,
			SocketAddress routerAddress, int[] arr, Packet pkt) throws IOException {
		int[] windows = { 0, 0, 0, 0, 0 };

		for (int seqNum = base; seqNum < Math.min(base + windowSize, dataChunks.size()); seqNum++) {
			if (arr != null && arr[seqNum] == 1) {
				windows[seqNum] = 1;
				continue;
			}

			String chunk = dataChunks.get(seqNum);
//			System.out.println("Sending from Server: chunk");
			
			Packet p = pkt.toBuilder().setType(0).setSequenceNumber(0).setPayload(chunk.getBytes()).create();
//			System.out.println("Sending packet data as: " + p.getPayload().toString());

			byte[] packetBytes = p.toBytes();
			DatagramPacket packet = new DatagramPacket(packetBytes, packetBytes.length, routerAddress);

			try {
				conn.send(packet);
//				conn.send(p.toBuffer(), routerAddress);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

//        socket.setSoTimeout(timeout);
//		channel.configureBlocking(false);
//		Selector selector = Selector.open();
//		channel.register(selector, OP_READ);
//		selector.select(50000);
//		Set<SelectionKey> keys = selector.selectedKeys();
//		if (keys.isEmpty()) {
//			System.out
//					.println("[CLIENT] - No response after " + timeout + " for Packet " + windows[windows.length - 1]);
//			sendUdpMessages(dataChunks, channel, serverAddress, routerAddress, windows);
//		}
		try(DatagramSocket receiveSocket = new DatagramSocket()) {
			while (true) {
				byte[] receiveData = new byte[1024];

				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
//				System.out.println("server Waiting to receive:");
				receiveSocket.setSoTimeout(timeout);
				receiveSocket.receive(receivePacket);
				byte[] data = new byte[receivePacket.getLength()];
				System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, receivePacket.getLength());

//				ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);
//				SocketAddress router = channel.receive(buf);

				String requestPayload = "";
//					buf.flip();
				Packet packet = Packet.fromBytes(data);
//					buf.flip();
				requestPayload = new String(packet.getPayload(), UTF_8);
//				System.out.println("Received payload from client: "+ requestPayload.length());

				if (packet.getType() == 1) {
					
//		        	response_to_return_2 = parse_request(response_to_return.encode())

				packet = packet.toBuilder().setType(2).setPayload("SYN Recieved!".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);
//		        			p.payload = ("SYN Recieved!").encode()
//		        			print("SYN Recieved!")
//		        			conn.sendto(p.to_bytes(), sender)
			}
				if (packet.getType() == 3) {					

//			        	response_to_return_2 = parse_request(response_to_return.encode())
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

//						System.out.println("inside p.getType == 3");
//						Packet p = packet.toBuilder().setType(3).setPayload("ACK Recieved!".getBytes()).create();
//						channel.send(p.toBuffer(), router);
//			        			p.payload = ("SYN Recieved!").encode()
//			        			print("SYN Recieved!")
//			        			conn.sendto(p.to_bytes(), sender)
				} else if (packet.getType() == 0) { // is a data packet

					int currSeqNum = (int) packet.getSequenceNumber();
					String payLoad = new String(packet.getPayload(), UTF_8);
//					System.out.println("Payload: " + payLoad);

					dataReceived[currSeqNum] = new StringBuffer();
					dataReceived[currSeqNum].append(payLoad);

//					System.out.println("Sending Ack for Payload from client");

					packet = packet.toBuilder().setType((byte) 3).setPayload("Sending ack for payload".getBytes())
							.create();
					byte[] responseBytes = packet.toBytes();

					DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length,routerAddress);
					conn.send(responsePacket);

					StringBuilder dataString = new StringBuilder();
					boolean allDataReceived = true;

					if (dataReceived[currSeqNum].toString().endsWith(EOM)) {
//						System.out.println("EOM reached sendUDPmsgs");
						for (int i = 0; i < currSeqNum; i++) {
//							System.out.println("inside EOM for sendUDPmsgs");
							if (dataReceived[i] == null) {
//								System.out.println("inside EOM datarcv sendUDPmsgs");
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

//	                    p.setType((byte) 3);
//						p.toBuilder().setType((byte) 3).create();
//						byte[] responseBytes = p.toBytes();

//						DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length,
//								InetAddress.getByName(routerHost), routerPort);
//						socket.send(responsePacket);

//						channel.send(p.toBuffer(), routerAddress);

//						boolean allDataReceived = true;
//						
//						
//						
//						
//						
//						
//						for (int i = 1; i <= dataReceived.length; i++) {
//							if (dataReceived[i] == null) {
//								allDataReceived = false;
//								break;
//							} else {
//								dataString.append(dataReceived[currSeqNum]);
//								System.out.println(dataString);
//								if (allDataReceived && dataReceived[i].toString().equals(EOM)) {
//									System.out.println(dataString);
//									String response = ServerUtil.parseAndReturnResponse(dataString.toString());
//									response+=EOM;
//									packet.toBuilder().setType((byte) 0).setPayload(response.getBytes()).setSequenceNumber(currSeqNum+1).create();
//									channel.send(packet.toBuffer(), routerAddress);
//								}
//							}
//						}

//						while(requestPayload.endsWith(EOM)) {
//							String data = createDataString(requestPayload, channel, packet);
//						}

//						Packet resp = packet.toBuilder().setPayload(response.getBytes()).create();
//						return resp;
//						String response = ServerUtil.parseAndReturnResponse(requestPayload);
//						List<String> dataChunks = new ArrayList<>();
//						for (int i = 0; i < response.length(); i += 1013) {
//							dataChunks.add(response.substring(i, Math.min(i + 1013, response.length())));
//						}

//						System.out.println(" Response of the GET: " + response);
//						response += EOM;
				}

//                InetAddress senderAddress = receivePacket.getAddress();
//                int senderPort = receivePacket.getPort();
//                Packet p = Packet.fromBytes(receivePacket.getData());

//				System.out.println("Sender: " + senderAddress + ":" + senderPort);
//				System.out.println("Router: " + senderAddress);
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
//			ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);

			byte[] receiveData = new byte[1024];

			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
//			conn.setSoTimeout(timeout);
			conn.receive(receivePacket);
			byte[] data = new byte[receivePacket.getLength()];
			System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, receivePacket.getLength());
			
			
//			System.out.println("Datagram packet length : "+receivePacket.getLength());
//			System.out.println("packet size 84: "+ data.length);
			packet = Packet.fromBytes(data);

//			String requestPayload = "";
//				buf.flip();
//				buf.flip();
//			requestPayload = new String(packet.getPayload(), UTF_8);
			int packetType = packet.getType();
			if (packetType == 1) {
				
//		        	response_to_return_2 = parse_request(response_to_return.encode())

				packet = packet.toBuilder().setType(2).setPayload("SYN Recieved!".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);
//		        			p.payload = ("SYN Recieved!").encode()
//		        			print("SYN Recieved!")
//		        			conn.sendto(p.to_bytes(), sender)
			} else if (packetType == 3) {
//		        	response_to_return_2 = parse_request(response_to_return.encode())
//				System.out.println("inside p.getType == 3");
				packet = packet.toBuilder().setType(3).setPayload("ACK Recieved!".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);
//		        			p.payload = ("SYN Recieved!").encode()
//		        			print("SYN Recieved!")
//		        			conn.sendto(p.to_bytes(), sender)
			} else {
//				System.out.println("Payload byte len: "+packet.getPayload().length);
				String payLoad = new String(packet.getPayload(), UTF_8);
//				System.out.println("Payload Length 73: " + payLoad.length());

				packet = packet.toBuilder().setType((byte) 3).setPayload("Sending ack for payload".getBytes()).create();
				byte[] responseBytes = packet.toBytes();

				DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, routerAddress);
				conn.send(responsePacket);

				int currSeqNum = (int) packet.getSequenceNumber();
				dataReceived[currSeqNum] = new StringBuffer();
				dataReceived[currSeqNum].append(payLoad);

				StringBuilder dataString = new StringBuilder();
//				System.out.println("Data for payload GET : " +dataReceived[currSeqNum].toString());
//				System.out.println("Does datda end with $: "+dataReceived[currSeqNum].toString().endsWith(EOM));
				if (dataReceived[currSeqNum].toString().endsWith(EOM)) {
//					System.out.println("EOM reached getRequest");
					boolean allDataReceived = true;
					for (int i = 0; i < currSeqNum; i++) {
//						System.out.println("inside for EOM getRequest");
						if (dataReceived[i] == null) {
//							System.out.println("inside dataRec getRequest");
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
//						System.out.println("Final datastring :"+finalDataString);
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

//		StringBuilder b = new StringBuilder();
//        Charset utf8 = StandardCharsets.UTF_8;
//        ByteBuffer buf = ByteBuffer.allocate(1024);
//        int count = 0;
//        for (; ; ) {
//            if(count > 0)
//                break;
//            int nr = channel.read(buf);
////            int nr = connection.read(buf);
//            if (nr == -1)
//                break;
//
//            if (nr > 0) {
//                // ByteBuffer is tricky, you have to flip when switch from read to write, or vice-versa
//                buf.flip();
//                b.append(utf8.decode(buf));
//                count++;
//                buf.clear();
//            }
//        }

		return null;
	}

//	private static String createDataString(String requestPayload, DatagramChannel channel, Packet packet) {
//		StringBuilder strBuilder  = new StringBuilder();
//		strBuilder.append(requestPayload);
//		
//		Packet p = packet.toBuilder().setType(3).setPayload(String.valueOf("ACK for sequence num: "+ packet.getSequenceNumber()+" ").getBytes()).create();
//		windowSize[]
//		return strBuilder.toString();
//	}

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
