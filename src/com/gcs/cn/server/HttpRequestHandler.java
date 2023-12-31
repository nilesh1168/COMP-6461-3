package com.gcs.cn.server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class HttpRequestHandler implements Runnable {

	private DatagramSocket conn;
	private static Object lock;
	static String format = "text/plain";
	static String body;
	static String path = "/";
	public static String query;
	static boolean isGet = false;
	static boolean isPost = false;
	
	public HttpRequestHandler(DatagramSocket conn, Object lock) {
		this.conn = conn;
		HttpRequestHandler.lock = lock;
	}

	@Override
	public void run() {
//		System.out.println("--------------------------------");
//		//			System.out.println("New client from" + connection.getLocalAddress());
//		System.out.println("New client from" + conn.getLocalAddress());
//		System.out.println("--------------------------------");

		try {
//			while(true) {				
//			String request = ServerUtil.getRequest(connection);
			
				ServerUtil.getRequest(conn);
				
				
				System.out.println("Request Completed");
//			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			conn.close();
		}
	}

	public static String getHandler() {
		// TODO Auto-generated method stub
		List<File> files = new ArrayList<>();
		List<String> fileNames = new ArrayList<>();
		ServerUtil.getFiles(httpfs.directory, files, fileNames);

		body = "";

		if (!path.isEmpty() && path.equals("/")) {
			switch (format) {
			case "application/json":
				body = ServerUtil.toJSONString(fileNames);
				break;
			case "application/xml": {
				StringBuilder b = new StringBuilder();
				b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
				b.append("<files>");
				for (String file : fileNames) {
					b.append("<file>").append(file).append("</file");
				}
				b.append("</files>");
				body = b.toString();
				break;
			}
			case "text/html": {
				StringBuilder b = new StringBuilder();
				b.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Files on Server</title>")
						.append("</head><body><h2>File List on Server</h2><ul>");
				for (String file : fileNames) {
					b.append("<li>").append(file).append("</li>");
				}
				b.append("<ul></body></html>");
				body = b.toString();
				break;
			}
			default:
				body = String.join("\n", fileNames);
				break;
			}
			return ServerUtil.responseGenerator(200, body, null);
		} else if (path.split("/").length >= 3) {
			body = "401 Unauthorized.\n" + "The requested URL " + path + " cannot be accessed.\n"
					+ "The requested file is located outside the working directory.";
			return ServerUtil.responseGenerator(401, body, null);
		}
		else {
			String fileName = path.split("/")[1];
			String contentDisposition = null;
			if (fileNames.contains(fileName) || fileNames.contains(fileName + ".txt")) {
				if(!ServerUtil.checkPermission(httpfs.directory + path, isPost)) {
					body = "401 Unauthorized.\n" + "The requested URL " + path + " cannot be accessed.\n"
							+ "The requested file does not have sufficient permissions.";
					return ServerUtil.responseGenerator(401, body, null);
				}
				if (!fileName.contains(".txt")) {
					fileName = fileName + ".txt";
				}
				Path filePath = Path.of(httpfs.directory + "/" + fileName);
				try {
					synchronized (lock) {
						// instead of file content save it back to file
						body += Files.readString(filePath);
					}

					contentDisposition = "Content-Disposition: attachment; filename=" + fileName;

				} catch (IOException e) {
					e.printStackTrace();
				}
				if(body.isEmpty()) {
					body = "No content in file";
					return ServerUtil.responseGenerator(204, body, contentDisposition);
				}
				return ServerUtil.responseGenerator(200, body, contentDisposition);
			} else {
				body = "404. There is an error.\n";
				body += "The requested URL " + path + " was not found on this server.\n";
				body += "That is all we know.";
				return ServerUtil.responseGenerator(404, body, null);
			}

		}
	}


	public static String postHandler() {
		if(HttpServer.verbose)
			System.out.println("HttpRequestHandler.postHandler()");
		
		List<File> files = new ArrayList<>();
		List<String> fileNames = new ArrayList<>();
		ServerUtil.getFiles(httpfs.directory, files, fileNames);

		if (!path.isEmpty() && path.equals("/")) {
			body = "404 Error.\n" + "We cannot create a file without a name.\n";
			return ServerUtil.responseGenerator(404, body, null);
		} else if (path.split("/").length >= 3) {
			body = "401 Unauthorized.\n" + "The requested URL " + path + " cannot be accessed.\n"
					+ "The requested file is located outside the working directory.";
			return ServerUtil.responseGenerator(401, body, null);
		}
		else {
			String fileName = path.split("/")[1];

			// File exists
			if (fileNames.contains(fileName)) {
				if(HttpServer.verbose)
					System.out.println("File already exists. Performing operations...");
				if(!ServerUtil.checkPermission(httpfs.directory + path, isPost)) {
					body = "401 Unauthorized.\n" + "The requested URL " + path + " cannot be accessed.\n"
							+ "The requested file does not have sufficient permissions.";
					return ServerUtil.responseGenerator(401, body, null);
				}
				if (query != null && query.contains("overwrite=true")) {
					synchronized (lock) {
						if(body!=null) {							
							ServerUtil.createAndWriteToFile(fileName, body, false);
							body = "File has been successfully overwritten";
							return ServerUtil.responseGenerator(204, body, null);
						}
						else {
							body = "No data to write in the file";
							return ServerUtil.responseGenerator(204, body, null);
						}
					}
				} else {
					synchronized (lock) {
						if(body!=null) {							
							ServerUtil.createAndWriteToFile(fileName, body, true);
							body = "File has been successfully updated";
							return ServerUtil.responseGenerator(204, body, null);
						}
						else {
							body = "No data to write in the file";
							return ServerUtil.responseGenerator(204, body, null);
						}
					}
				}
			} else { // File Doesn't already exist
				synchronized (lock) {
					if(HttpServer.verbose)
						System.out.println("File does not exist. Creating new file...");
					ServerUtil.createAndWriteToFile(fileName, body, false);
					body = "File has been successfully created";
					return ServerUtil.responseGenerator(201, body, null);
				}
			}

		}
	}

}
