# pubsub-android


## Install
**Android libraries are somewhat different from normal Java libraries in that you will often download the whole Eclipse project rather than a pre-compiled .jar file. That's why you need:**

1. Install a Git client for your computer
2. Clone this repository by writing the following in the terminal. **git clone https://github.com/pubsubio/pubsub-android**
3. Create your new Android project
4. **Right-click** your **project** in the **Package Explorer** and select **Properties**
5. Select **Android** and **scroll down** to the section called *Library*
6. Press **Add** and select the library called **pubsub_android*

## Getting started with Pubsub.io for Android

**First, create a ServiceConnection in your activity. This is required because pubsub-android is a Service, and also where we define what hub & sub to connect to.**

``` java
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
```

**Now we can register callbacks that our app will react to, these are just plain integers (They should be unique!).**

``` java
// Create your own callback id, random number. Make sure they don't have the same values as any of the Pubsub constants.
private int MY_OWN_CALLBACK = 6322;

// Use our new callback id to subscribe to specific events on the sub
			JSONObject json_filter = new JSONObject();

// Our filter is currently looking for version numbers that are GREATER THAN 0.1!
			try {
				JSONObject version = new JSONObject();
				version.put("$gt", 0.1);

				json_filter.put("version", version);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			mPubsub.subscribe(json_filter, VERSION_FILTER);
```

**Then, create the Handler. This will act as a message callback between the Service and your Activity.**

``` java
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Pubsub.RAW_TEXT:
				byte[] readBuf = (byte[]) msg.obj;
				String readMessage = new String(readBuf, 0, msg.arg1);
				// TODO Do something with the message here...
				break;
			case Pubsub.TERMINATED:
				// TODO React when the connection fails...
				break;
			case Pubsub.ERROR:
				// TODO Read error messages here...
				break;
			case MY_OWN_CALLBACK:
try {
					double version = doc.getDouble("version");
					Log.i(TAG, "Version message from Pubsub.io: " + version);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	};
```

**To start the service, you'll use an intent in the "onResume()" activity method.**

``` java
	@Override
	protected void onResume() {
		// Connect to the service
		Intent intent = new Intent(this, Pubsub.class);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		super.onResume();
	}
```

**And make sure to disconnect from the service in "onPause()" too! Note, this will NOT shut down your connection.**

``` java
	@Override
	protected void onPause() {
		// Disconnect from the service
		unbindService(serviceConnection);

		super.onPause();
	}
```

## Also, you must make sure NOT to forget your AndroidManifest, it's imperative that you make the following two changes!

* Add a <service> tag that points to the Pubsub service, otherwise you can't connect to it.
* Add a <uses-permission> tag with the INTERNET rule.
