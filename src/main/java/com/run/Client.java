package com.run;

import com.controller.LoginController;

public class Client {

	private static String VERSION = "1.1.0";

	public static void main(String[] args) {
		System.out.println("BChat v" + VERSION);
		new LoginController();
	}
}