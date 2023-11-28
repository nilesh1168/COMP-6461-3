package com.gcs.cn.client;

import java.util.Arrays;
import java.util.List;

public class httpc {

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("help")) {
			HttpcUtil.displayHelpInfo();
		} else if (args.length == 2 && args[0].equals("help") && args[1].equals("get")) {
			HttpcUtil.displayGetHelpInfo();
		} else if (args.length == 2 && args[0].equals("help") && args[1].equals("post")) {
			HttpcUtil.displayPostHelpInfo();
		} else if (args.length > 1 && args[0].equals("get")) {
			List<String> argsL = Arrays.asList(args);
			HttpcUtil.parseGET(argsL.subList(1, argsL.size()));
		} else if (args.length > 1 && args[0].equals("post")) {
			List<String> argsL = Arrays.asList(args);
			HttpcUtil.parsePOST(argsL.subList(1, argsL.size()));
		}
	}
}
