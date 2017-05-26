package com.chat.herechat.ServiceHandlers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.chat.herechat.Utilities.Constants;

/**
 * Opens a socket to a peer and sends a single string. Used to send control messages and user text messages
 */
public class SendControlMessage extends Thread {
	private Socket mSocket;
	private Handler mHandler;
	private String mMsg;
	private String mPeerIP;
	private String mRoomUniqueID;
	private static final int SOCKET_PORT = 4000;
	
	public SendControlMessage(Handler h, String peerIP, String msg, String RoomUniqueID) {
		mHandler = h;
		mPeerIP = peerIP;
		mMsg = msg;
		mRoomUniqueID=RoomUniqueID;
	}
	
	public SendControlMessage(String peerIP, String msg) {
		this(null,peerIP,msg,null);
	}

	@Override
	public void run() {
		PrintWriter mOut=null;
		try {
		    /**
		     * Create a client socket with the host,
		     * port, and timeout information.
		     */
			mSocket = new Socket();
			mSocket.bind(null);
		    mSocket.connect((new InetSocketAddress(mPeerIP, SOCKET_PORT)), 3000);
		    mOut =  new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream())), true);
		} catch (IOException e) {
			SendMessageViaHandler(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_FAILED);
			e.printStackTrace();
			return;
		}
			
		mOut.println(mMsg); //send via socket
		mOut.flush();
		mOut.close();
		
		if (mHandler!=null) //if we have a handler to return the result to
			SendMessageViaHandler(Constants.SINGLE_SEND_THREAD_ACTION_RESULT_SUCCESS); //notify that the send was successful
	}
	
	private void SendMessageViaHandler(String result) {
		if (mHandler!=null) {
			Message msg = mHandler.obtainMessage();
			Bundle data = new Bundle();
			data.putString(Constants.SINGLE_SEND_THREAD_KEY_UNIQUE_ROOM_ID, mRoomUniqueID);  //set the room's ID
			data.putString(Constants.SINGLE_SEND_THREAD_KEY_RESULT, result);  //put the result in the data
			msg.setData(data);
			mHandler.sendMessage(msg);
		}
	}
}
