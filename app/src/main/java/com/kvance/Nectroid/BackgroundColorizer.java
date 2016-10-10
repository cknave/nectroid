
// This file is part of Nectroid.
//
// Nectroid is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Nectroid is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Nectroid.  If not, see <http://www.gnu.org/licenses/>.

package com.kvance.Nectroid;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;


class BackgroundColorizer
{
    private Context mContext;
    private float[] mShift;
    private float[] mTempHSV;

    // Source and destination bitmaps
    private final Bitmap mSource;
    private Bitmap mDest;
    private BitmapDrawable mDestDrawable;
    private LayerDrawable mLayerBackground;

    // The reference color of R.drawable.orange_waffle
    private static final int SRC_COLOR = Color.parseColor("#F37502");
    private static final float[] SRC_HSV = new float[3];
    {   Color.colorToHSV(SRC_COLOR, SRC_HSV); }

    // New version of the BitmapDrawable constructor
    Constructor mBitmapDrawableConstructor;

    BackgroundColorizer(Context context)
    {
        Resources resources = context.getResources();
        mContext = context;
        mShift = new float[3];
        mTempHSV = new float[3];
        mSource = getSourceBitmap(context.getResources());
        Bitmap.Config config = mSource.getConfig();
        if(config == null) {
            config = Bitmap.Config.RGB_565;
        }
        mDest = Bitmap.createBitmap(mSource.getWidth(), mSource.getHeight(), config);

        // Look for the newer BitmapDrawable constructor.
        Class[] constructorArgs = { Resources.class, Bitmap.class };
        try {
            mBitmapDrawableConstructor = BitmapDrawable.class.getConstructor(constructorArgs);
        } catch(NoSuchMethodException e) {
            mBitmapDrawableConstructor = null;
        }
    }

        
    ///
    /// Public interface
    ///

    /** Shift the background color to this value. */
    public void shiftBackgroundColorTo(int color)
    {
        // Create the new shift values.
        calculateShiftForColor(color, mShift);

        // Shift every pixel.
        for(int y = 0; y < mSource.getHeight(); y++) {
            for(int x = 0; x < mSource.getWidth(); x++) {
                int srcColor = mSource.getPixel(x, y);
                Color.colorToHSV(srcColor, mTempHSV);
                shiftHSV(mTempHSV, mTempHSV);
                int destColor = Color.HSVToColor(mTempHSV);
                mDest.setPixel(x, y, destColor);
            }
        }

        // Create new drawables.
        updateDrawables();
    }


    /** Return the color-shifted background drawable. */
    public Drawable getDrawable()
    {
        return mLayerBackground;
    }


    ///
    /// Utility methods
    ///

    /** Return the orange_waffle source bitmap. */
    private final Bitmap getSourceBitmap(Resources res)
    {
        BitmapDrawable drawable = (BitmapDrawable)res.getDrawable(R.drawable.orange_waffle);
        return drawable.getBitmap();
    }


    /** Fill in shift with the [h_rot, s_coeff, v_coeff] values for hsv. */
    private void calculateShiftForHSV(float[] hsv, float[] shift)
    {
        shift[0] = hsv[0] - SRC_HSV[0]; // hue rotation
        shift[1] = hsv[1] / SRC_HSV[1]; // saturation coefficient
        shift[2] = hsv[2] / SRC_HSV[2]; // value coefficient
    }


    /** Fill in shift with the [h_rot, s_coeff, v_coeff] values for this color. */
    private void calculateShiftForColor(int color, float[] shift)
    {
        Color.colorToHSV(color, mTempHSV);
        calculateShiftForHSV(mTempHSV, shift);
    }


    /** Shift this source HSV color by mShift to the destination HSV color. */
    private void shiftHSV(float[] src, float[] dest)
    {
        dest[2] = src[2] * mShift[2]; // value scaling
        dest[1] = src[1] * mShift[1]; // saturation scaling
        dest[0] = src[0] + mShift[0]; // hue rotation
        if(dest[0] < 0.0) {
            dest[0] += 1.0;
        } else if(dest[0] > 1.0) {
            dest[0] -= 1.0;
        }
    }

    /** Update all background drawables. */
    private void updateDrawables()
    {
        // Repeating background:
        Resources res = mContext.getResources();
        mDestDrawable = makeBitmapDrawable(res, mDest);
        mDestDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        // Layered gradient:
        Drawable gradient = res.getDrawable(R.drawable.bg_gradient);
        Drawable[] layers = {
            mDestDrawable,
            gradient
        };
        mLayerBackground = new LayerDrawable(layers);
    }


    /** Create a new BitmapDrawable for this Bitmap. */
    private BitmapDrawable makeBitmapDrawable(Resources resources, Bitmap bitmap)
    {
        BitmapDrawable result = null;

        // Use the new constructor if it's available.
        if(mBitmapDrawableConstructor != null) {
            Object[] args = {resources, bitmap};
            try {
                Object newInstance = mBitmapDrawableConstructor.newInstance(args);
                result = (BitmapDrawable)newInstance;
            } catch(InstantiationException e) {
                // result stays null
            } catch(IllegalAccessException e) {
                // result stays null
            } catch(InvocationTargetException e) {
                // result stays null
            }
        }

        // Otherwise, use the old constructor.
        if(result == null) {
            result = new BitmapDrawable(bitmap);
        }
        return result;
    }
}
