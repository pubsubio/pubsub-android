/*
 * pubsub.io Android Library
 * Copyright (C) 2011  Andreas Göransson

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pubsub.io.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * Pubsub.io Android service class. This class allows an Android app to send to,
 * and read from, pubsub.io subs.
 * 
 * The reason for having this as a service instead of a component of an app, is
 * if the library should be used in a passive sense as well as active we need to
 * be able of reading the connection while the application is not in the
 * foreground.
 * 
 * @author Andreas Goransson
 * 
 */
public class Pubsub extends Service {

	private static final String TAG = "Pubsub";
	boolean DEBUG = true;

	// Hmm, what the heck did I use this for again? It was there for a reason, I
	// just can't remember. I think I'm loosing my mind...
	public static final float VERSION = 1.0f;

	// Callback constants
	public static final int RAW_TEXT = 10;
	public static final int TERMINATED = 11;
	public static final int ERROR = 12;

	private LocalBinder mBinder = new LocalBinder();
	private PubsubComm mPubsubComm = null;
	private Handler mHandler;

	private String mHost = "";
	private String mPort = "";
	private String mSub = "";

	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		public Pubsub getService() {
			return Pubsub.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		if (DEBUG)
			Log.i(TAG, "onBind()");

		return mBinder;
	}

	@Override
	public void onCreate() {
		if (DEBUG)
			Log.i(TAG, "onCreate()");

		super.onCreate();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		if (DEBUG)
			Log.i(TAG, "onStart()");

		super.onStart(intent, startId);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		if (DEBUG)
			Log.i(TAG, "onUnbind()");

		/*
		 * Could force stop communication here if we wanted, but that makes no
		 * sense since that happens in onPause... should communication stop just
		 * because you're getting a phonecall? That might be a limitation in
		 * Android though, I don't know... for now we'll assume that whenever an
		 * application uses startService and then bindService they WANT it to be
		 * always connected.
		 */
		return super.onUnbind(intent);
	}

	@Override
	public void onRebind(Intent intent) {
		if (DEBUG)
			Log.i(TAG, "onRebind()");

		super.onRebind(intent);
	}

	@Override
	public void onDestroy() {
		if (DEBUG)
			Log.i(TAG, "onDestroy()");

		if (mPubsubComm != null) {
			mPubsubComm.cancel(true);
		}

		super.onDestroy();
	}

	/**
	 * Connect to the default sub at hub.pubsub.io.
	 */
	public void connect() {
		// connect("hub.pubsub.io", "10547", "/");
		connect("79.125.4.43", "10547", "/");
	}

	/**
	 * Connect to a specified sub at hub.pubsub.io.
	 * 
	 * @param sub
	 */
	public void connect(String sub) {
		// connect("hub.pubsub.io", "10547", sub);
		connect("79.125.4.43", "10547", sub);
	}

	/**
	 * Connect to a specified sub on a specified pubsub hub.
	 * 
	 * @param url
	 * @param port
	 */
	public void connect(String host, String port, String sub) {
		if (DEBUG)
			Log.i(TAG, "connect(" + host + ", " + port + ", " + sub + ")");

		if (!hasInternet()) {
			Toast.makeText(
					getApplicationContext(),
					"Your device needs internet connection! Connection aborted!",
					Toast.LENGTH_SHORT).show();
			return;
		}

		mHost = host;
		mPort = port;
		mSub = sub;

		if (mPubsubComm == null)
			mPubsubComm = new PubsubComm();

		if (!mPubsubComm.isConnected()) {
			Socket socket = null;
			try {
				socket = new Socket(host, Integer.parseInt(port));
			} catch (NumberFormatException e) {
				Log.e(TAG, "Socket not created", e);
			} catch (UnknownHostException e) {
				Log.e(TAG, "Socket not created", e);
			} catch (IOException e) {
				Log.e(TAG, "Socket not created", e);
			}

			// Connect to pubsub and init thread socket
			if (socket != null) {
				mPubsubComm = new PubsubComm(socket);
				mPubsubComm.execute();
				// Also connect to the selected sub!
				sub(sub);
			} else {
				// If we failed to init the socket, just terminate the app?
				// Might cause null pointers if the handler isn't set though...
				// meh!
				mHandler.obtainMessage(TERMINATED).sendToTarget();
			}
		} else {
			if (DEBUG)
				Log.i(TAG, "Pubsub.io already connected, ignoring");
		}
	}

	/**
	 * Detects if we're subscribed to a sub. THIS DOESNT WORK! IT WILL
	 * AUTOMATICALLY RETURN TRUE RIGHT NOW... how can we detect if it's
	 * connected? some sort of ping perhaps? or a query even... or maybe trust
	 * the developer to do the right thing? (yeah, right... that'll happen, I
	 * can't even do the right thing for crying out loud)
	 * 
	 * @return
	 */
	public boolean isSubscribed() {
		// return (subs.size() > 0);
		return true;
	}

	/**
	 * Hook up to a specific sub.
	 * 
	 * @param sub
	 */
	public void sub(String sub) {
		if (mPubsubComm != null) {
			try {
				mPubsubComm.write(PubsubParser.sub(sub).getBytes());
			} catch (JSONException e) {
				if (mHandler != null) {
					JSONObject error = new JSONObject();
					try {
						error.put("simple", "Failed to construct sub message.");
						error.put("error", e.getMessage());
						mHandler.obtainMessage(ERROR, error).sendToTarget();
					} catch (JSONException e1) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	/**
	 * Subscribe to a filter, with a specified handler_callback, on the
	 * connected sub. The handler_callback should be a declared constant, and it
	 * should be used in the Handler of your activity!
	 * 
	 * @param json_filter
	 * @param handler_callback
	 */
	public void subscribe(JSONObject json_filter, int handler_callback) {
		if (mPubsubComm != null) {
			try {
				// Send the message to the hub
				mPubsubComm.write(PubsubParser.subscribe(json_filter,
						handler_callback).getBytes());
			} catch (JSONException e) {
				if (mHandler != null) {
					JSONObject error = new JSONObject();
					try {
						error.put("simple",
								"Failed to construct subscribe message.");
						error.put("error", e.getMessage());
						mHandler.obtainMessage(ERROR, error).sendToTarget();
					} catch (JSONException e1) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	/**
	 * Unsubscribe the specified handler_callback.
	 * 
	 * @param handler_callback
	 */
	public void unsubscribe(Integer handler_callback) {
		if (mPubsubComm != null) {
			try {
				// Send the message to the hub
				mPubsubComm.write(PubsubParser.unsubscribe(handler_callback)
						.getBytes());
			} catch (JSONException e) {
				if (mHandler != null) {
					JSONObject error = new JSONObject();
					try {
						error.put("simple",
								"Failed to construct unsubscribe message.");
						error.put("error", e.getMessage());
						mHandler.obtainMessage(ERROR, error).sendToTarget();
					} catch (JSONException e1) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	/**
	 * Publish a document to the connected sub.
	 * 
	 * @param doc
	 */
	public void publish(JSONObject doc) {
		if (mPubsubComm != null) {
			try {
				mPubsubComm.write(PubsubParser.publish(doc).getBytes());
			} catch (JSONException e) {
				if (mHandler != null) {
					JSONObject error = new JSONObject();
					try {
						// This comment is kind of useless!
						error.put("simple",
								"Failed to construct publish message.");
						error.put("error", e.getMessage());
						mHandler.obtainMessage(ERROR, error).sendToTarget();
					} catch (JSONException e1) {
						Log.e(TAG, e.getMessage(), e);
					}
				}
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	/**
	 * Disconnect the communication, this will stop the thread (and consequently
	 * all socket communication too)
	 */
	public void disconnect() {
		if (DEBUG)
			Log.i(TAG, "disconnect()");

		if (mPubsubComm != null)
			mPubsubComm.cancel(true);
	}

	/**
	 * Set the callback handler for the service. This is the handler where all
	 * callbacks from the hub will arrive, and also some library callbacks can
	 * be read from the same handler. Library handlers include RAW_TEXT,
	 * TERMINATED, and ERROR.
	 * 
	 * @param handler
	 */
	public void setHandler(Handler handler) {
		mHandler = handler;
	}

	/**
	 * Basic write. For the love of god, don't use this!!! All hell will break
	 * loose and tiny ants will eat your skin off when you sleep!
	 * 
	 * Nah, it's not that bad... chances are you'll do it wrong though so it
	 * won't work.
	 * 
	 * @param message
	 */
	@Deprecated
	public void write(String message) {
		if (DEBUG)
			Log.i(TAG, "Write: " + message);

		mPubsubComm.write(message.getBytes());
	}

	public boolean hasInternet() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		if (DEBUG)
			Log.i(TAG, "Testing WiFi status");

		// First test wifi for status!
		NetworkInfo netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (netInfo != null && netInfo.isConnectedOrConnecting()) {
			if (DEBUG)
				Log.i(TAG, "WiFi detected, connecting");
			return true;
		}

		if (DEBUG)
			Log.i(TAG, "No WiFi detected, trying mobile");

		netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if (netInfo != null && netInfo.isConnectedOrConnecting()) {
			if (DEBUG)
				Log.i(TAG, "Mobile detected, connecting");
			return true;
		}

		if (DEBUG)
			Log.i(TAG, "No Mobile detected, aborting");

		return false;
	}

	/**
	 * My name just never looks right in these comments... blasted English
	 * language, learn to use wierd Swedish characters, damn you!
	 * 
	 * @author Andreas G�ransson
	 * 
	 */
	private class PubsubComm extends AsyncTask<Void, String, Void> {
		// Just a log-tag
		private static final String TAG = "PubsubComm";

		// nom nom nom
		private Socket mSocket;
		private InputStream mInputStream;
		private OutputStream mOutputStream;

		private StringBuffer mStringBuffer;

		public PubsubComm() {
		}

		public PubsubComm(Socket socket) {
			// Hello teacher tell me what's my lesson
			mSocket = socket;

			// Look right through me, look right through me
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			try {
				// And I find it kind of funny
				tmpIn = socket.getInputStream();
				// I find it kind of sad
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				// The dreams in which I'm dying is the best I've ever had
				Log.e(TAG, "temp sockets not created", e);
			}

			// I find it hard to tell you I find it hard to take
			mInputStream = tmpIn;
			// When people run in circles it's a very very... mad world, mad
			// world
			mOutputStream = tmpOut;

			mStringBuffer = new StringBuffer();
		}

		public boolean isConnected() {
			return (mSocket != null && mSocket.isConnected());
		}

		@Override
		protected Void doInBackground(Void... params) {
			byte[] buffer = new byte[1024];
			int bytes;

			while (!isCancelled()) {
				try {
					// Read from the InputStream
					bytes = mInputStream.read(buffer);

					if (mHandler != null && bytes > -1) {
						// Add the read string to the StringBuffer for
						// processing later
						String readMessage = new String(buffer, 0, bytes);

						// Always send the raw text
						mHandler.obtainMessage(RAW_TEXT, bytes, -1, buffer)
								.sendToTarget();

						publishProgress(readMessage);
					}

				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					// TODO, restart the thing if it failed?
					cancel(true);
					break;
				}
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			// Add all the strings to the StringBuffer
			for (int i = 0; i < values.length; i++) {
				mStringBuffer.append(values[i]);
			}

			// Process the stringbuffer
			while (hasNext(mStringBuffer)) {
				// Get the next JSONObject
				int[] startAndStop = getNext(mStringBuffer);
				String next = mStringBuffer.substring(startAndStop[0],
						startAndStop[1]);

				// Process the JSON message
				process(next);

				// Delete the selected characters from the buffer.
				mStringBuffer.delete(startAndStop[0], startAndStop[1]);
			}

			super.onProgressUpdate(values);
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

				for (int i = start+1; i < mStringBuffer.length(); i++) {

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

		@Override
		protected void onCancelled() {
			stop();

			super.onCancelled();
		}

		@Override
		protected void onPostExecute(Void result) {
			stop();

			super.onPostExecute(result);
		}

		/**
		 * Just stops the streams and sends the TERMINATED message to the
		 * activity.
		 */
		private void stop() {
			if (DEBUG)
				Log.i(TAG, "stop()");

			// Just stop the blasted sockets lest the almighty Tengil smite ye!
			try {
				mInputStream.close();
				mOutputStream.close();
				mSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (mHandler != null)
				mHandler.obtainMessage(TERMINATED).sendToTarget();
		}

		/**
		 * Write a byte buffer to the stream
		 * 
		 * @param buffer
		 */
		public void write(byte[] buffer) {
			try {
				mOutputStream.write(attachHeaderAndFooter(buffer));
			} catch (IOException e) {
				Log.e(TAG, "Exception during write", e);
				stop();
			}
		}

		/**
		 * This adds the required header and footer for the package, without
		 * them the hub won't recognize the message.
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
	}
}
