package plugin.google.maps;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

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
