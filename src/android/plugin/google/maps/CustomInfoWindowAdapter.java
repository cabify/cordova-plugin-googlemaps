package plugin.google.maps;


import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cabify.rider.R;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

public class CustomInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

    private final LinearLayout mWindow;

    private final TextView mInfoWindowText;

    private String mFirstString = "";
    private String mSecondString = "";

    public CustomInfoWindowAdapter(Context context) {

        mWindow = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.custom_info_window, null);
        mInfoWindowText = (TextView) mWindow.findViewById(R.id.title);
    }

    @Override
    public View getInfoWindow(Marker marker) {
        render(marker, mInfoWindowText);
        return mWindow;
    }

    @Override
    public View getInfoContents(Marker marker) {
        render(marker, mInfoWindowText);
        return mWindow;
    }

    public void updateInfoWindowText(Marker marker, String firstString, String secondString) {

        mFirstString = firstString.concat("\n");
        mSecondString = secondString;

        if (marker != null && marker.isInfoWindowShown()) {
            marker.hideInfoWindow();
            marker.showInfoWindow();
        }
    }

    private void render(Marker marker, TextView titleView) {

        if (titleView != null) {
            // Spannable string allows us to edit the formatting of the text.
            Spannable wordtoSpan = new SpannableString(mFirstString + mSecondString);
            wordtoSpan.setSpan(new RelativeSizeSpan(1.4f), mFirstString.length(),
                    mFirstString.length() + mSecondString.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (wordtoSpan.length() > 0) {
                titleView.setText(wordtoSpan);
            } else {
                marker.hideInfoWindow();
            }

        } else {
            marker.hideInfoWindow();
        }

    }
}
