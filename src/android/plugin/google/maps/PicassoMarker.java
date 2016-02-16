package plugin.google.maps;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class PicassoMarker implements Target {

    private final PluginAsyncInterface callback;
    private final AsyncLoadImageInterface asyncLoadImageInterface;

    public PicassoMarker(PluginAsyncInterface callback, AsyncLoadImageInterface asyncLoadImageInterface) {

        this.callback = callback;
        this.asyncLoadImageInterface = asyncLoadImageInterface;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PicassoMarker that = (PicassoMarker) o;

        if (!callback.equals(that.callback)) return false;
        return asyncLoadImageInterface.equals(that.asyncLoadImageInterface);

    }

    @Override
    public int hashCode() {
        int result = callback.hashCode();
        result = 31 * result + asyncLoadImageInterface.hashCode();
        return result;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        asyncLoadImageInterface.onPostExecute(bitmap);
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        asyncLoadImageInterface.onPostExecute(null);
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {

    }

}
