package plugin.google.maps;

import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.ui.IconGenerator;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaResourceApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class PluginMarker extends MyPlugin {

  private PicassoMarker picassoMarker;
  private RoundedVehicleMarker roundedVehicleMarker;
  private AsyncLoadImageInterface asyncLoadImageInterface;


  private enum Animation {
    DROP,
    BOUNCE
  }

  private CustomInfoWindowAdapter customInfoWindowAdapter;



  @SuppressWarnings("unused")
  private PluginAsyncInterface noOpAsyncInterface() {
    return new PluginAsyncInterface() {

      @Override
      public void onPostExecute(Object object){
        Log.d("client", "noOpAsyncInterface onPostExecute");
      }

      @Override
      public void onError(String errorMsg){
        Log.d("client", "noOpAsyncInterface onError: " + errorMsg);
      }
    };
  }

  @SuppressWarnings("unused")
  private MarkerOptions getMarkerOptions(final JSONObject opts) throws JSONException {
    // Create an instance of Marker class
    final MarkerOptions markerOptions = new MarkerOptions();

    if (opts.has("position")) {
        JSONObject position = opts.getJSONObject("position");
        markerOptions.position(new LatLng(position.getDouble("lat"), position.getDouble("lng")));
    }
    if (opts.has("title")) {
        markerOptions.title(opts.getString("title"));
    }
    if (opts.has("snippet")) {
        markerOptions.snippet(opts.getString("snippet"));
    }
    if (opts.has("visible")) {
      if (opts.has("icon") && "".equals(opts.getString("icon")) == false) {
        markerOptions.visible(false);
      } else {
        markerOptions.visible(opts.getBoolean("visible"));
      }
    }
    if (opts.has("draggable")) {
      markerOptions.draggable(opts.getBoolean("draggable"));
    }
    if (opts.has("rotation")) {
      markerOptions.rotation((float)opts.getDouble("rotation"));
    }
    if (opts.has("flat")) {
      markerOptions.flat(opts.getBoolean("flat"));
    }
    if (opts.has("opacity")) {
      markerOptions.alpha((float) opts.getDouble("opacity"));
    }
    if (opts.has("zIndex")) {
      // do nothing, API v2 has no zIndex :(
    }

    return markerOptions;
  }

  private Bundle getIconBundle(final JSONObject opts) throws JSONException{
    Bundle bundle = null;
    Object value = opts.get("icon");
    if (JSONObject.class.isInstance(value)) {
      JSONObject iconProperty = (JSONObject)value;
      bundle = PluginUtil.Json2Bundle(iconProperty);

      // The `anchor` of the `icon` property
      if (iconProperty.has("anchor")) {
        value = iconProperty.get("anchor");
        if (JSONArray.class.isInstance(value)) {
          JSONArray points = (JSONArray)value;
          double[] anchorPoints = new double[points.length()];
          for (int i = 0; i < points.length(); i++) {
            anchorPoints[i] = points.getDouble(i);
          }
          bundle.putDoubleArray("anchor", anchorPoints);
        }
      }

      // The `infoWindowAnchor` property for infowindow
      if (opts.has("infoWindowAnchor")) {
        value = opts.get("infoWindowAnchor");
        if (JSONArray.class.isInstance(value)) {
          JSONArray points = (JSONArray)value;
          double[] anchorPoints = new double[points.length()];
          for (int i = 0; i < points.length(); i++) {
            anchorPoints[i] = points.getDouble(i);
          }
          bundle.putDoubleArray("infoWindowAnchor", anchorPoints);
        }
      }
    } else if (JSONArray.class.isInstance(value)) {
      float[] hsv = new float[3];
      JSONArray arrayRGBA = (JSONArray)value;
      Color.RGBToHSV(arrayRGBA.getInt(0), arrayRGBA.getInt(1), arrayRGBA.getInt(2), hsv);
      bundle = new Bundle();
      bundle.putFloat("iconHue", hsv[0]);
    } else {
      bundle = new Bundle();
      bundle.putString("url", (String)value);
    }

    if (opts.has("animation")) {
      bundle.putString("animation", opts.getString("animation"));
    }
    if (opts.has("markerType")) {
        bundle.putString("markerType", opts.getString("markerType"));
    }
    if (opts.has("customMarkerFirstString")) {
        bundle.putString("customMarkerFirstString", opts.getString("customMarkerFirstString"));
    }
    if (opts.has("customMarkerSecondString")) {
        bundle.putString("customMarkerSecondString", opts.getString("customMarkerSecondString"));
    }
    if (opts.has("mapOrientation")) {
        bundle.putString("mapOrientation", opts.getString("mapOrientation"));
    }
    if (opts.has("journeyCar")) {
      bundle.putBoolean("journeyCar", opts.getBoolean("journeyCar"));
    }
    return bundle;
  }

  private void setAnimationForJustCreatedMarker(Marker marker, String markerAnimation, final CallbackContext callbackContext) throws JSONException{
    PluginAsyncInterface asyncInterface;
    if (callbackContext != null){
      asyncInterface = new PluginAsyncInterface() {

        @Override
        public void onPostExecute(Object object){
          Marker marker = (Marker)object;
          try {
            callbackContext.success(getMarkerResultJSON(marker));
          } catch (JSONException e){
            callbackContext.error("Error generating result JSON");
          }
        }

        @Override
        public void onError(String errorMsg){
          callbackContext.error(errorMsg);
        }
      };
    } else {
      asyncInterface = noOpAsyncInterface();
    }

    if (markerAnimation != null) {
      PluginMarker.this.setMarkerAnimation_(marker, markerAnimation, asyncInterface);
    } else {
        if (callbackContext != null) {
        callbackContext.success(getMarkerResultJSON(marker));
      }
    }
  }

  private void setIconforJustCreatedMarker(Marker marker, final JSONObject opts, Bundle bundle, final CallbackContext callbackContext) throws JSONException{
    Log.d("client", "-- setting icon for just created marker");
    PluginAsyncInterface asyncInterface;

      asyncInterface = new PluginAsyncInterface() {

      @Override
      public void onPostExecute(Object object) {
        Marker marker = (Marker)object;
        if (opts.has("visible")) {
          try {
            marker.setVisible(opts.getBoolean("visible"));
          } catch (JSONException e) {}
          } else {
            marker.setVisible(true);
          }
          // Animation
          if (opts.has("animation")) {
            try {
              setAnimationForJustCreatedMarker(marker, opts.getString("animation"), callbackContext);
            } catch (JSONException e) {
              e.printStackTrace();
            }
          } else {
            if (callbackContext != null){
              try {
                callbackContext.success(getMarkerResultJSON(marker));
              } catch (JSONException e){
                callbackContext.error("Error generating result JSON");
              }
            } else {
              Log.d("client", "-- setIconforJustCreatedMarker: callbackContext is null");
            }
          }
        }

        @Override
        public void onError(String errorMsg) {
          callbackContext.error(errorMsg);
        }

      };


    this.setIcon_(marker, bundle, asyncInterface);
  }

  private void storeMarker(Marker marker, final JSONObject opts) throws JSONException {
    // Store the marker
    String id = "marker_" + marker.getId();
    this.objects.put(id, marker);

    JSONObject properties = new JSONObject();
    if (opts.has("type")) {
      properties.put("type", opts.getString("type"));
      properties.put("marker_id", id);
    }
    if (opts.has("styles")) {
      properties.put("styles", opts.getJSONObject("styles"));
    }
    if (opts.has("disableAutoPan")) {
      properties.put("disableAutoPan", opts.getBoolean("disableAutoPan"));
    } else {
      properties.put("disableAutoPan", false);
    }
    this.objects.put("marker_property_" + marker.getId(), properties);
  }

  private JSONObject getMarkerResultJSON(final Marker marker) throws JSONException{
    final JSONObject result = new JSONObject();
    result.put("hashCode", marker.hashCode());
    result.put("id", "marker_" + marker.getId());
    return result;
  }


  private void setIconAndAnimationForMarker(Marker marker, JSONObject opts, CallbackContext callbackContext) throws JSONException {
    // Load icon
    if (opts.has("icon")) {
      Bundle bundle = getIconBundle(opts);
      setIconforJustCreatedMarker(marker, opts, bundle, callbackContext);
    } else {
      // Animation
      if (opts.has("animation")) {
        try {
          setAnimationForJustCreatedMarker(marker, opts.getString("animation"), callbackContext);
        } catch (JSONException e) {
          e.printStackTrace();
        }
      } else {
        if (callbackContext != null){
          callbackContext.success(getMarkerResultJSON(marker));
        }
      }
    }
  }

  @SuppressWarnings("unused")
  private Marker addMarkerToMap(MarkerOptions markerOptions, final JSONObject opts, final CallbackContext callbackContext) throws JSONException {
    Log.d("client", "-- adding marker to map");
    Marker marker = map.addMarker(markerOptions);
    storeMarker(marker, opts);
    setIconAndAnimationForMarker(marker, opts, callbackContext);
    return marker;
  }


  /**
   * Create a marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void createMarker(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    JSONObject opts = args.getJSONObject(1);
    addMarkerToMap(getMarkerOptions(opts), opts, callbackContext);
  }

  /**
   * Create multiple markers
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void createMultipleMarkers(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    final JSONArray markersJSONArray = args.getJSONArray(1);

    JSONObject opts;
    MarkerOptions markerOptions;
    Marker marker;

    Log.d("client", "creating multiple markers");

    List<JSONObject> optsList = new ArrayList<JSONObject>();
    List<MarkerOptions> markerOptionsList = new ArrayList<MarkerOptions>();
    JSONArray resultsJSONArray = new JSONArray();

    for(int i=0; i < markersJSONArray.length(); i++){
      opts = markersJSONArray.getJSONObject(i);
      optsList.add(opts);
      markerOptionsList.add(getMarkerOptions(opts));
    }

    for(int i=0; i < markersJSONArray.length(); i++){
      marker = addMarkerToMap(markerOptionsList.get(i), optsList.get(i), null);
      resultsJSONArray.put(getMarkerResultJSON(marker));
    }

    Log.d("client", "-- added markers to map");

    callbackContext.success(resultsJSONArray);
  }

  private void setDropAnimation_(final Marker marker, final PluginAsyncInterface callback) {
    final Handler handler = new Handler();
    final long startTime = SystemClock.uptimeMillis();
    final long duration = 100;

    final Projection proj = this.map.getProjection();
    final LatLng markerLatLng = marker.getPosition();
    final Point markerPoint = proj.toScreenLocation(markerLatLng);
    final Point startPoint = new Point(markerPoint.x, 0);

    final Interpolator interpolator = new LinearInterpolator();

    handler.post(new Runnable() {
      @Override
      public void run() {
        LatLng startLatLng = proj.fromScreenLocation(startPoint);
        long elapsed = SystemClock.uptimeMillis() - startTime;
        float t = interpolator.getInterpolation((float) elapsed / duration);
        double lng = t * markerLatLng.longitude + (1 - t) * startLatLng.longitude;
        double lat = t * markerLatLng.latitude + (1 - t) * startLatLng.latitude;
        marker.setPosition(new LatLng(lat, lng));
        if (t < 1.0) {
          // Post again 16ms later.
          handler.postDelayed(this, 16);
        } else {
          marker.setPosition(markerLatLng);
          callback.onPostExecute(marker);
        }
      }
    });
  }

  /**
   * Bounce animation
   * http://android-er.blogspot.com/2013/01/implement-bouncing-marker-for-google.html
   */
  private void setBounceAnimation_(final Marker marker, final PluginAsyncInterface callback) {
    final Handler handler = new Handler();
    final long startTime = SystemClock.uptimeMillis();
    final long duration = 2000;

    final Projection proj = this.map.getProjection();
    final LatLng markerLatLng = marker.getPosition();
    final Point startPoint = proj.toScreenLocation(markerLatLng);
    startPoint.offset(0, -200);

    final Interpolator interpolator = new BounceInterpolator();

    handler.post(new Runnable() {
      @Override
      public void run() {
        LatLng startLatLng = proj.fromScreenLocation(startPoint);
        long elapsed = SystemClock.uptimeMillis() - startTime;
        float t = interpolator.getInterpolation((float) elapsed / duration);
        double lng = t * markerLatLng.longitude + (1 - t) * startLatLng.longitude;
        double lat = t * markerLatLng.latitude + (1 - t) * startLatLng.latitude;
        marker.setPosition(new LatLng(lat, lng));

        if (t < 1.0) {
          // Post again 16ms later.
          handler.postDelayed(this, 16);
        } else {
          marker.setPosition(markerLatLng);
          callback.onPostExecute(marker);
        }
      }
    });
  }

  private void setMarkerAnimation_(Marker marker, String animationType, PluginAsyncInterface callback) {
    Animation animation = null;
    try {
      animation = Animation.valueOf(animationType.toUpperCase(Locale.US));
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (animation == null) {
      callback.onPostExecute(marker);
      return;
    }
    switch(animation) {
    case DROP:
      this.setDropAnimation_(marker, callback);
      break;

    case BOUNCE:
      this.setBounceAnimation_(marker, callback);
      break;

    default:
      break;
    }
  }

  /**
   *
   * http://android-er.blogspot.com/2013/01/implement-bouncing-marker-for-google.html
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setAnimation(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    String animation = args.getString(2);
    final Marker marker = this.getMarker(id);

    this.setMarkerAnimation_(marker, animation, new PluginAsyncInterface() {

      @Override
      public void onPostExecute(Object object) {
        callbackContext.success();
      }

      @Override
      public void onError(String errorMsg) {
        callbackContext.error(errorMsg);
      }

    });
  }

  /**
   * Show the InfoWindow binded with the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void showInfoWindow(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    Marker marker = this.getMarker(id);
    marker.showInfoWindow();
    this.sendNoResult(callbackContext);
  }

  /**
   * Set rotation for the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setRotation(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    float rotation = (float)args.getDouble(2);
    String id = args.getString(1);
    this.setFloat("setRotation", id, rotation, callbackContext);
  }

  /**
   * Set opacity for the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setOpacity(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    float alpha = (float)args.getDouble(2);
    String id = args.getString(1);
    this.setFloat("setAlpha", id, alpha, callbackContext);
  }

    /**
     * Set zIndex for the marker (dummy code, not available on Android V2)
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    @SuppressWarnings("unused")
    private void setZIndex(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // nothing to do :(
        // it's a shame google...
    }

  /**
   * set position
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setPosition(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    LatLng position = new LatLng(args.getDouble(2), args.getDouble(3));
    Marker marker = this.getMarker(id);
    marker.setPosition(position);
    this.sendNoResult(callbackContext);
  }

  /**
   * Set flat for the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setFlat(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    boolean isFlat = args.getBoolean(2);
    String id = args.getString(1);
    this.setBoolean("setFlat", id, isFlat, callbackContext);
  }

  /**
   * Set visibility for the object
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  protected void setVisible(JSONArray args, CallbackContext callbackContext) throws JSONException {
    boolean visible = args.getBoolean(2);
    String id = args.getString(1);
    this.setBoolean("setVisible", id, visible, callbackContext);
  }
  /**
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  protected void setDisableAutoPan(JSONArray args, CallbackContext callbackContext) throws JSONException {
    boolean disableAutoPan = args.getBoolean(2);
    String id = args.getString(1);
    Marker marker = this.getMarker(id);
    String propertyId = "marker_property_" + marker.getId();
    JSONObject properties = null;
    if (this.objects.containsKey(propertyId)) {
      properties = (JSONObject)this.objects.get(propertyId);
    } else {
      properties = new JSONObject();
    }
    properties.put("disableAutoPan", disableAutoPan);
    this.objects.put(propertyId, properties);
    this.sendNoResult(callbackContext);
  }
  /**
   * Set title for the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setTitle(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String title = args.getString(2);
    String id = args.getString(1);
    this.setString("setTitle", id, title, callbackContext);
  }

  /**
   * Set the snippet for the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setSnippet(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String snippet = args.getString(2);
    String id = args.getString(1);
    this.setString("setSnippet", id, snippet, callbackContext);
  }

  /**
   * Hide the InfoWindow binded with the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void hideInfoWindow(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    Marker marker = this.getMarker(id);
    marker.hideInfoWindow();
    this.sendNoResult(callbackContext);
  }

  /**
   * Return the position of the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void getPosition(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    Marker marker = this.getMarker(id);
    LatLng position = marker.getPosition();

    JSONObject result = new JSONObject();
    result.put("lat", position.latitude);
    result.put("lng", position.longitude);
    callbackContext.success(result);
  }

  /**
   * Return 1 if the InfoWindow of the marker is shown
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void isInfoWindowShown(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    Marker marker = this.getMarker(id);
    Boolean isInfoWndShown = marker.isInfoWindowShown();
    callbackContext.success(isInfoWndShown ? 1 : 0);
  }

  /**
   * Remove the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void remove(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    Marker marker = this.getMarker(id);
    if (marker != null) {
      removeMarker(marker, id);
      this.sendNoResult(callbackContext);
    } else {
      callbackContext.success();
    }
  }

  /**
   * Remove the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void removeMultipleMarkers(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    JSONArray markersToRemove = args.getJSONArray(1);
    String id;

    for(int i=0; i < markersToRemove.length(); i++){
      id = markersToRemove.getString(i);
      Marker marker = this.getMarker(id);
      if (marker != null) {
        removeMarker(marker, id);
      }
    }
    callbackContext.success();
  }

  @SuppressWarnings("unused")
  private void removeMultipleMarkersByType(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String typeToRemove = args.getString(1);

    for (HashMap.Entry<String, Object> entry : this.objects.entrySet()) {
      Object properties = entry.getValue();
      if (properties instanceof JSONObject) {
        String type = ((JSONObject)properties).getString("type");
        if (type.equals(typeToRemove)) {
          String id = ((JSONObject)properties).getString("marker_id");
          Marker marker = this.getMarker(id);
          if (marker != null) {
            removeMarker(marker, id);
          }
        }
      }
    }
    callbackContext.success();
  }

  private void removeMarker(Marker marker, String id) {
    try {
      marker.remove();
      this.objects.remove(id);
      String propertyId = "marker_property_" + id;
      this.objects.remove(propertyId);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   * Set anchor for the icon of the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setIconAnchor(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    float anchorX = (float)args.getDouble(2);
    float anchorY = (float)args.getDouble(3);
    String id = args.getString(1);
    Marker marker = this.getMarker(id);

    Bundle imageSize = (Bundle) this.objects.get("imageSize");
    if (imageSize != null) {
      this._setIconAnchor(marker, anchorX, anchorY, imageSize.getInt("width"), imageSize.getInt("height"));
    }
    this.sendNoResult(callbackContext);
  }

  @SuppressWarnings("unused")
  private void updateEstimationMarker(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    String firstString = args.getString(2);
    String secondString = args.getString(3);
    String mapOrientation = args.getString(4);
    Marker marker = this.getMarker(id);

    boolean markerOrientation = mapOrientation.equals("e2w");

    buildDestinationMarker(marker, markerOrientation, firstString, secondString);

    this.sendNoResult(callbackContext);
  }

  @SuppressWarnings("unused")
  private void showCustomInfoWindow(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    String firstString = args.getString(2);
    String secondString = args.getString(3);
    String markerType = args.getString(4);
    Marker marker = this.getMarker(id);


    if (markerType.equals("infowindow")) {
      if (map != null && customInfoWindowAdapter == null) {
        customInfoWindowAdapter = new CustomInfoWindowAdapter(getContext());
      }
      map.setInfoWindowAdapter(customInfoWindowAdapter);
      customInfoWindowAdapter.updateInfoWindowText(marker, firstString, secondString);
    }
    this.sendNoResult(callbackContext);
  }


  /**
   * Set anchor for the InfoWindow of the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setInfoWindowAnchor(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    float anchorX = (float)args.getDouble(2);
    float anchorY = (float)args.getDouble(3);
    String id = args.getString(1);
    Marker marker = this.getMarker(id);

    Bundle imageSize = (Bundle) this.objects.get("imageSize");
    if (imageSize != null) {
      this._setInfoWindowAnchor(marker, anchorX, anchorY, imageSize.getInt("width"), imageSize.getInt("height"));
    }
    this.sendNoResult(callbackContext);
  }

  /**
   * Set draggable for the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setDraggable(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    Boolean draggable = args.getBoolean(2);
    String id = args.getString(1);
    this.setBoolean("setDraggable", id, draggable, callbackContext);
  }

  /**
   * Set icon of the marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  private void setIcon(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String id = args.getString(1);
    Marker marker = this.getMarker(id);
    Object value = args.get(2);
    Bundle bundle = null;
    if (JSONObject.class.isInstance(value)) {
      JSONObject iconProperty = (JSONObject)value;
      bundle = PluginUtil.Json2Bundle(iconProperty);

      // The `anchor` for icon
      if (iconProperty.has("anchor")) {
        value = iconProperty.get("anchor");
        if (JSONArray.class.isInstance(value)) {
          JSONArray points = (JSONArray)value;
          double[] anchorPoints = new double[points.length()];
          for (int i = 0; i < points.length(); i++) {
            anchorPoints[i] = points.getDouble(i);
          }
          bundle.putDoubleArray("anchor", anchorPoints);
        }
      }
    } else if (JSONArray.class.isInstance(value)) {
      float[] hsv = new float[3];
      JSONArray arrayRGBA = (JSONArray)value;
      Color.RGBToHSV(arrayRGBA.getInt(0), arrayRGBA.getInt(1), arrayRGBA.getInt(2), hsv);
      bundle = new Bundle();
      bundle.putFloat("iconHue", hsv[0]);
    } else if (String.class.isInstance(value)) {
      bundle = new Bundle();
      bundle.putString("url", (String)value);
    }
    if (bundle != null) {
      this.setIcon_(marker, bundle, new PluginAsyncInterface() {

        @Override
        public void onPostExecute(Object object) {
          PluginMarker.this.sendNoResult(callbackContext);
        }

        @Override
        public void onError(String errorMsg) {
          callbackContext.error(errorMsg);
        }
      });
    } else {
      this.sendNoResult(callbackContext);
    }
  }

  private void setIcon_(final Marker marker, final Bundle iconProperty, final PluginAsyncInterface callback) {
    if (iconProperty.containsKey("iconHue")) {
      float hue = iconProperty.getFloat("iconHue");
      marker.setIcon(BitmapDescriptorFactory.defaultMarker(hue));
      callback.onPostExecute(marker);
      return;
    }

    String iconUrl = iconProperty.getString("url");
    if (iconUrl.indexOf("://") == -1 &&
        iconUrl.startsWith("/") == false &&
        iconUrl.startsWith("www/") == false) {
      iconUrl = "./" + iconUrl;
    }
    if (iconUrl.indexOf("./") == 0) {
      String currentPage = this.webView.getUrl();
      currentPage = currentPage.replaceAll("[^\\/]*$", "");
      iconUrl = iconUrl.replace("./", currentPage);
    }

    if (iconUrl == null) {
      callback.onPostExecute(marker);
      return;
    }

    // Insert here our estimation marker
    if (iconProperty.containsKey("markerType") && iconProperty.getString("markerType") != null
            && iconProperty.getString("markerType").equals("estimation")) {
      // Custom Cabify Marker
      if (iconProperty.containsKey("customMarkerFirstString") && iconProperty.containsKey("customMarkerSecondString")) {
        String firstString = iconProperty.getString("customMarkerFirstString");
        String secondString = iconProperty.getString("customMarkerSecondString");
        boolean markerOrientation = iconProperty.containsKey("mapOrientation") && iconProperty.getString("mapOrientation").equals("e2w");
        buildDestinationMarker(marker, markerOrientation, firstString, secondString);
        callback.onPostExecute(marker);
        return;
      }
    }


    if (iconUrl.indexOf("http") != 0) {

      AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>() {

        @Override
        protected Bitmap doInBackground(Void... params) {
          String iconUrl = iconProperty.getString("url");

          Bitmap image = null;
          if (iconUrl.indexOf("cdvfile://") == 0) {
            CordovaResourceApi resourceApi = webView.getResourceApi();
            iconUrl = PluginUtil.getAbsolutePathFromCDVFilePath(resourceApi, iconUrl);
          }

          if (iconUrl.indexOf("data:image/") == 0 && iconUrl.indexOf(";base64,") > -1) {
            String[] tmp = iconUrl.split(",");
            image = PluginUtil.getBitmapFromBase64encodedImage(tmp[1]);
          } else if (iconUrl.indexOf("file://") == 0 &&
              iconUrl.indexOf("file:///android_asset/") == -1) {
            iconUrl = iconUrl.replace("file://", "");
            File tmp = new File(iconUrl);
            if (tmp.exists()) {
              image = BitmapFactory.decodeFile(iconUrl);
            } else {
              if (PluginMarker.this.mapCtrl.isDebug) {
                Log.w("GoogleMaps", "icon is not found (" + iconUrl + ")");
              }
            }
          } else if (iconUrl.indexOf("file:///android_asset/") == 0) {
            iconUrl = iconUrl.replace("file:///android_asset/", "");
            if (iconUrl.indexOf("./") == 0) {
              iconUrl = iconUrl.replace("./", "www/");
            }
            AssetManager assetManager = PluginMarker.this.cordova.getActivity().getAssets();
            InputStream inputStream;
            try {
              inputStream = assetManager.open(iconUrl);
              image = BitmapFactory.decodeStream(inputStream);
            } catch (IOException e) {
              e.printStackTrace();
              callback.onPostExecute(marker);
              return null;
            }
          } else {
            if (iconUrl.indexOf("cabify:///marker/") == 0) {
              iconUrl = iconUrl.replace("cabify:///marker/", "");
            }
            int targetResourceId = FakedR.getId(getContext(),"drawable",iconUrl);

            if(targetResourceId != 0){
              image = BitmapFactory.decodeResource(PluginMarker.this.cordova.getActivity().getResources(), targetResourceId);
            }

          }

          if (image == null) {
            callback.onPostExecute(marker);
            return null;
          }

          Boolean isResized = false;
          if (iconProperty.containsKey("size") == true) {
            Object size = iconProperty.get("size");

            if (Bundle.class.isInstance(size)) {

              Bundle sizeInfo = (Bundle)size;
              int width = sizeInfo.getInt("width", 0);
              int height = sizeInfo.getInt("height", 0);
              if (width > 0 && height > 0) {
                isResized = true;
                width = (int)Math.round(width * PluginMarker.this.density);
                height = (int)Math.round(height * PluginMarker.this.density);
                image = PluginUtil.resizeBitmap(image, width, height);
              }
            }
          }
          return image;
        }

        @Override
        protected void onPostExecute(Bitmap image) {
          if (image == null) {
            callback.onPostExecute(marker);
            return;
          }

          try {
              //TODO: check image is valid?
              BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(image);
              marker.setIcon(bitmapDescriptor);

              // Save the information for the anchor property
              Bundle imageSize = new Bundle();
              imageSize.putInt("width", image.getWidth());
              imageSize.putInt("height", image.getHeight());
              PluginMarker.this.objects.put("imageSize", imageSize);


              // The `anchor` of the `icon` property
              if (iconProperty.containsKey("anchor") == true) {
                double[] anchor = iconProperty.getDoubleArray("anchor");
                if (anchor.length == 2) {
                  _setIconAnchor(marker, anchor[0], anchor[1], imageSize.getInt("width"), imageSize.getInt("height"));
                }
              }


              // The `anchor` property for the infoWindow
              if (iconProperty.containsKey("infoWindowAnchor") == true) {
                double[] anchor = iconProperty.getDoubleArray("infoWindowAnchor");
                if (anchor.length == 2) {
                  _setInfoWindowAnchor(marker, anchor[0], anchor[1], imageSize.getInt("width"), imageSize.getInt("height"));
                }
              }

              callback.onPostExecute(marker);

              } catch (java.lang.IllegalArgumentException e) {
                        Log.e("GoogleMapsPlugin","PluginMarker: Warning - marker method called when marker has been disposed, wait for addMarker callback before calling more methods on the marker (setIcon etc).");
                        //e.printStackTrace();

             }
        }
      };
      task.execute();


      return;
    }

    if (iconUrl.indexOf("http") == 0) {
      int width = -1;
      int height = -1;
      if (iconProperty.containsKey("size") == true) {

        Bundle sizeInfo = (Bundle) iconProperty.get("size");
        width = sizeInfo.getInt("width", width);
        height = sizeInfo.getInt("height", height);
      }

      asyncLoadImageInterface = new AsyncLoadImageInterface() {

        @Override
        public void onPostExecute(Bitmap image) {
          if (image == null) {
            callback.onPostExecute(marker);
            return;
          }

          try {
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(image);
            marker.setIcon(bitmapDescriptor);

            // Save the information for the anchor property
            Bundle imageSize = new Bundle();
            imageSize.putInt("width", image.getWidth());
            imageSize.putInt("height", image.getHeight());
            PluginMarker.this.objects.put("imageSize", imageSize);

            // The `anchor` of the `icon` property
            if (iconProperty.containsKey("anchor") == true) {
              double[] anchor = iconProperty.getDoubleArray("anchor");
              if (anchor.length == 2) {
                _setIconAnchor(marker, anchor[0], anchor[1], imageSize.getInt("width"), imageSize.getInt("height"));
              }
            }

            // The `anchor` property for the infoWindow
            if (iconProperty.containsKey("infoWindowAnchor") == true) {
              double[] anchor = iconProperty.getDoubleArray("infoWindowAnchor");
              if (anchor.length == 2) {
                _setInfoWindowAnchor(marker, anchor[0], anchor[1], imageSize.getInt("width"), imageSize.getInt("height"));
              }
            }

            callback.onPostExecute(marker);

          } catch (java.lang.IllegalArgumentException e) {
            e.printStackTrace();
          }
        }

      };

        RequestCreator requestCreator = Picasso.with(this.cordova.getActivity())
                    .load(iconUrl);

        if (isAJourneyCarMarker(iconProperty)) {
            roundedVehicleMarker =
                    new RoundedVehicleMarker(callback, asyncLoadImageInterface, this.cordova.getActivity());
            roundedVehicleMarker.buildIconRequestCreator(requestCreator);
            requestCreator.into(roundedVehicleMarker);

        } else {
            picassoMarker = new PicassoMarker(callback, asyncLoadImageInterface);

            if (width != -1 || height != -1) {
                requestCreator = requestCreator.resize(width, height);
            }

            requestCreator.into(picassoMarker);
        }

    }
  }

  private boolean isAJourneyCarMarker(Bundle iconProperty) {
    return iconProperty.getBoolean("journeyCar");
  }

  private void _setIconAnchor(Marker marker, double anchorX, double anchorY, int imageWidth, int imageHeight) {
    // The `anchor` of the `icon` property
    anchorX = anchorX * this.density;
    anchorY = anchorY * this.density;
    marker.setAnchor((float)(anchorX / imageWidth), (float)(anchorY / imageHeight));
  }
  private void _setInfoWindowAnchor(Marker marker, double anchorX, double anchorY, int imageWidth, int imageHeight) {
    // The `anchor` of the `icon` property
    anchorX = anchorX * this.density;
    anchorY = anchorY * this.density;
    marker.setInfoWindowAnchor((float)(anchorX / imageWidth), (float)(anchorY / imageHeight));
  }

  private void buildDestinationMarker(Marker marker, boolean leftIndicator, String first,
                                               String second) {

    IconGenerator iconFactory = new IconGenerator(getContext());

    TextView t = new TextView(getContext());
    t.setBackgroundResource(leftIndicator ? FakedR.getId(getContext(),"drawable","ic_marker_destination_left")
            : FakedR.getId(getContext(),"drawable","ic_marker_destination_right"));
    t.setTextSize(11);
    t.setTextColor(Color.parseColor("#CCCCCC"));
    t.setIncludeFontPadding(false);

    iconFactory.setBackground(null);
    iconFactory.setContentView(t);

    Bitmap sampleBitmap = iconFactory.makeIcon();
    if (first != null && !first.equals("null")) {
      // Tweak the incoming text to show it in two lines
      first = first.concat("\n");
    } else {
      first = "";
    }
    Spannable wordtoSpan = new SpannableString(first + second);

    wordtoSpan.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFFFF")), first.length(),
            first.length() + second.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    wordtoSpan.setSpan(new RelativeSizeSpan(1.2f), first.length(),
            first.length() + second.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    wordtoSpan.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), first.length(),
            first.length() + second.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    t.setText(wordtoSpan);

    Bitmap bitmap = iconFactory.makeIcon();

    marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap));
    marker.setAnchor(computeMarkerHorizontalAnchor(leftIndicator, sampleBitmap.getWidth(),
            bitmap.getWidth()), 1.0f);
  }

  private float computeMarkerHorizontalAnchor(boolean leftIndicator, int originalWidth,
                                              int computedWidth) {

    int supposedAnchorPos = (int) (leftIndicator ? originalWidth * 0.3f : originalWidth * 0.7f);

    if (leftIndicator) {
      return (float) supposedAnchorPos / computedWidth;
    } else {
      return (float) (computedWidth - (originalWidth - supposedAnchorPos)) / computedWidth;
    }
  }

  private Context getContext(){
    return this.cordova.getActivity().getApplicationContext();
  }


}
