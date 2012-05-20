/*
 * pubsub.io Android Example
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

package pubsub.io.android.example;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pubsub.io.android.Pubsub;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Example pubsub.io - Android client.
 * 
 * @author Andreas Goransson
 * 
 */
public class Pubsub_exampleActivity extends Activity implements
		SensorEventListener, LocationListener {

	protected static final String TAG = "Pubsub_exampleActivity";

	// Custom handler callbacks
	protected static final int VERSION_FILTER = 1;

	// Used to toggle the accelerometer publishing
	private boolean publishing_acc = false;

	// Used to toggle the gps publishing
	private boolean publishing_gps = false;

	// Pubsub object
	private Pubsub mPubsub;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	private LocationManager mLocationManager;

	private TextView txtAccelerometer, txtGps;

	private ArrayList<String> json_messages;

	private ArrayAdapter<String> mArrayAdapter;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		timer = System.currentTimeMillis();

		mPubsub = new Pubsub(this, mHandler);
		mPubsub.connect("android");

		// Sensors
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500,
				5, this);

		// UI
		Button publish_message = (Button) findViewById(R.id.button1);
		publish_message.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				JSONObject doc = new JSONObject();
				try {
					JSONArray authors = new JSONArray();
					authors.put("mathias");
					authors.put("ian");
					doc.put("authors", authors);
					doc.put("version", 0.1);
				} catch (JSONException e) {
					e.printStackTrace();
				}

				mPubsub.publish(doc);
			}
		});

		txtAccelerometer = (TextView) findViewById(R.id.textView2);
		txtAccelerometer.setText((publishing_acc == true ? "ON" : "OFF"));
		txtAccelerometer.setBackgroundColor((publishing_acc == true ? Color.GREEN
				: Color.RED));
		Button publish_accelerometer = (Button) findViewById(R.id.button2);
		publish_accelerometer.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				publishing_acc = !publishing_acc;
				txtAccelerometer.setText((publishing_acc == true ? "ON" : "OFF"));
				txtAccelerometer
						.setBackgroundColor((publishing_acc == true ? Color.GREEN
								: Color.RED));
			}
		});

		txtGps = (TextView) findViewById(R.id.textView3);
		txtGps.setText((publishing_gps == true ? "ON" : "OFF"));
		txtGps
				.setBackgroundColor((publishing_gps == true ? Color.GREEN : Color.RED));
		Button publish_gps = (Button) findViewById(R.id.button3);
		publish_gps.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				publishing_gps = !publishing_gps;
				txtGps.setText((publishing_gps == true ? "ON" : "OFF"));
				txtGps.setBackgroundColor((publishing_gps == true ? Color.GREEN
						: Color.RED));
			}
		});

		json_messages = new ArrayList<String>();
		mArrayAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, json_messages);
		ListView mListView = (ListView) findViewById(R.id.listView1);
		mListView.setAdapter(mArrayAdapter);
	}

	@Override
	protected void onPause() {
		mSensorManager.unregisterListener(this);

		super.onPause();
	}

	@Override
	protected void onResume() {
		// mPubsub.reconnect();

		mSensorManager.registerListener(this, mAccelerometer,
				SensorManager.SENSOR_DELAY_NORMAL);

		super.onResume();
	}

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case Pubsub.CONNECTION_FAILED:
				// Connection failed... what to do?
				break;
				
			case Pubsub.CONNECTED_TO_HOST:
				// When we're connected, print the host name in a Toast.
				Toast.makeText(Pubsub_exampleActivity.this,
						msg.getData().getString(Pubsub.HOST_NAME), Toast.LENGTH_SHORT)
						.show();
				break;

			case Pubsub.CONNECTION_LOST:
				// Connection lost, what to do?
				mPubsub.reconnect();
				break;

			case Pubsub.SUBSCRIBES:
				// Subscribe to all "version" messages with values greater than 0.1!
				JSONObject json_filter = new JSONObject();
				try {
					JSONObject version = new JSONObject();
					version.put("$gt", 0.1);

					json_filter.put("version", version);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				mPubsub.subscribe(json_filter, VERSION_FILTER);
				break;

			case Pubsub.RAW_TEXT:
				// We can get the un-parsed message in here if we want...
				byte[] readBuf = (byte[]) msg.obj;
				String readMessage = new String(readBuf, 0, msg.arg1);
				Log.i(TAG, readMessage);
				break;

			case VERSION_FILTER:
				// Get the message (doc) from the server.
				JSONObject doc = (JSONObject) msg.obj;

				try {
					double version = doc.getDouble("version");
					// Add the value to the ListView
					json_messages.add(0, Double.toString(version));
					mArrayAdapter.notifyDataSetChanged();
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;

			}
		}
	};

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// We're just making sure that
		if (publishing_acc && time_to_publish(200)) {
			float[] vals = event.values;

			JSONObject doc = new JSONObject();
			try {
				doc.put("x", vals[0]);
				doc.put("y", vals[1]);
				doc.put("z", vals[2]);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			mPubsub.publish(doc);
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		// We're just making sure that
		if (publishing_gps && time_to_publish(200)) {
			double lat = location.getLatitude();
			double lon = location.getLongitude();

			JSONObject doc = new JSONObject();
			try {
				doc.put("lat", lat);
				doc.put("lon", lon);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			mPubsub.publish(doc);
		}
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	/** Contains the last known time for publish */
	private long timer = 0;

	/**
	 * Determines if it's time to publish another value to the stream.
	 * 
	 * @param delay
	 * @return
	 */
	private boolean time_to_publish(long delay) {
		long currenttime = System.currentTimeMillis();

		boolean timetopublish = (currenttime - timer > delay ? true : false);

		if (timetopublish)
			timer = currenttime;

		return timetopublish;
	}
}