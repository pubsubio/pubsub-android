package pubsub.io.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * 
 * @author Andreas G�ransson
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

		// Could force stop communication here if we wanted, but that makes no sense
		// since that happens in onPause... should communication stop just because
		// you're getting a phonecall? That might be a limitation in Android though,
		// I don't know...
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

		if (mPubsubComm == null) {
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
				// Might cause null pointers if the handler isn't set though... meh!
				mHandler.obtainMessage(TERMINATED).sendToTarget();
			}
		} else {
			if (DEBUG)
				Log.i(TAG, "Pubsub.io already connected, ignoring");
		}
	}

	/**
	 * Detects if we're subscribed to a sub. THIS DOESNT WORK! IT WILL
	 * AUTOMATICALLY RETURN TRUE RIGHT NOW... how can we detect if it's connected?
	 * some sort of ping perhaps? or a query even... or maybe trust the developer
	 * to do the right thing? (yeah, right... that'll happen, I can't even do the
	 * right thing for crying out loud)
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
	 * Subscribe to a filter, with a specified handler_callback, on the connected
	 * sub. The handler_callback should be a declared constant, and it should be
	 * used in the Handler of your activity!
	 * 
	 * @param json_filter
	 * @param handler_callback
	 */
	public void subscribe(JSONObject json_filter, int handler_callback) {
		if (mPubsubComm != null) {
			try {
				// Send the message to the hub
				mPubsubComm.write(PubsubParser.subscribe(json_filter, handler_callback)
						.getBytes());
			} catch (JSONException e) {
				if (mHandler != null) {
					JSONObject error = new JSONObject();
					try {
						error.put("simple", "Failed to construct subscribe message.");
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
				mPubsubComm
						.write(PubsubParser.unsubscribe(handler_callback).getBytes());
			} catch (JSONException e) {
				if (mHandler != null) {
					JSONObject error = new JSONObject();
					try {
						error.put("simple", "Failed to construct unsubscribe message.");
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
						error.put("simple", "Failed to construct publish message.");
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
	 * callbacks from the hub will arrive, and also some library callbacks can be
	 * read from the same handler. Library handlers include RAW_TEXT, TERMINATED,
	 * and ERROR.
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
	 * Nah, it's not that bad... chances are you'll do it wrong though so it won't
	 * work.
	 * 
	 * @param message
	 */
	@Deprecated
	public void write(String message) {
		if (DEBUG)
			Log.i(TAG, "Write: " + message);

		mPubsubComm.write(message.getBytes());
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
			// When people run in circles it's a very very... mad world, mad world
			mOutputStream = tmpOut;
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
						// Always send the raw text
						mHandler.obtainMessage(RAW_TEXT, bytes, -1, buffer).sendToTarget();

						// Parse the message and send to the right callback
						String readMessage = new String(buffer, 0, bytes);
						publishProgress(readMessage);
					}

				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					// TODO, restart the thing if it failed?
					break;
				}

			}

			return null;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			/*
			 * For now we'll use the onProgressUpdate for sending callback messages
			 * back to the activity, this might not be optimal because the messages
			 * might stack and deliver several at once (i.e. they might not be
			 * delivered "on time")
			 */
			for (int i = 0; i < values.length; i++) {
				try {
					JSONObject message = new JSONObject(values[i]);
					int callback_id = message.getInt("id");
					JSONObject doc = message.getJSONObject("doc");
					// Send the message
					mHandler.obtainMessage(callback_id, doc).sendToTarget();
				} catch (JSONException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}

			super.onProgressUpdate(values);
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
		 * Just stops the streams and sends the TERMINATED message to the activity.
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
	}
}
