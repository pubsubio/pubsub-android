package pubsub.io.android;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class for parsing pubsub.io messages.
 * 
 * @author Andreas Göransson
 * 
 */
public class PubsubParser {

	/**
	 * Subscribes to a specific sub.
	 * 
	 * @param sub
	 * @return
	 * @throws JSONException
	 */
	public static String sub(String sub) throws JSONException {

		JSONObject root = new JSONObject();
		root.put("sub", sub);

		return root.toString();
	}

	/**
	 * Publish to a sub that is subscribed.
	 * 
	 * @param doc
	 * @return
	 * @throws JSONException
	 */
	public static String publish(JSONObject doc) throws JSONException {

		JSONObject root = new JSONObject();
		root.put("name", "publish");
		root.put("doc", doc);

		return root.toString();
	}
}
