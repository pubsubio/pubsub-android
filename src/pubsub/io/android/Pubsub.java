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

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

/**
 * Pubsub.io Android service class. This class allows an Android app to send to,
 * and read from, pubsub.io subs.
 * 
 * The reason for having this as a service instead of a component of an app is
 * if the library should be used in a passive sense as well as active we need to
 * be able of reading the connection while the application is not in the
 * foreground.
 * 
 * @author Andreas Goransson
 * 
 */
public class Pubsub {

	private static final String TAG = "Pubsub";
	private boolean DEBUG = false;

	// Callback constants
	/** Recieved raw bytes from the socket */
	public static final int RAW_TEXT = 10;

	/** Perform all subscribes in this callback! */
	public static final int SUBSCRIBES = 12;

	/** Connection changed state */
	public static final int STATE_CHANGE = 13;

	/** Notification that the connection was lost (was connected) */
	public static final int CONNECTION_LOST = 14;

	/** Notification that the connection failed (was never connected) */
	public static final int CONNECTION_FAILED = 16;

	/** Recieved when succesful connection was established to PubSub host */
	public static final int CONNECTED_TO_HOST = 15;
	public static final String HOST_NAME = "host";

	/** Message sent by this client to PubSub.io host */
	public static final int SENT_MESSAGE = 16;

	private PubsubComm mPubsubComm = null;
	private Handler mHandler;

	private String mHost = "";
	private String mPort = "";
	private String mSub = "";

	private Context mContext;

	public Pubsub(Context ctx, Handler handler) {
		mContext = ctx;
		mHandler = handler;

		setupPubsub();
	}

	/**
	 * To debug or not debug, it is the question.
	 * 
	 * @param debug
	 *            ...and this is the answer.
	 */
	public void DEBUG(boolean debug) {
		this.DEBUG = debug;
	}

	private void setupPubsub() {
		if (DEBUG)
			Log.i(TAG, "setupPubsub()");

		// Initialize the BluetoothChatService to perform bluetooth connections
		mPubsubComm = new PubsubComm(mContext, mHandler);
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
					mContext,
					"Your device needs internet connection! Connection aborted!",
					Toast.LENGTH_SHORT).show();
			return;
		}

		mHost = host;
		mPort = port;
		mSub = sub;

		mPubsubComm.connect(mHost, mPort, mSub);
	}

	public void reconnect() {
		if (mHost != null && mPort != null && mSub != null)
			this.connect(mHost, mPort, mSub);
	}

	/**
	 * Hook up to a specific sub.
	 * 
	 * @param sub
	 */
	public void sub(String sub) {
		if (DEBUG)
			Log.i(TAG, "Sub: " + sub);

		// Check that we're actually connected before trying anything
		if (mPubsubComm.getState() != mPubsubComm.STATE_CONNECTED) {
			Toast.makeText(mContext, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		try {
			mPubsubComm.write(PubsubParser.sub(sub).getBytes());
		} catch (JSONException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	/**
	 * Subscribe to a filter, with a specified handler_callback, on the
	 * connected sub. The handler_callback should be a declared constant, and it
	 * should be used in the Handler of your activity!
	 * 
	 * @param json_filter
	 * @param handler_callback
	 * @throws JSONException
	 */
	public void subscribe(JSONObject json_filter, int handler_callback)
			throws JSONException {
		if (DEBUG)
			Log.i(TAG, "Subscribe: " + json_filter.toString());

		// Check that we're actually connected before trying anything
		if (mPubsubComm.getState() != mPubsubComm.STATE_CONNECTED) {
			Toast.makeText(mContext, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		new CommTask().execute(PubsubParser.subscribe(json_filter,
				handler_callback).getBytes());
	}

	/**
	 * Unsubscribe the specified handler_callback.
	 * 
	 * @param handler_callback
	 * @throws JSONException
	 */
	public void unsubscribe(Integer handler_callback) throws JSONException {
		if (DEBUG)
			Log.i(TAG, "Unsubscribe: " + handler_callback);

		// Check that we're actually connected before trying anything
		if (mPubsubComm.getState() != mPubsubComm.STATE_CONNECTED) {
			Toast.makeText(mContext, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		new CommTask().execute(PubsubParser.unsubscribe(handler_callback)
				.getBytes());
	}

	/**
	 * Publish a document to the connected sub.
	 * 
	 * @param doc
	 * @throws JSONException
	 */
	public void publish(JSONObject doc) throws JSONException {
		if (DEBUG)
			Log.i(TAG, "Publish: " + doc.toString());

		// Check that we're actually connected before trying anything
		if (mPubsubComm.getState() != mPubsubComm.STATE_CONNECTED) {
			Toast.makeText(mContext, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		new CommTask().execute(PubsubParser.publish(doc).getBytes());
	}

	/**
	 * Disconnect the communication, this will stop the thread (and consequently
	 * all socket communication too)
	 */
	public void disconnect() {
		if (DEBUG)
			Log.i(TAG, "disconnect()");

		if (mPubsubComm != null)
			mPubsubComm.stop();
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
		if (DEBUG)
			Log.i(TAG, "setHandler()");

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
	protected void write(String message) {
		if (DEBUG)
			Log.i(TAG, "Write: " + message);

		// Check that we're actually connected before trying anything
		if (mPubsubComm.getState() != mPubsubComm.STATE_CONNECTED) {
			Toast.makeText(mContext, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		// Send a message to the PubSub.io host
		mPubsubComm.write(message.getBytes());
	}

	/**
	 * Detects if we have internet or not, checks both WiFi and 3G.
	 * 
	 * @return
	 */
	public boolean hasInternet() {
		if (DEBUG)
			Log.i(TAG, "hasInternet()");

		ConnectivityManager cm = (ConnectivityManager) mContext
				.getSystemService(Context.CONNECTIVITY_SERVICE);

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

	@SuppressLint("NewApi")
	private class CommTask extends AsyncTask<byte[], Void, Void> {

		@Override
		protected Void doInBackground(byte[]... params) {
			mPubsubComm.write(params[0]);
			return null;
		}
	}

}
