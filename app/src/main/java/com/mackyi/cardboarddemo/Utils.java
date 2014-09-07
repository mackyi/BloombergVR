package com.mackyi.cardboarddemo;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

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
		moveMatrix[3] = center[0];
		moveMatrix[7] = center[1];
		moveMatrix[11] = center[2];
		moveMatrix[15] = 1;
		float[] resultM = new float[16];
		Matrix.multiplyMM(resultM, 0, tMatrix, 0, moveMatrix, 0);
		float[] resultV = new float[object.length];
		for (int i = 0; i < object.length; i += 4) {
			Matrix.multiplyMV(resultV, i, resultM, 0, object, i);
		}
		return resultV;
	}
	
	public static void simulateTouch(View view, float x, float y) {
		// Obtain MotionEvent object
		long downTime = SystemClock.uptimeMillis();
		long eventTime = SystemClock.uptimeMillis() + 100;
		// List of meta states found here: developer.android.com/reference/android/view/KeyEvent.html#getMetaState()
		int metaState = 0;
		MotionEvent motionEvent = MotionEvent.obtain(
		    downTime, 
		    eventTime, 
		    MotionEvent.ACTION_UP, 
		    x, 
		    y, 
		    metaState
		);

		// Dispatch touch event to view
		view.dispatchTouchEvent(motionEvent);
	}

}
