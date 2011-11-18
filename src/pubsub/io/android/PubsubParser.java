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

/**
 * Class for parsing pubsub.io messages.
 * 
 * @author Andreas G�ransson
 * 
 */
public class PubsubParser {

	/**
	 * Creates the "sub" message.
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
	 * Creates the "subscribe" message.
	 * 
	 * @param json_filter
	 * @param handler_callback
	 * @return
	 * @throws JSONException
	 */
	public static String subscribe(JSONObject json_filter, int handler_callback)
			throws JSONException {

		JSONObject root = new JSONObject();
		root.put("name", "subscribe");
		root.put("query", json_filter);
		root.put("id", handler_callback);

		return root.toString();
	}

	/**
	 * Creates the "unsubscribe" message.
	 * 
	 * @param sub
	 * @return
	 * @throws JSONException
	 */
	public static String unsubscribe(int handler_callback) throws JSONException {

		JSONObject root = new JSONObject();
		root.put("name", "unsubscribe");
		root.put("id", handler_callback);

		return root.toString();
	}

	/**
	 * Creates the "publish" message.
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
