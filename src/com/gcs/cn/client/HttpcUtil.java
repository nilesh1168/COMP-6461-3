package com.gcs.cn.client;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HttpcUtil {
	public static final String HELPTEXT = "httpc is a curl-like application but supports HTTP protocol only.\n"
			+ "    \n" + "    Usage:\n" + "        httpc command [arguments]\n" + "    \n" + "    The commands are:\n"
			+ "        get     executes a HTTP GET request and prints the response.\n"
			+ "        post    executes a HTTP POST request and prints the response.\n"
			+ "        help    prints this screen.\n" + "    \n"
			+ "    Use \"httpc help [command]\" for more information about a command.";

	public static final String GETINFO = "usage:" + "  httpc get [-v] [-h key:value] URL\n" + "    \n"
			+ "Get executes a HTTP GET request for a given URL.\n" + "    \n"
			+ "        -v              Prints the detail of the response such as protocol, status and headers.\n"
			+ "        -h key:value    Associates headers to HTTP Request with the format 'key:value'.";

	public static final String POSTINFO = "usage:" + " httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL\n"
			+ "    \n" + "Post executes a HTTP POST request for a given URL with inline data or from file.\n" + "    \n"
			+ "        -v              Prints the detail of the response such as protocol, status and headers.\n"
			+ "        -h key:value    Associates headers to HTTP Request with the format 'key:value'.\n"
			+ "        -d string       Associates an inline data to the body HTTP POST request.\n"
			+ "        -f file         Associates the content of a file to the body HTTP POST request.\n" + "    \n"
			+ "Either [-d] or [-f] can be used but not both.";

	public static void displayHelpInfo() {
		System.out.println(HttpcUtil.HELPTEXT);

	}

	public static void displayGetHelpInfo() {
		System.out.println(HttpcUtil.GETINFO);

	}

	public static void displayPostHelpInfo() {
		System.out.println(HttpcUtil.POSTINFO);

	}

	public static void parseGET(List<String> subList) {
		ArrayList<String> args = new ArrayList<>(subList);
		String[] headers = null;
		boolean verbose = false;
		String outputFile = null;
		boolean isRedirect = false;
		
		if (args.contains("-v")) {
			args.remove("-v");
			verbose = true;
		}
		if (args.contains("-h")) {
			int index = args.indexOf("-h");
			args.remove("-h");
			String headerLine = args.get(index);
			args.remove(headerLine);
			headers = headerLine.split(",");
		}
		if(args.contains("-o")){
            int index = args.indexOf("-o");
            args.remove("-o");
            outputFile = args.get(index);
            args.remove(outputFile);
        }
		 if(args.contains("-r")){
	            args.remove("-r");
	            isRedirect = true;
	        }
		
		if(args.size() >= 1) {
			HttpClient client = new HttpClient(args.get(0), headers, verbose, outputFile, isRedirect);
			client.get();
		}else {
			System.out.println("Something went wrong, with input. Please check and try again");
		}
	}

	public static boolean isRedirect(String[] response) {
        String[] headers = response[0].split("\r\n");
        String responseCode = headers[0].split("\\s+")[1];
        return responseCode.startsWith("3");
    }
	
	public static void saveResponseIntoOutputFile(String line, String outputFile) {
		try {
			Files.createDirectories(Paths.get("Output"));
			Path filePath = Paths.get("Output" + "/" + outputFile);
			Path file;
			if (!Files.exists(filePath)) {
				file = Files.createFile(filePath);
			} else {
				file = filePath;
			}
			FileWriter fileWriter = new FileWriter(file.toString());
			fileWriter.write(line);
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	public static void parsePOST(List<String> subList) {
		ArrayList<String> args = new ArrayList<>(subList);
        String file = null;
        String postBody = null;
        String[] headers = null;
        boolean verbose = false;
        String outputFile = null;
        boolean redirectionAllowed = false;

        if(args.contains("-d") && args.contains("-f")){
            System.out.println("You can use either [-d] or [-f] but not both");
        }else {
            if(args.contains("-v")){
                args.remove("-v");
                verbose = true;
            }
            if(args.contains("-h")){
                int index = args.indexOf("-h");
                args.remove("-h");
                String headerLine = args.get(index);
                args.remove(headerLine);
                headers = headerLine.split(",");
            }
            if(args.contains("-r")){
                args.remove("-r");
                redirectionAllowed = true;
            }
            if(args.contains("-d")){
                int index = args.indexOf("-d");
                args.remove("-d");
                postBody = args.get(index);
                args.remove(postBody);
            }
            if(args.contains("-f")){
                int index = args.indexOf("-f");
                args.remove("-f");
                file = args.get(index);
                args.remove(file);
            }
            if(args.contains("-o")){
                int index = args.indexOf("-o");
                args.remove("-o");
                outputFile = args.get(index);
                args.remove(outputFile);
            }

            if(args.size() >= 1){
				HttpClient client = new HttpClient(args.get(0), headers, verbose, postBody, outputFile, redirectionAllowed, file);
                client.post();
            }else {
                System.out.println("Something went wrong, with input. Please check and try again");
            }
        }
	}

}
