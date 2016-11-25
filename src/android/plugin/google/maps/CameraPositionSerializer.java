package plugin.google.maps;

import com.google.android.gms.maps.model.CameraPosition;
import org.json.JSONObject;
import org.json.JSONException;

public class CameraPositionSerializer {

  public static String toString (CameraPosition position) {
  	JSONObject params = new JSONObject();
    String positionStr = "";

    try {
      JSONObject target = new JSONObject();
      target.put("lat", position.target.latitude);
      target.put("lng", position.target.longitude);
      params.put("target", target);
      params.put("hashCode", position.hashCode());
      params.put("bearing", position.bearing);
      params.put("tilt", position.tilt);
      params.put("zoom", position.zoom);
      positionStr = params.toString();
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return positionStr;
  }

}