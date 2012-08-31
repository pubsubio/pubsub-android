# pubsub-android


## Installing and consuming Pubsub.io in Android
**Android libraries are somewhat different from normal Java libraries in that you will often download the whole Eclipse project rather than a pre-compiled .jar file. That's why you need:**

**Installing the library**

1. Install a Git client for your computer
2. Clone this repository by writing the following in the terminal. **git clone https://github.com/pubsubio/pubsub-android**
3. Start Eclipse
4. **Right-click** the **Package Explorer** area (don't right-click a project!) and select **Import**
5. Select **General** and then **Existing projects into workspace**
6. Select the **Select root_directory** radio button and press **browse**
7. Navigate to, and select, the directory/folder where you cloned the pubsub-android library (*see: point 2 in this list*)
8. Press **Finish**

**Consuming the library**

1. Create your new Android project
2. **Right-click** your **project** in the **Package Explorer** and select **Properties**
3. Select **Android** and **scroll down** to the section called **Library**
4. Press **Add** and select the library called **pubsub_android**

## Getting started with Pubsub.io for Android

** Create the Pubsub object, this will be your main interaction channel with the Pubsub service.**

``` java
	Pubsub mPubsub = new Pubsub( this, mHandler );
```

** All events in Pubsub.io are recievied through a Handler interface, register a new Handler and call it "mHandler". **

``` java
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Pubsub.CONNECTED_TO_HOST:
				// React when a connection attempt was successfull, for example:
				// Subscribing or publishing
				break;
			case Pubsub.CONNECTION_FAILED:
				// React when a connection attempt failed, for example:
				// Issue another connection attempt in a few seconds.
				break;
			case Pubsub.CONNECTION_LOST:
				// React when a connection was lost, for example:
				// Inform the user and abort any pubsub-dependant tasks.
				break;
			}
		}
	};
```

** Subscribing to a topic. **
``` java
	private static final int MY_FILTER = 1;
	
	JSONObject filter = new JSONObject();
	try {
		filter.put("name", "value");
		mPubsub.subscribe(filter, MY_FILTER);
	} catch (JSONException e) {
		e.printStackTrace();
	}
```

** Publishing to a topic. **
``` java
	JSONObject doc = new JSONObject();
	try {
		filter.put("name", "value");
		mPubsub.publish(doc);
	} catch (JSONException e) {
		e.printStackTrace();
	}
```

## Also, make sure to add the following <uses-permission> tags in your manifest file.

* Add a <uses-permission> tag with the INTERNET rule.
* Add a <user-permission> tag with the ACCESS_NETWORK_STATE rule.
