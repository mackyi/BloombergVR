package com.mackyi.cardboarddemo;
import android.app.Activity;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase;
import android.test.ActivityInstrumentationTestCase2;
import android.test.TouchUtils;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;
import android.util.Log;
import android.webkit.WebView;

public class Utils {
	
	/**
	 * transforms the coordinates of the object according to tMatrix
	 * @param tMatrix - transformation matrix
	 * @param object - object coordinates
	 * @param center - center of object
	 * @return the object coordinates translated by tMatrix
	 */
	public static float[] getTransformedObj(float[] tMatrix, float[] object, float[] center) {
		float[] moveMatrix = new float[16];
        float[] newObj = new float[12];
        for (int i = 0; i < newObj.length; i++) {
            if (i%4 == 3) {
                newObj[i] = 1;
                continue;
            }
            newObj[i] = object[i - i/4];
        }
        moveMatrix[0] = 1;
        moveMatrix[5] = 1;
        moveMatrix[10] = 1;
		moveMatrix[12] = center[0];
		moveMatrix[13] = center[1];
		moveMatrix[14] = center[2];
		moveMatrix[15] = 1;
		float[] resultM = new float[16];
		Matrix.multiplyMM(resultM, 0, tMatrix, 0, moveMatrix, 0);
        Log.d("tMatrix: ", Arrays.toString(tMatrix));
        Log.d("obj: ", Arrays.toString(newObj));
		float[] resultV = new float[newObj.length];
		for (int i = 0; i < newObj.length; i += 4) {
			Matrix.multiplyMV(resultV, i, resultM, 0, newObj, i);

		}
		return resultV;
	}
    public static float[] getXY(float[] corners, float[] cp) {
        float[] result = new float[4];

        float[] tl = new float[3];
        float[] bl = new float[3];
        float[] tr = new float[3];

        tl[0] = corners[0];
        tl[1] = corners[1];
        tl[2] = corners[2];

        bl[0] = corners[4];
        bl[1] = corners[5];
        bl[2] = corners[6];

        tr[0] = corners[8];
        tr[1] = corners[9];
        tr[2] = corners[10];

        float cw = tr[0] - tl[0];
        float rx = cp[0] - tl[0];

        float ch = tl[1] - bl[1];
        float ry = tl[1] - cp[1];
        Log.d("Debug: ", String.format("cw %f, rx %f, ch %f, ry %f", cw, rx, ch, ry));
        result[0] = rx;
        result[1] = ry;
        result[2] = cw;
        result[3] = ch;
        return result;
    }
	public static void mv(float[] result, int ro, float[] m, float[] v) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result[i + ro] =  v[i] * m[4*j + i];
            }
        }
    }
	public static void simulateTouch(WebView view, float rx, float ry, float cw, float ch) {
		/*// Obtain MotionEvent object
		long downTime = SystemClock.uptimeMillis();
		long eventTime = SystemClock.uptimeMillis() + 100;
		// List of meta states found here: developer.android.com/reference/android/view/KeyEvent.html#getMetaState()
		int metaState = 0;
        view.setClickable(true);
		MotionEvent motionEvent = MotionEvent.obtain(
		    downTime, 
		    eventTime, 
		    MotionEvent.ACTION_MOVE,
		    x, 
		    y,
		    metaState
		);*/
        String sx = Float.toString(rx);
        String sy = Float.toString(ry);
        String scw = Float.toString(cw);
        String sch = Float.toString(ch);
        String js = "javascript:(function () {\n" +
                "    var rx = "+ sx + ",ry=" + sy +", cw = " + scw + ", ch=" + sch + "; var x = rx * window.innerHeight / cw, y = ry * window.innerWidth / ch;\n" +
                "    var clickEvent= document.createEvent('MouseEvents');\n" +
                "    clickEvent.initMouseEvent(\n" +
                "    'click', true, true, window, 0,\n" +
                "    0, 0, x, y, false, false,\n" +
                "    false, false, 0, null\n" +
                "    );\n" +
                "    document.elementFromPoint(x, y).dispatchEvent(clickEvent);\n" +
                "})()";
        Log.d("JS String: ", js);
        view.loadUrl(js);

        //view.dispatchTouchEvent(motionEvent);
	}

    
}
