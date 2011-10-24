package pubsub.io.android.example;

import org.json.JSONException;
import org.json.JSONObject;

import pubsub.io.android.Pubsub;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * Example pubsub.io - Android client.
 * 
 * @author Andreas G�ransson
 * 
 */
public class Pubsub_exampleActivity extends Activity implements
		SensorEventListener {

	protected static final String TAG = "Pubsub_exampleActivity";

	protected static final int MY_HANDLER = 1241;

	// Pubsub object
	private Pubsub mPubsub;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		
		Button publish = (Button) findViewById(R.id.button1);
		publish.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				JSONObject doc = new JSONObject();
				try {
					doc.put("where", "android");
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				mPubsub.publish(doc);
			}
		});
	}

	@Override
	protected void onPause() {
		// Disconnect from the service
		unbindService(serviceConnection);

		mSensorManager.unregisterListener(this);

		super.onPause();
	}

	@Override
	protected void onResume() {
		// Connect to the service
		Intent intent = new Intent(this, Pubsub.class);
		startService(intent);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		mSensorManager.registerListener(this, mAccelerometer,
				SensorManager.SENSOR_DELAY_NORMAL);

		super.onResume();
	}

	private ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mPubsub = ((Pubsub.LocalBinder) service).getService();
			mPubsub.setHandler(mHandler);
			mPubsub.connect("10.3.3.11", "10547");

			// In other clients the "connect" and the "sub" method are done at
			// the same time... this should probably be changed!
			mPubsub.sub("android");

			// Subscribe to everything?
			JSONObject json_filter = new JSONObject();
			mPubsub.subscribe(json_filter, MY_HANDLER);
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mPubsub.disconnect();
			mPubsub = null;
			mPubsub.setHandler(null);
		}
	};

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Pubsub.RAW_TEXT:
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				// TODO maybe log the un-parsed message?
				break;
			case Pubsub.TERMINATED:
				// TODO react to connection failures?
				break;
			case Pubsub.ERROR:
				// TODO get error message...
				break;
			case MY_HANDLER:
				Log.i(TAG, "MY_HANDLER message: " + (String) msg.obj);
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
		if (mPubsub != null && mPubsub.isSubscribed()) {
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

			// mPubsub.publish(doc);
		}
	}
}