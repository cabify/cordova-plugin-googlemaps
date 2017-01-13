package plugin.google.maps;


import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

public class RoundedVehicleMarker implements Target {

    private static int MARKER_SIZE_PX = 48;
    private static int INNER_VEHICLE_SIZE_PX = 32;

    private final PluginAsyncInterface pluginCallback;

    private final AsyncLoadImageInterface asyncLoadImageInterface;

    private Context mContext;

    private int MARKER_DIMENSION;

    private FakedR fakedR;

    public RoundedVehicleMarker(
            PluginAsyncInterface pluginCallback,
            AsyncLoadImageInterface asyncLoadImageInterface,
            Context context) {
        this.pluginCallback = pluginCallback;
        this.asyncLoadImageInterface = asyncLoadImageInterface;
        mContext = context;
        initView(context);
    }

    public void initView(Context context) {
        fakedR = new FakedR(context);
        MARKER_DIMENSION = dpToPx(MARKER_SIZE_PX, context.getResources());
    }

    public RequestCreator buildIconRequestCreator(RequestCreator requestCreator) {
        return requestCreator
                .error(fakedR.getId("drawable", "ic_placeholder_car_hire"))
                .placeholder(fakedR.getId("drawable", "ic_placeholder_car_hire"))
                .transform(new ColorFilterTransformation(Color.WHITE))
                .resize(dpToPx(INNER_VEHICLE_SIZE_PX, mContext.getResources()), 0);
    }

    private int dpToPx(float dips, Resources resources) {
        float pixels = TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dips, resources.getDisplayMetrics());
        return (int) pixels;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
        insertVehicleBitmapOnLayerList(bitmap);
    }

    @Override
    public void onBitmapFailed(Drawable drawable) {
        insertVehicleBitmapOnLayerList(drawableToBitmap(drawable));
    }

    @Override
    public void onPrepareLoad(Drawable drawable) {

    }

    private void insertVehicleBitmapOnLayerList(Bitmap bitmap) {

        Bitmap output = Bitmap
                .createBitmap(MARKER_DIMENSION, MARKER_DIMENSION, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = Color.parseColor("#2E2545");
        final Paint paint = new Paint();

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(MARKER_DIMENSION / 2, MARKER_DIMENSION / 2, MARKER_DIMENSION / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        int x = (MARKER_DIMENSION / 2) - (bitmap.getWidth() / 2);

        int y = (MARKER_DIMENSION / 2) - (bitmap.getHeight() / 2);

        canvas.drawBitmap(bitmap, x, y, null);

        asyncLoadImageInterface.onPostExecute(output);

    }


    private Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1,
                    Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap
                    .createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                            Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }


}
