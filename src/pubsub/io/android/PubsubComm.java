package pubsub.io.android;

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing PubSub.io
 * connections. It has a thread for connecting with a hub, and a thread for
 * performing data transmissions when connected.
 * 
 * Based on the BluetoothChat example by Google.
 */
public class PubsubComm {

	private final static String TAG = "PubsubComm";

	/** Handler for communicating with the UI */
	private final Handler mHandler;

	/** Thread for establishing connection to PubSub.io */
	private ConnectThread mConnectThread;

	/** Thread for handling communication with PubSub.io */
	private ConnectedThread mConnectedThread;

	/** Current PubSub.io state */
	private int mState;

	// Connection status constants
	/** Default state */
	public static final int STATE_NONE = 0;
	/** Attempting to connect to PubSub.io */
	public static final int STATE_CONNECTING = 1;
	/** Connection to PubSub.io established */
	public static final int STATE_CONNECTED = 2;

	public PubsubComm(Context context, Handler handler) {
		mState = STATE_NONE;
		mHandler = handler;
	}

	/**
	 * Set the current state of the connection
	 * 
	 * @param state
	 *          An integer defining the current connection state
	 */
	private synchronized void setState(int state) {
		mState = state;

		// Send message about state change to UI
		mHandler.obtainMessage(Pubsub.STATE_CHANGE, state, -1).sendToTarget();
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
	}

	/**
	 * Start the chat service. Specifically start AcceptThread to begin a session
	 * in listening (server) mode. Called by the Activity onResume()
	 */
	public synchronized void start() {
		Log.d(TAG, "start");

		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param host
	 *          PubSub.io server host.
	 * @param port
	 *          PubSub.io server port.
	 */
	public synchronized void connect(String host, String port, String sub) {
		Log.d(TAG, "connect to: " + host + ":" + port);

		// Cancel any thread attempting to make a connection
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(host, port, sub);
		mConnectThread.start();

		setState(STATE_CONNECTING);
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 * 
	 * @param socket
	 *          The BluetoothSocket on which the connection was made
	 * @param device
	 *          The BluetoothDevice that has been connected
	 */
	public synchronized void connected(Socket socket, String sub) {
		Log.d(TAG, "connected");

		// Cancel the thread that completed the connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		// Send the name of the connected device back to the UI Activity
		Message msg = mHandler.obtainMessage(Pubsub.CONNECTED_TO_HOST);
		Bundle bundle = new Bundle();
		bundle.putString(Pubsub.HOST_NAME, socket.getInetAddress().getHostName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		// Notify the activity that subscribes are now safe!
		mHandler.obtainMessage(Pubsub.SUBSCRIBES).sendToTarget();

		setState(STATE_CONNECTED);

		// Subscribe to the defined sub
		try {
			write(PubsubParser.sub(sub).getBytes());
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		Log.d(TAG, "stop");

		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		setState(STATE_NONE);
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 * 
	 * @param out
	 *          The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void write(byte[] out) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed() {
		// Notify the client that the connection failed.
		mHandler.obtainMessage(Pubsub.CONNECTION_FAILED).sendToTarget();

		// Start the service over to restart listening mode
		PubsubComm.this.start();
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost() {
		// Notify the client that a disconnection happend.
		mHandler.obtainMessage(Pubsub.CONNECTION_LOST).sendToTarget();
		
		// Start the service over to restart listening mode
		PubsubComm.this.start();
	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or fails.
	 */
	private class ConnectThread extends Thread {
		private Socket mmSocket;
		private String host, port, sub;

		public ConnectThread(String host, String port, String sub) {
			this.host = host;
			this.port = port;
			this.sub = sub;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectThread");

			setName("ConnectThread");

			Socket tmp = null;

			// Attempt to get the socket connection
			try {
				tmp = new Socket(host, Integer.parseInt(port));
			} catch (IOException e) {
				connectionFailed();
				return;
			}

			mmSocket = tmp;

			// Reset the ConnectThread because we're done
			synchronized (PubsubComm.this) {
				mConnectThread = null;
			}

			// Start the connected thread
			connected(mmSocket, sub);
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect " + mmSocket.getInetAddress()
						+ " socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final Socket mmSocket;

		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		private StringBuffer mStringBuffer;

		public ConnectedThread(Socket socket) {
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;

			mStringBuffer = new StringBuffer();
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectedThread");

			byte[] buffer = new byte[1024];
			int bytes;

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);

					if (bytes > 0) {
						// Send the obtained bytes to the UI Activity
						mHandler.obtainMessage(Pubsub.RAW_TEXT, bytes, -1, buffer)
								.sendToTarget();

						Log.i(TAG, "recieved: " + new String(buffer, 0, bytes));
						
						mStringBuffer.append(new String(buffer, 0, bytes));
						
						
						// Process the stringbuffer
						while(hasNext(mStringBuffer)) {
							// Get the next JSONObject
							int[] startAndStop = getNext(mStringBuffer);
							String next = mStringBuffer.substring(startAndStop[0],
									startAndStop[1]);

							// Process the JSON message
							process(next);

							// Delete the selected characters from the buffer.
							mStringBuffer.delete(startAndStop[0], startAndStop[1]);
						}
						
						mStringBuffer.setLength(0);
					} else if (bytes == -1) {
						// End of stream.
						Log.e(TAG, "End of stream found (-1).");
						connectionLost();
						// Start the service over to restart listening mode
						PubsubComm.this.start();
						break;
					}

				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					connectionLost();
					// Start the service over to restart listening mode
					PubsubComm.this.start();
					break;
				}
			}
		}

		/**
		 * Create and send the JSONObject to the Processing sketch.
		 * 
		 * @param next
		 */
		private void process(String json_formatted) {
			try {
				JSONObject message = new JSONObject(json_formatted);
				int callback_id = message.getInt("id");
				JSONObject doc = message.getJSONObject("doc");
				// Send the message
				mHandler.obtainMessage(callback_id, doc).sendToTarget();
			} catch (JSONException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}

		/**
		 * Detect if the StringBuffer has another JSON package inside it...
		 * 
		 * @param mStringBuffer
		 * @return
		 */
		private boolean hasNext(StringBuffer mStringBuffer) {
			int start = mStringBuffer.indexOf("{");
			int end = -1;

			if (start != -1) {
				int starts = 1;
				int ends = 0;

				for (int i = start; i < mStringBuffer.length(); i++) {

					if (mStringBuffer.charAt(i) == '{') {
						starts++;
					} else if (mStringBuffer.charAt(i) == '}') {
						ends++;
						end = i + 1;
					}

					if (starts == ends)
						break;
				}
			}

			if (start != -1 && end != -1 && start < end)
				return true;

			return false;
		}

		private int[] getNext(StringBuffer mStringBuffer) {
			int start = mStringBuffer.indexOf("{");
			int end = -1;

			if (start != -1) {
				int starts = 1;
				int ends = 0;

				for (int i = start + 1; i < mStringBuffer.length(); i++) {

					if (mStringBuffer.charAt(i) == '{') {
						starts++;
					} else if (mStringBuffer.charAt(i) == '}') {
						ends++;
						end = i + 1;
					}

					if (starts == ends)
						break;
				}
			}

			if (start != -1 && end != -1)
				return new int[] { start, end };

			return null;
		}

		/**
		 * Write to the connected OutStream.
		 * 
		 * @param buffer
		 *          The bytes to write
		 */
		public void write(byte[] buffer) {
			try {
				mmOutStream.write(attachHeaderAndFooter(buffer));

				// Share the sent message back to the UI Activity
				mHandler.obtainMessage(Pubsub.SENT_MESSAGE, -1, -1, buffer)
						.sendToTarget();
			} catch (IOException e) {
				Log.e(TAG, "Exception during write", e);
			}
		}

		/**
		 * This adds the required header and footer for the package, without them
		 * the hub won't recognize the message.
		 * 
		 * @param buffer
		 * @return
		 */
		private byte[] attachHeaderAndFooter(byte[] buffer) {
			// In total, 2 bytes longer than the original message!
			byte[] sendbuffer = new byte[buffer.length + 2];

			// Set the first byte (0x000000)
			sendbuffer[0] = (byte) 0x000000;

			// Add the real package (buffer)
			for (int i = 1; i < sendbuffer.length - 1; i++)
				sendbuffer[i] = buffer[i - 1];

			// Add the footer (0xFFFFFD)
			sendbuffer[sendbuffer.length - 1] = (byte) 0xFFFFFD;

			return sendbuffer;
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}