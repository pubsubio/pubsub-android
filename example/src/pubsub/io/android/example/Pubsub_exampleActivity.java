package pubsub.io.android.example;

import pubsub.io.android.Pubsub;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

/**
 * Example pubsub.io - Android client.
 * 
 * @author Andreas Göransson
 * 
 */
public class Pubsub_exampleActivity extends Activity {

	// Pubsub object
	private Pubsub mPubsub;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	@Override
	protected void onPause() {
		// Disconnect from the service
		unbindService(serviceConnection);

		super.onPause();
	}

	@Override
	protected void onResume() {
		// Connect to the service
		Intent intent = new Intent(this, Pubsub.class);
		startService(intent);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		super.onResume();
	}

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mPubsub = ((Pubsub.LocalBinder) service).getService();
			mPubsub.setHandler(mHandler);
			mPubsub.connect();
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
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
			}
		}
	};
}