package com.controller;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Base64;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import com.model.ResponseModel;
import com.model.ConnectionModel;
import com.view.ChatView;

public class ChatController {

	private static String HANDSHAKE = "HANDSHAKE;";
	private static String AUDIO_PATH = "/ressources/audio/";
	private static String NEW_MESSAGE_AUDIO_FILE = "new_message_sound.wav";
	private static String USERNAME_PARAMETER = "username=";
	private static String MESSAGE_PARAMETER = "message=";
	private static String ID_PARAMETER = "uuid=";
	private static String COLOR_PARAMETER = "color=";

	private PrintWriter outputStream;
	private BufferedReader inputStream;
	private Socket clientSocket;
	private ConnectionModel settingsModel;
	private ChatView chatView;
	private int id;
	private Clip newMessageSound;
	private AudioInputStream audioInputStream;

	private boolean running = true;

	public ChatController(ConnectionModel settingsModel, Socket clientSocket, BufferedReader inputStream,
			PrintWriter outputStream) {
		// Setup
		this.clientSocket = clientSocket;
		this.inputStream = inputStream;
		this.outputStream = outputStream;
		this.settingsModel = settingsModel;
		try {
			newMessageSound = AudioSystem.getClip();
			InputStream audioSource = getClass().getResourceAsStream(AUDIO_PATH + NEW_MESSAGE_AUDIO_FILE);
			InputStream bufferedInputStream = new BufferedInputStream(audioSource);
			audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream);
			newMessageSound.open(audioInputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
		connect();
	}

	public void connect() {

		chatView = new ChatView();
		chatView.addSendButtonListener(new SendButtonListener());
		chatView.addEnterKeyListener(new EnterListener());
		chatView.addExitButtonListener(new ExitButtonListener());
		
		// Handshake
		outputStream.println(HANDSHAKE +"username="+settingsModel.getUsername()+";"+"color="+settingsModel.getColorAsString()+";");
		new WaitForInputThread().start();
	}

	public void disconnect() {
		outputStream.println("/disconnect");
		running = false;
		outputStream.close();
		try {
			clientSocket.close();
		} catch (IOException e) {
			System.out.println("Error while closing the connection.");
			e.printStackTrace();
		}
		chatView.dispatchEvent(new WindowEvent(chatView, WindowEvent.WINDOW_CLOSING));
	}

	public void sendMessageFromInput() {
		String inputText = chatView.getInputText();
		switch (inputText) {
		case "/exit":
			disconnect();
			break;
		case "":
			break;
		default:
			outputStream.println(inputText);
			break;
		}

	}

	private void playNewMessageSound() {
		new Thread(new NewMessageSound()).start();
	}

	class NewMessageSound implements Runnable {

		public void run() {
			if (newMessageSound.isRunning()) {
				newMessageSound.stop();
				newMessageSound.setFramePosition(0);
			} else if (!newMessageSound.isRunning()) {
				newMessageSound.setFramePosition(0);
			}

			newMessageSound.start();
		}
	}

	class WaitForInputThread extends Thread {

		public WaitForInputThread() {
			super();
		}

		public void run() {
			String line;
			
			while(true) {
				try {
					line = inputStream.readLine();
					if(line.startsWith(HANDSHAKE)) {
						//Log the handshake
						System.out.println(line);
						int idIndex = line.indexOf(ID_PARAMETER)+ID_PARAMETER.length();
						int idCloserIndex = line.indexOf(";", idIndex);
						id = Integer.parseInt(line.substring(idIndex,idCloserIndex));
						break;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			while (running) {
				try {
					line = inputStream.readLine();
					System.out.println(line);
					if (line != null) {
						ResponseModel responseModel = new ResponseModel();
						
						if(line.contains("SERVERMESSAGE;")) {
							//line = line.substring(line.indexOf("SERVERMESSAGE;")+"SERVERMESSAGE;".length());
							responseModel.setIsSeverMessage(true);
						}

						//Retrieve username
						int usernameIndex = line.indexOf(USERNAME_PARAMETER)+USERNAME_PARAMETER.length();
						int usernameCloserIndex = line.indexOf(";", usernameIndex);
						responseModel.setUsername(line.substring(usernameIndex,usernameCloserIndex));
						
						//Retrieve username attribute set
						responseModel.setUsernameAttributeSet(null);
						
						//Retrieve message
						int messageIndex = line.indexOf(MESSAGE_PARAMETER)+MESSAGE_PARAMETER.length();
						int messageIndexCloserIndex = line.indexOf(";", messageIndex);
						responseModel.setMessage(line.substring(messageIndex, messageIndexCloserIndex));
						
						//Retrieve id
						int idIndex = line.indexOf(ID_PARAMETER)+ID_PARAMETER.length();
						int idIndexCloserIndex = line.indexOf(";", idIndex);
						responseModel.setId(Integer.parseInt(line.substring(idIndex, idIndexCloserIndex)));
						
						//Retrieve color
						int colorIndex = line.indexOf(COLOR_PARAMETER)+COLOR_PARAMETER.length();
						int colorCloserIndex = line.indexOf(";", colorIndex);
						String rgbColor = line.substring(colorIndex,colorCloserIndex);
						
						System.out.println("rgb color: "+rgbColor);
						
						//Get r
						int rIndex = rgbColor.indexOf("r=") + "r=".length();
						int rCloserIndex = rgbColor.indexOf(",", rIndex);
						int r = Integer.parseInt(rgbColor.substring(rIndex,rCloserIndex));
						
						//Get g
						int gIndex = rgbColor.indexOf("g=") + "g=".length();
						int gCloserIndex = rgbColor.indexOf(",", gIndex);
						int g = Integer.parseInt(rgbColor.substring(gIndex,gCloserIndex));
						
						//Get b
						int bIndex = rgbColor.indexOf("b=") + "b=".length();
						int bCloserIndex = rgbColor.indexOf("]", bIndex);
						int b = Integer.parseInt(rgbColor.substring(bIndex,bCloserIndex));
						responseModel.setColor(new Color(r,g,b));
						
						if (responseModel.getId() != id) {
							playNewMessageSound();
						}else {
							responseModel.setUsername("You");
						}
						chatView.addMessageToChat(responseModel);
					}
				} catch (SocketException e) {

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	class SendButtonListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			sendMessageFromInput();
		}
	}
	
	class ExitButtonListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			disconnect();
		}
	}

	class EnterListener implements KeyListener {
		@Override
		public void keyPressed(KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.VK_ENTER)
				sendMessageFromInput();
		}

		@Override
		public void keyReleased(KeyEvent e) {
			// TODO Auto-generated method stub

		}

		@Override
		public void keyTyped(KeyEvent e) {
		}

	}
}
