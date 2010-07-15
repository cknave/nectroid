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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Activity;
import android.util.Log;


/** Transition helper.
 *
 * Provide a wrapper around Activity.overridePendingTransition() using java introspection.
 */
class Transition
{
    private static final Class[] signature = new Class[] { int.class, int.class };
    private static final Object[] args = new Object[2];
    private static Method overrideMethod;
    static {
        try {
            overrideMethod = Activity.class.getMethod("overridePendingTransition", signature);
        } catch(NoSuchMethodException e) {
            overrideMethod = null;
        }
    }

    private static final String TAG = "Nectroid";


    /** Call overridePendingTransition() if the API has it. */
    public static void set(Activity activity, int enterAnim, int exitAnim)
    {
        if(overrideMethod != null) {
            try {
                args[0] = enterAnim;
                args[1] = exitAnim;
                overrideMethod.invoke(activity, args);
            } catch(InvocationTargetException e) {
                Log.w(TAG, "Unable to invoke overridePendingTransaction");
            } catch(IllegalAccessException e) {
                Log.w(TAG, "Unable to invoke overridePendingTransaction");
            }
        }
    }
}
