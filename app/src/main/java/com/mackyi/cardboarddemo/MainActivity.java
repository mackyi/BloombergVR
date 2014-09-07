package com.mackyi.cardboarddemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.vrtoolkit.cardboard.*;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;


/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer, SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private static final String TAG = "MainActivity";

    private static final int MAX_TABS = 6;
    private static final int SCROLL_SPEED = 100;


    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.3f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

    // We keep the light always position just above the user.
    private final float[] mLightPosInWorldSpace = new float[] {0.0f, 2.0f, 0.0f, 1.0f};
    private final float[] mLightPosInEyeSpace = new float[4];

    private static final String US_LANGUAGE_STD = "en-US";
    private static final int RESULT_SPEECH = 1;
    private static final int REQUEST_CODE = 1234;

    private static final int COORDS_PER_VERTEX = 3;

    private final WorldLayoutData DATA = new WorldLayoutData();

    private FloatBuffer mFloorVertices;
    private FloatBuffer mFloorColors;
    private FloatBuffer mFloorNormals;

    private FloatBuffer mCubeVertices;
    private FloatBuffer mCubeColors;
    private FloatBuffer mCubeFoundColors;
    private FloatBuffer mCubeNormals;
    private FloatBuffer mCubeUVs;

    private int mGlProgram;
    private int mGlProgram2;
    private int mGlProgram3;
    private int mPositionParam;
    private int mPositionParam2;
    private int mPositionParam3;
    private int mNormalParam;
    private int mColorParam;
    private int mColorParam2;
    private int mModelViewProjectionParam;
    private int mLightPosParam;
    private int mModelViewParam;
    private int mModelParam;
    private int mIsFloorParam;
    private int mUVParam;

    private float[] mCamera;
    private float[] mView;
    private float[] mHeadView;
    private float[] mModelViewProjection;
    private float[] mModelView;

    private float[] mModelFloor;

    private int mScore = 0;
    private float mObjectDistance = 14f;
    private float mFloorDepth = 20f;

    private Vibrator mVibrator;

    private CardboardOverlayView mOverlayView;

    private int mModelViewProjectionParam2;

    private int[] textures; //  = new int[MAX_TABS];

    private boolean[] tabExists = new boolean[MAX_TABS];
    private FloatBuffer mSearchVertices;
    private FloatBuffer searchUVs;
    private int mUVParam2;
    private int mModelViewProjectionParam3;

    private Tab bestTab;

    private Bitmap searchBMap;

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("Sensor val: ", Float.toString(event.values[0]));
        if (Math.abs(event.values[0]) > 20) {
            addNewTab();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public enum ScrollStatus{
        MIDDLE,
        ABOVE,
        BELOW
    };

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader
     * @param type The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return
     */
    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     * @param func
     */
    private static void checkGLError(String func) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, func + ": glError " + error);
            throw new RuntimeException(func + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.common_ui);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);


        mCamera = new float[16];
        mView = new float[16];
        mModelViewProjection = new float[16];
        mModelView = new float[16];
        mModelFloor = new float[16];
        mHeadView = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Pull the magnet to load a new tab.");

//        tabs.add(new Tab(this));
//        tabs.get(0).setTextures(createSurfaceTexture(TEXTURE_WIDTH, TEXTURE_HEIGHT, 0));
//        tabs.get(0).mWebView.loadUrl("http://www.reddit.com");
//        addContentView(tabs.get(0).mWebView, new ViewGroup.LayoutParams( TEXTURE_WIDTH, TEXTURE_HEIGHT ) );

        // new Surface( surfaceTexture );
        /**
         * Attach our sensors
         */

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mSensor, mSensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
//        int glSurfaceTex = createSurfaceTexture(TEXTURE_WIDTH, TEXTURE_HEIGHT);
//        surfaceTexture = new SurfaceTexture( glSurfaceTex );
//        surfaceTexture.setDefaultBufferSize( TEXTURE_WIDTH, TEXTURE_HEIGHT );
//        surface = new Surface( surfaceTexture);
    }

    /**
     * Creates the buffers we use to store information about the 3D world. OpenGL doesn't use Java
     * arrays, but rather needs data in a format it can understand. Hence we use ByteBuffers.
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(DATA.CUBE_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        mCubeVertices = bbVertices.asFloatBuffer();
        mCubeVertices.put(DATA.CUBE_COORDS);
        mCubeVertices.position(0);

        ByteBuffer bbSearchVertices = ByteBuffer.allocateDirect(DATA.SEARCH_COORDS.length * 4);
        bbSearchVertices.order(ByteOrder.nativeOrder());
        mSearchVertices = bbSearchVertices.asFloatBuffer();
        mSearchVertices.put(DATA.SEARCH_COORDS);
        mSearchVertices.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(DATA.CUBE_COLORS.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        mCubeColors = bbColors.asFloatBuffer();
        mCubeColors.put(DATA.CUBE_COLORS);
        mCubeColors.position(0);

        ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(DATA.CUBE_FOUND_COLORS.length * 4);
        bbFoundColors.order(ByteOrder.nativeOrder());
        mCubeFoundColors = bbFoundColors.asFloatBuffer();
        mCubeFoundColors.put(DATA.CUBE_FOUND_COLORS);
        mCubeFoundColors.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(DATA.CUBE_NORMALS.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        mCubeNormals = bbNormals.asFloatBuffer();
        mCubeNormals.put(DATA.CUBE_NORMALS);
        mCubeNormals.position(0);

        ByteBuffer bbUVs = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_UV.length * 4);
        bbUVs.order(ByteOrder.nativeOrder());
        mCubeUVs = bbUVs.asFloatBuffer();
        mCubeUVs.put(DATA.CUBE_UV);
        mCubeUVs.position(0);

        ByteBuffer bbSearchUVs = ByteBuffer.allocateDirect(WorldLayoutData.SEARCH_UVS.length * 4);
        bbUVs.order(ByteOrder.nativeOrder());
        searchUVs = bbSearchUVs.asFloatBuffer();
        searchUVs.put(DATA.SEARCH_UVS);
        searchUVs.position(0);

        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(DATA.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        mFloorVertices = bbFloorVertices.asFloatBuffer();
        mFloorVertices.put(DATA.FLOOR_COORDS);
        mFloorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(DATA.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        mFloorNormals = bbFloorNormals.asFloatBuffer();
        mFloorNormals.put(DATA.FLOOR_NORMALS);
        mFloorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(DATA.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        mFloorColors = bbFloorColors.asFloatBuffer();
        mFloorColors.put(DATA.FLOOR_COLORS);
        mFloorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
        int webVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.web_vertex);
        int textureShader=  loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.web_texture);
        int imageShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.simpler_image_fragment);

        mGlProgram = GLES20.glCreateProgram();
        mGlProgram2 = GLES20.glCreateProgram();
        mGlProgram3 = GLES20.glCreateProgram();
        GLES20.glAttachShader(mGlProgram, vertexShader);
        GLES20.glAttachShader(mGlProgram, gridShader);
        GLES20.glLinkProgram(mGlProgram);

        GLES20.glAttachShader(mGlProgram2, webVertexShader);
        GLES20.glAttachShader(mGlProgram2, textureShader);
        GLES20.glLinkProgram(mGlProgram2);

        GLES20.glAttachShader(mGlProgram3, webVertexShader);
        GLES20.glAttachShader(mGlProgram3, imageShader);
        GLES20.glLinkProgram(mGlProgram3);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        createTextures(MAX_TABS+1);


        searchBMap = BitmapFactory.decodeResource(getResources(), R.drawable.blackbox);
        createSurfaceTexture(searchBMap.getWidth(), searchBMap.getHeight(), MAX_TABS);
        // Object first appears directly in front of user
//        Matrix.setIdentityM(tabs.get(0).mModelCube, 0);
//        Matrix.translateM(tabs.get(0).mModelCube, 0, 0, 0, -mObjectDistance);

        Matrix.setIdentityM(mModelFloor, 0);
        Matrix.translateM(mModelFloor, 0, 0, -mFloorDepth, 0); // Floor appears below user

        checkGLError("onSurfaceCreated");
    }

    /**
     * Converts a raw text file into a string.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return
     */
    private String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        GLES20.glUseProgram(mGlProgram);

        mModelViewProjectionParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVP");
        mModelViewProjectionParam2 = GLES20.glGetUniformLocation(mGlProgram2, "u_MVP");
        mModelViewProjectionParam3 = GLES20.glGetUniformLocation(mGlProgram3, "u_MVP");
        mLightPosParam = GLES20.glGetUniformLocation(mGlProgram, "u_LightPos");
        mModelViewParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVMatrix");
        mModelParam = GLES20.glGetUniformLocation(mGlProgram, "u_Model");
        mIsFloorParam = GLES20.glGetUniformLocation(mGlProgram, "u_IsFloor");

        // Build the Model part of the ModelView matrix.
        // Matrix.rotateM(mModelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(mHeadView, 0);

        for (int i = 0; i < tabs.size(); i++) {
            ScrollStatus status = scrollStatusForTab(i);
            if(status == ScrollStatus.BELOW){
                tabs.get(i).mWebView.setScrollSpeed(-SCROLL_SPEED);
            } else if(status == ScrollStatus.ABOVE){
                tabs.get(i).mWebView.setScrollSpeed(SCROLL_SPEED);
            } else {
                tabs.get(i).mWebView.setScrollSpeed(0);
            }
        }
        checkGLError("onReadyToDraw");
    }

    /**
     * Draws a frame for an eye. The transformation for that eye (from the camera) is passed in as
     * a parameter.
     * @param transform The transformations to apply to render this eye.
     */
    @Override
    public void onDrawEye(EyeTransform transform) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        for(int i = 0; i < tabs.size(); i++){
            synchronized(tabs.get(i)){
                if(tabs.get(i).texture != null) {
                    tabs.get(i).texture.updateTexImage(); // Update texture
                }
            }
        }


        mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
        mPositionParam2 = GLES20.glGetAttribLocation(mGlProgram2, "a_Position");
        mPositionParam3 = GLES20.glGetAttribLocation(mGlProgram3, "a_Position");
        mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
        mColorParam = GLES20.glGetAttribLocation(mGlProgram, "a_Color");
        mColorParam2 = GLES20.glGetAttribLocation(mGlProgram2, "a_Color");
        mUVParam = GLES20.glGetAttribLocation(mGlProgram2, "a_uv");
        mUVParam2 = GLES20.glGetAttribLocation(mGlProgram3, "a_uv");

        GLES20.glEnableVertexAttribArray(mPositionParam);
        GLES20.glEnableVertexAttribArray(mPositionParam2);
        GLES20.glEnableVertexAttribArray(mPositionParam3);
        GLES20.glEnableVertexAttribArray(mNormalParam);
        GLES20.glEnableVertexAttribArray(mColorParam);
        GLES20.glEnableVertexAttribArray(mColorParam2);
        GLES20.glEnableVertexAttribArray(mUVParam);
        GLES20.glEnableVertexAttribArray(mUVParam2);
        checkGLError("mColorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);

        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, mLightPosInWorldSpace, 0);
        GLES20.glUniform3f(mLightPosParam, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1],
                mLightPosInEyeSpace[2]);

        for(int i = 0; i < tabs.size(); i++){
            synchronized(tabs.get(i)){
                // Build the ModelView and ModelViewProjection matrices
                // for calculating cube position and light.
                Matrix.multiplyMM(mModelView, 0, mView, 0, tabs.get(i).mModelCube, 0);
                Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0, mModelView, 0);

                drawCube(tabs.get(i));
            }
        }
        // Set mModelView for the floor, so we draw floor in the correct location
        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelFloor, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0,
                mModelView, 0);
        drawFloor (transform.getPerspective());
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Draw the cube. We've set all of our transformation matrices. Now we simply pass them into
     * the shader.
     */
    public void drawCube(Tab tab) {
        GLES20.glUseProgram(mGlProgram2);
        GLES10.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tab.textureInt);

//        // This is not the floor!
//        GLES20.glUniform1f(mIsFloorParam, 0f);
//
//        // Set the Model in the shader, used to calculate lighting
//        GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelCube, 0);
//
//        // Set the ModelView in the shader, used to calculate lighting
//        GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, mModelView, 0);

        // Set the position of the cube
        GLES20.glVertexAttribPointer(mPositionParam2, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, mCubeVertices);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(mModelViewProjectionParam2, 1, false, mModelViewProjection, 0);

//        // Set the normal posit/ions of the cube, again for shading
//        GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT,
//                false, 0, mCubeNormals);

//        if (isLookingAtObject()) {
//            GLES20.glVertexAttribPointer(mColorParam2, 4, GLES20.GL_FLOAT, false,
//                    0, mCubeFoundColors);
//        } else {
//            GLES20.glVertexAttribPointer(mColorParam2, 4, GLES20.GL_FLOAT, false,
//                    0, mCubeColors);
//        }
        GLES20.glVertexAttribPointer(mUVParam, 2, GLES20.GL_FLOAT, false, 0, mCubeUVs);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);


        GLES20.glUseProgram(mGlProgram3);
        GLES20.glUniformMatrix4fv(mModelViewProjectionParam3, 1, false, mModelViewProjection, 0);
        GLES20.glVertexAttribPointer(mPositionParam3, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, mSearchVertices);
        GLES20.glVertexAttribPointer(mUVParam2, 2, GLES20.GL_FLOAT, false, 0, searchUVs);
        // GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        checkGLError("Drawing cube");
    }

    /**
     * Draw the floor. This feeds in data for the floor into the shader. Note that this doesn't
     * feed in data about position of the light, so if we rewrite our code to draw the floor first,
     * the lighting might look strange.
     */
    public void drawFloor(float[] perspective) {

        GLES20.glUseProgram(mGlProgram);
        // This is the floor!
        GLES20.glUniform1f(mIsFloorParam, 1f);

        // Set ModelView, MVP, position, normals, and color
        GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelFloor, 0);
        GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, mModelView, 0);
        GLES20.glUniformMatrix4fv(mModelViewProjectionParam, 1, false, mModelViewProjection, 0);
        GLES20.glVertexAttribPointer(mPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, mFloorVertices);
        GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT, false, 0, mFloorNormals);
        GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false, 0, mFloorColors);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        checkGLError("drawing floor");
    }
    public Tab getTab(ArrayList<Tab> tabs, float[] eye, float[] centerOfView) {
        if (tabs == null || tabs.size() == 0) {
            return null;
        }
        float best_angle = 1;
        int best_tab = 0;
        float[] mOwnView = new float[16];
        float[] centerOfCube = new float[4];
        float[] cubeStart = {0,0,0,1};
        float[] centerOfCube_3 = new float[3];
        for (int i = 0; i < tabs.size(); i++) {
            Matrix.multiplyMM(mOwnView, 0, mView, 0, tabs.get(i).mModelCube, 0);
            Matrix.multiplyMV(centerOfCube, 0, mOwnView, 0, cubeStart, 0);
            centerOfCube_3[0] = centerOfCube[0];
            centerOfCube_3[1] = centerOfCube[1];
            centerOfCube_3[2] = centerOfCube[2];
            float angle = Main.getCos(Main.subtract(centerOfView, eye), centerOfCube_3);
            if (angle < best_angle) {
                best_tab = i;
                best_angle = angle;
            }
        }
        return tabs.get(best_tab);
    }
    public boolean inBounds(Tab t, float[] eye, float[] centerOfView) {
        float[] viewLine = Main.subtract(centerOfView, eye);
        float[] obj = new float[9];
        for (int i = 0; i < obj.length; i++) {
            obj[i] = DATA.CUBE_COORDS[i];
        }
        float[] mOwnView = new float[16];
        float[] center = {0,0,0};
        Matrix.multiplyMM(mOwnView, 0, mView, 0, t.mModelCube, 0); // don't need to map to eye frame
        float[] transObj = Utils.getTransformedObj(mOwnView, obj, center);
        float[] line = new float[6];
        line[0] = eye[0];
        line[1] = eye[1];
        line[2] = eye[2];
        line[3] = centerOfView[0];
        line[4] = centerOfView[1];
        line[5] = centerOfView[2];
        float[] p = Main.getIntersection(transObj, line);
        if (p==null)
            return false;
        float[] utM = new float[16];
        Matrix.invertM(utM, 0, mOwnView, 0);
        float[] p2 = new float[4];
        for (int i = 0; i < p.length; i++) {
            p2[i] = p[i];
        }
        p2[3] = 1;

        float[] point = new float[4];
        float[] untranslated = new float[12];
        for(int i = 0; i < untranslated.length; i+=4) {
            Matrix.multiplyMV(untranslated, i, utM, 0, transObj, i);
        }
        Matrix.multiplyMV(untranslated, 0, utM, 0, transObj, 0);

        Matrix.multiplyMV(point, 0, utM, 0, p2, 0);

        String str = String.format("(%f, %f, %f", point[0], point[1], point[2]);
        Log.i("CubeBound Pos:", str);
        //Utils.simulateTouch(this.getCardboardView(), pointX, pointY);
        // Always give user feedback
        float[] xy = Utils.getXY(untranslated, point);

        Log.d("Bound pos: ", String.format("%f, %f, %f, %f", xy[0], xy[1], xy[2], xy[3]));

        // safe boundary of 1f
        if (xy[1] < -1f || xy[1] > xy[3] + 1f) {
            return false;
        }
        return true;
    }
    /**
     * Increment the score, hide the object, and give feedback if the user pulls the magnet while
     * looking at the object. Otherwise, remind the user what to do.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");
        float[] eye = {0, 0, CAMERA_Z};
        float[] centerOfView = {0,0,0};
        if(tabs.size() == 0){
            addNewTab();
            return;
        }
        bestTab = getTab(tabs, eye, centerOfView);
        if(inBounds(bestTab, eye, centerOfView)) {
            processClick(bestTab);
        } else {
            Log.d("Speech", "SPEECH MODE DUMMY OUTPUT");
            startVoiceRecognitionActivity();
            // put your speech call here
        }
        /*if (!isLookingAtObject()) {
//            mScore++;
//            mOverlayView.show3DToast("Found it! Look around for another one.\nScore = " + mScore);
//            hideObject();
            mOverlayView.show3DToast("Clicked!");
            if(tabs.size() < MAX_TABS){
                addNewTab();
            }
        } */
        // Always give user feedback
        mVibrator.vibrate(50);
    }

    private void processClick(Tab tab) {
        float[] obj = new float[9];
        for (int i = 0; i < obj.length; i++) {
            obj[i] = DATA.CUBE_COORDS[i];
        }
        float[] center = {0,0,0};
        float[] mOwnView = new float[16];
        Matrix.multiplyMM(mOwnView, 0, mView, 0, tab.mModelCube, 0); // don't need to map to eye frame
        float[] transObj = Utils.getTransformedObj(mOwnView, obj, center);

        Log.i("Points: ", Arrays.toString(transObj));
        float[] line = {0f, 0f, CAMERA_Z, 0f, 0f, 0f};
        float[] p = Main.getIntersection(transObj, line);
        if (p==null)
            return;
        float[] utM = new float[16];
        Matrix.invertM(utM, 0, mOwnView, 0);
        float[] p2 = new float[4];
        for (int i = 0; i < p.length; i++) {
            p2[i] = p[i];
        }
        p2[3] = 1;
        float[] point = new float[4];
        float[] untranslated = new float[12];
        for(int i = 0; i < untranslated.length; i+=4) {
            Matrix.multiplyMV(untranslated, i, utM, 0, transObj, i);
        }
        Matrix.multiplyMV(untranslated, 0, utM, 0, transObj, 0);
        String str2 = String.format("(%f, %f, %f", untranslated[0], untranslated[1], untranslated[2]);
        Log.i("Point Pos: ", str2);

        Matrix.multiplyMV(point, 0, utM, 0, p2, 0);
        if (point != null) {
            String str = String.format("(%f, %f, %f", point[0], point[1], point[2]);
            Log.i("Click Pos:", str);
        }
        //Utils.simulateTouch(this.getCardboardView(), pointX, pointY);
        // Always give user feedback
        Display d = getWindowManager().getDefaultDisplay();
        int w = tab.mWebView.getWidth();
        int h = tab.mWebView.getHeight();
        Log.d("Dim: ", String.format("%d, %d", w, h));
        float[] xy = Utils.getXY(untranslated, point);

        Log.d("click pos: ", String.format("%f, %f, %f, %f", xy[0], xy[1], xy[2], xy[3]));
        Utils.simulateTouch(tab.mWebView, xy[0], xy[1], xy[2], xy[3]);
    }
    /**
     * Find a new random position for the object.
     * We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
     */
//    private void hideObject() {
//        float[] rotationMatrix = new float[16];
//        float[] posVec = new float[4];
//
//        // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
//        // the object's distance from the user.
//        float angleXZ = (float) Math.random() * 180 + 90;
//        Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
//        float oldObjectDistance = mObjectDistance;
//        mObjectDistance = (float) Math.random() * 15 + 5;
//        float objectScalingFactor = mObjectDistance / oldObjectDistance;
//        Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor, objectScalingFactor);
//        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, mModelCube, 12);
//
//        // Now get the up or down angle, between -20 and 20 degrees
//        float angleY = (float) Math.random() * 80 - 40; // angle in Y plane, between -40 and 40
//        angleY = (float) Math.toRadians(angleY);
//        float newY = (float)Math.tan(angleY) * mObjectDistance;
//
//        Matrix.setIdentityM(mModelCube, 0);
//        Matrix.translateM(mModelCube, 0, posVec[0], newY, posVec[2]);
//    }

    private void addNewTab() {
        Tab newTab = new Tab(this);
        newTab.setTextures(createSurfaceTexture(TEXTURE_WIDTH, TEXTURE_HEIGHT, tabs.size()));
        newTab.mWebView.loadUrl("https://www.reddit.com");
        tabs.add(newTab);
        float[] rotationMatrix = new float[16];
        float[] posVec = new float[4];
        Matrix.setIdentityM(newTab.mModelCube, 0);;
        Matrix.translateM(newTab.mModelCube, 0, 0, 0, -mObjectDistance);
        // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
        // the object's distance from the user.

//        float yaw = angleToOriginalObject(newTab.mModelCube);
//        Log.e(TAG, "yaw is " + yaw);
//
//        int pos = Math.round(yaw/60);
//        if(pos == -3) pos = 3;
//        if(pos == -2) pos = 4;
//        if(pos == -1) pos = 5;

//        if(tabExists[pos]){
//            if(!tabExists[(pos+1) % tabExists.length]) {
//                pos = (pos + 1 )% tabExists.length;
//            } else if(!tabExists[(pos + tabExists.length - 1) % tabExists.length]) {
//                pos = (pos + tabExists.length - 1) % tabExists.length;
//            } else {
//                return;
//            }
//        }
//        tabExists[pos] = true;
//        int angle = pos*60;

//        Log.e(TAG, "angle is:" + angle);

        float angleXZ = (float) -60 * (tabs.size() -1); //  * (tabs.size()-1);
        Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, newTab.mModelCube, 12);

        Matrix.setIdentityM(newTab.mModelCube, 0);
        Matrix.translateM(newTab.mModelCube, 0, posVec[0], posVec[1], posVec[2]);

        Matrix.rotateM(newTab.mModelCube, 0, angleXZ, 0f, 1f, 0f);

        addContentView(newTab.mWebView, new ViewGroup.LayoutParams( TEXTURE_WIDTH, TEXTURE_HEIGHT ) );
    }

    private float angleToOriginalObject(float[] mModelCube) {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

        // Convert object space to camera space. Use the headView from onNewFrame.
        // TODO(mackyi): loop over all tabs
        Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelCube, 0);
        Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);
        Log.e(TAG, "Object position: X: " + objPositionVec[0]
                + "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
        float yaw = 180/(float)Math.PI*(float)Math.atan2(objPositionVec[0], -objPositionVec[2]);

        return yaw;
    }
    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     * @return
     */
    private boolean isLookingAtObject() {
        // if(tabs.size() == 0 ) return true;
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

        // Convert object space to camera space. Use the headView from onNewFrame.
        // TODO(mackyi): loop over all tabs
        for(Tab tab: tabs) {
            Matrix.multiplyMM(mModelView, 0, mHeadView, 0, tab.mModelCube, 0);
            Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);

            float pitch = (float)Math.atan2(objPositionVec[1], -objPositionVec[2]);
            float yaw = (float)Math.atan2(objPositionVec[0], -objPositionVec[2]);

            Log.i(TAG, "Object position: X: " + objPositionVec[0]
                    + "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
            Log.i(TAG, "Object Pitch: " + pitch +"  Yaw: " + yaw);

            if((Math.abs(pitch) < PITCH_LIMIT*3) && (Math.abs(yaw) < YAW_LIMIT*13)) {
                return true;
            }
        }
        return false;
    }

    private ScrollStatus scrollStatusForTab(int tab) {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

        Matrix.multiplyMM(mModelView, 0, mHeadView, 0, tabs.get(tab).mModelCube, 0);
        Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);

        float pitch = (float)Math.atan2(objPositionVec[1], -objPositionVec[2]);
        float yaw = (float)Math.atan2(objPositionVec[0], -objPositionVec[2]);

        Log.i(TAG, "Object position: X: " + objPositionVec[0]
                + "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
        Log.i(TAG, "Object Pitch: " + pitch +"  Yaw: " + yaw);

        if( Math.abs(pitch) > PITCH_LIMIT * 2 && (Math.abs(yaw) < YAW_LIMIT*22)) {
            if(objPositionVec[1] > 0) {
                return ScrollStatus.BELOW;
            } else {
                return ScrollStatus.ABOVE;
            }
        }
        return ScrollStatus.MIDDLE;
    }

    private final int TEXTURE_WIDTH = 3000;
    private final int TEXTURE_HEIGHT = 3000;

    public class CustomWebView extends WebView {
        Surface surface;
        int scrollSpeed;
        Handler mHandler;

        public CustomWebView(Context context) {
            super(context);
            getSettings().setJavaScriptEnabled(true);
            String newUA= "Mozilla/5.0 (Windows NT 6.1; rv:27.3) Gecko/20130101 Firefox/27.3";
            getSettings().setUserAgentString(newUA);
            setWebChromeClient(new WebChromeClient() {
            });
            setWebViewClient(new WebViewClient());

            setLayoutParams(new ViewGroup.LayoutParams(TEXTURE_WIDTH, TEXTURE_HEIGHT));
            mHandler = new Handler();
            new Scroller(this).run();
        }

        public void setSurface(Surface surface){
            this.surface = surface;
            this.invalidate();
        }

        public void setScrollSpeed(int scrollSpeed){
            this.scrollSpeed = scrollSpeed;
        }

        private class Scroller implements Runnable {
            CustomWebView webView;

            public Scroller(CustomWebView webView) {
                this.webView = webView;
            }
            public void run()
            {
                int height = (int) Math.floor(webView.getContentHeight() * webView.getScale());
                int webViewHeight = webView.getMeasuredHeight();
                webView.scrollBy(0, webView.scrollSpeed);
//
//                if( scrollSpeed < 0) {
//                    int diff = height - (webView.getScrollY() + webViewHeight);
//                    webView.scrollBy(0, Math.max(diff, webView.scrollSpeed));
//                } else if( scrollSpeed > 0) {
//                    Log.e(TAG, "scrolling up since scrollY is " + webView.getScrollY());
//                    Log.e(TAG, "height is " + height + " and webViewHeight is " + webViewHeight);
//                    webView.scrollBy(0, Math.min(webView.getScrollY(), webView.scrollSpeed));
//                }
                mHandler.postDelayed(this, 100);
            }
        }

        @Override
        protected void onDraw( Canvas canvas ) {
            if ( surface != null ) {
                // Requires a try/catch for .lockCanvas( null )
                try {
                    final Canvas surfaceCanvas = surface.lockCanvas( null ); // Android canvas from surface
                    super.onDraw( surfaceCanvas ); // Call the WebView onDraw targetting the canvas
                    surface.unlockCanvasAndPost( surfaceCanvas ); // We're done with the canvas!
                } catch ( Exception excp ) {
                    excp.printStackTrace();
                }
            }
            // super.onDraw( canvas ); // <- Uncomment this if you want to show the original view
        }
    }

    void createTextures( int numTextures) {
        textures = new int[numTextures];
        GLES10.glGenTextures(numTextures, textures, 0);
        Log.e(TAG, Arrays.toString(textures));
        Log.e(TAG, "Create textures error: " + GLES10.glGetError());
    }

    int createSurfaceTexture(final int width, final int height, int index) {
        Log.e(TAG, "Create surface with array " + Arrays.toString(textures));
        if(textures == null){
            return 0;
        }
        int glTexture =textures[index % textures.length];

        if ( glTexture > 0 ) {
            if(index >= MAX_TABS) {
                GLES10.glBindTexture( GLES10.GL_TEXTURE_2D, glTexture );
            } else {
                GLES10.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, glTexture );
            }

            // Notice the use of GL_TEXTURE_2D for texture creation
            GLES10.glTexImage2D( GL10.GL_TEXTURE_2D, 0, GL10.GL_RGB, width, height, 0, GL10.GL_RGB, GL10.GL_UNSIGNED_BYTE, null );

            if(index < MAX_TABS) {
                GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE );

                GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE );

                GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST );
                GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST );

                GLES10.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0 );
            } else {
                GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, searchBMap, 0);
            }

            GLES10.glTexParameterf(GL10.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_MIN_FILTER,
                    GL10.GL_LINEAR);
            GLES10.glTexParameterf(GL10.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_MAG_FILTER,
                    GL10.GL_LINEAR);
        }
        return glTexture;
    }

    /**
     * Handle the action of the button being clicked
     */
    public void speakButtonClicked(View v)
    {
        startVoiceRecognitionActivity();
    }

    /**
     * Fire an intent to start the voice recognition activity.
     */
    private void startVoiceRecognitionActivity()
    {
        mOverlayView.show3DToast("Waiting for voice input...");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Voice recognition Demo...");
        startActivityForResult(intent, REQUEST_CODE);
    }

    /**
     * Handle the results from the voice recognition activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK)
        {
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            if(matches != null){
                String s = matches.get(0);
                if(s.contains(".")){
                    s = s.replace(" ", "");
                }else{
                    s = s.replace(" ", "+");
                    s = "www.google.com/#q="+s;
                }
                System.out.println(s);
                bestTab.mWebView.loadUrl(s);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    ArrayList<Tab> tabs = new ArrayList<Tab>();

    public class Tab {
        public CustomWebView mWebView;
        public float[] mModelCube;

        public int textureInt;
        public SurfaceTexture texture;
        public Surface surface;

        public Tab(Activity activity){
            mWebView = new CustomWebView(activity);
            mModelCube = new float[16];
        }

        public void setTextures(int textureInt) {

            Log.e(TAG, "Creating tab with textureInt " + textureInt);
            this.textureInt = textureInt;
            this.texture = new SurfaceTexture(textureInt);
            texture.setDefaultBufferSize( TEXTURE_WIDTH, TEXTURE_HEIGHT );
            this.surface = new Surface(texture);
            this.mWebView.setSurface(surface);
        }
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }
}