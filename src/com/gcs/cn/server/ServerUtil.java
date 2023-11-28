package com.gcs.cn.server;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.gcs.cn.packet.Packet;

public class ServerUtil {

	public static void displayHelpInfo() {
		String helpInfo = "httpfs is a simple file server.\n" +
                "\n" +
                "    Usage:\n" +
                "        httpfs [-v] [-p PORT] [-d PATH-TO-DIR]\n" +
                "\n" +
                "    The commands are:\n" +
                "        -v      Prints debugging messages.\n" +
                "        -p      Specifies the port number that the server will listen and serve at. (Default is 8080)\n" +
                "        -d      Specifies the directory that the server will use to read/write requested files. Default is the current directory when launching the application.";


        System.out.println(helpInfo);
		
	}

	public static HttpServer optionsParser(List<String> subList) {
		ArrayList<String> args = new ArrayList<>(subList);
		boolean verbose = false;
		int port = 0;
		String directory = "";
		if(args.contains("-v")){
            args.remove("-v");
            verbose = true;
        }
        if(args.contains("-p")){
            int index = args.indexOf("-p");
            args.remove("-p");
            String portString = args.get(index);
            port = Integer.parseInt(portString);
            args.remove(portString);
        }
        if(args.contains("-d")){
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
	
	
	public static Packet getRequest(DatagramChannel channel) throws IOException {
		if(HttpServer.verbose) {
			System.out.println("Verbose: True");
			System.out.println("Working directory: "+httpfs.directory);
		}
		
		ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);
		SocketAddress router = channel.receive(buf);
		String requestPayload = "";
		if (router != null) {
			buf.flip();
			Packet packet = Packet.fromBuffer(buf);
			buf.flip();
			requestPayload = new String(packet.getPayload(), UTF_8);
			String response = ServerUtil.parseAndReturnResponse(requestPayload);
			Packet resp = packet.toBuilder().setPayload(response.getBytes()).create();
			return resp;
		}		
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
	

	public static String parseAndReturnResponse(String request) {
		// TODO Auto-generated method stub
		/*
	        * GET /hello.txt? HTTP/1.1
	        Host: localhost
	        User-Agent: Concordia-HTTP/1.0
	        Accept: application/xml
	        * */
	        String[] requestArr = request.split("\r\n\r\n");
	        String[] headers = requestArr[0].split("\r\n");
	        

	        //GET /hello.txt? HTTP/1.1
	        String requestTypeAndUrl = headers[0];
	        String[] requestTypeAndUrlArr = requestTypeAndUrl.split(" ");
	        String requestType = requestTypeAndUrlArr[0];
	        parseUrl(requestTypeAndUrlArr[1]);

	        if(requestArr.length > 1){
	            HttpRequestHandler.body = requestArr[1];
	        }


	        for (String header: headers) {
	            if(header.contains("application/json")){
	            	HttpRequestHandler.format = "application/json";
	                break;
	            }else if(header.contains("text/html")){
	            	HttpRequestHandler.format = "text/html";
	                break;
	            }else if(header.contains("application/xml")){
	            	HttpRequestHandler.format = "application/xml";
	                break;
	            }else {
	            	HttpRequestHandler.format = "text/plain";
	            }
	        }

	        if(requestType.equals("GET")){
	        	if(HttpServer.verbose)
	        		System.out.println("Request-Type: GET");
	        	HttpRequestHandler.isGet = true;
	        	HttpRequestHandler.isPost = false;
	            return HttpRequestHandler.getHandler();
	        }else if(requestType.equals("POST")){
	        	if(HttpServer.verbose)
	        		System.out.println("Request-Type: POST");
	        	HttpRequestHandler.isPost = true;
	        	HttpRequestHandler.isGet = false;
	            return HttpRequestHandler.postHandler();
	        }

	        return "Unknown HTTP Method Specified";
	}
	

	private static void parseUrl(String urlString) {
		if(HttpServer.verbose)
			System.out.println("ServerUtil.parseUrl()");
		String[] urlArr = urlString.split("\\?");
        if(urlArr.length >=  1)
        	HttpRequestHandler.path = urlArr[0];
        if(urlArr.length > 1)
        	HttpRequestHandler.query = urlArr[1];
	}

	public static void getFiles(String directory, List<File> files, List<String> fileNames) {
		if(HttpServer.verbose)
			System.out.println("ServerUtil.getFiles()");

		File dir = new File(directory);
		
        File[] fileArr= dir.listFiles();
        if(fileArr != null){
            files.addAll(List.of(fileArr));
            for (File f :fileArr) {
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
		if(HttpServer.verbose)
			System.out.println("ServerUtil.toJSONString()");
		
		StringBuilder b = new StringBuilder();
        b.append("[ \"files\":\"");
        for (String file: fileNames) {
            b.append(file);
            b.append("\n");
        }
        b.append("\"]");
        return b.toString();
	}

	public static String responseGenerator(int code, String body, Object contentDisposition) {
		if(HttpServer.verbose)
			System.out.println("ServerUtil.responseGenerator()");
		
		StringBuilder b = new StringBuilder();
        if(code == 200){
            b.append("HTTP/1.1 200 OK\r\n");
        }else if(code == 201){
            b.append("HTTP/1.1 201 Created\r\n");
        }else if (code == 204){
            b.append("HTTP/1.1 204 No Content\r\n");
        } else if (code == 401) {
            b.append("HTTP/1.1 401 Unauthorized\r\n");
        } else if (code == 404) {
            b.append("HTTP/1.1 404 Not Found\r\n");
        }

        b.append("Date: ").append(LocalDateTime.now()).append("\r\n");
        if(contentDisposition != null){
            b.append("Content-Type: text/plain\r\n");
            b.append(contentDisposition).append("\r\n");
        }else{
            b.append("Content-Type: text/html\r\n");
        }
        b.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
        b.append("Server: httpfs/1.0\r\n");


        if(code == 201){
            b.append("Location: ").append(HttpRequestHandler.path).append("\r\n");
        }
        b.append("\r\n\r\n");
        b.append(body);

        return b.toString();
	}

	public static void createAndWriteToFile(String fileName, String fileBody, boolean append) {
		if(HttpServer.verbose)
			System.out.println("ServerUtil.createAndWriteToFile()");
		
		try {
            Path filePath = Paths.get(httpfs.directory + "/" + fileName);
            Path file;
            if (!Files.exists(filePath)) {
                file = Files.createFile(filePath);
            } else {
                file = filePath;
            }
            FileWriter fileWriter = new FileWriter(file.toString(), append);
            fileWriter.write(fileBody);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
		
	}
	
	
	static boolean checkPermission(String filePath, boolean isPost) {
		if(HttpServer.verbose)
			System.out.println("ServerUtil.checkPermission()");
		
		Path p = Paths.get(filePath);
		if (isPost) {			
			if (Files.isWritable(p))
				return true;
		}
		else{			
			if (Files.isReadable(p))
				return true;
		}

		return false;
	}
		
}
