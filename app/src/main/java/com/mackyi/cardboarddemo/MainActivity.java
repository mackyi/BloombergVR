package com.mackyi.cardboarddemo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Arrays;
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

/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {

    private static final String TAG = "MainActivity";

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
    private int mPositionParam;
    private int mPositionParam2;
    private int mNormalParam;
    private int mColorParam;
    private int mColorParam2;
    private int mModelViewProjectionParam;
    private int mLightPosParam;
    private int mModelViewParam;
    private int mModelParam;
    private int mIsFloorParam;
    private int mUVParam;

    private float[] mModelCube;
    private float[] mCamera;
    private float[] mView;
    private float[] mHeadView;
    private float[] mModelViewProjection;
    private float[] mModelView;

    private float[] mModelFloor;

    private int mScore = 0;
    private float mObjectDistance = 12f;
    private float mFloorDepth = 20f;

    private Vibrator mVibrator;

    private CardboardOverlayView mOverlayView;

    private WebView mWebView;
    private int mModelViewProjectionParam2;

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

        mModelCube = new float[16];
        mCamera = new float[16];
        mView = new float[16];
        mModelViewProjection = new float[16];
        mModelView = new float[16];
        mModelFloor = new float[16];
        mHeadView = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Pull the magnet when you find an object.");

        mWebView = new CustomWebView( this );
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.loadUrl("http://www.reddit.com");

        addContentView( mWebView, new ViewGroup.LayoutParams( TEXTURE_WIDTH, TEXTURE_HEIGHT ) );
        // new Surface( surfaceTexture );


    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
        int glSurfaceTex = createSurfaceTexture(TEXTURE_WIDTH, TEXTURE_HEIGHT);
        surfaceTexture = new SurfaceTexture( glSurfaceTex );
        surfaceTexture.setDefaultBufferSize( TEXTURE_WIDTH, TEXTURE_HEIGHT );
        surface = new Surface( surfaceTexture);
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

        mGlProgram = GLES20.glCreateProgram();
        mGlProgram2 = GLES20.glCreateProgram();
        GLES20.glAttachShader(mGlProgram, vertexShader);
        GLES20.glAttachShader(mGlProgram, gridShader);
        GLES20.glLinkProgram(mGlProgram);

        GLES20.glAttachShader(mGlProgram2, webVertexShader);
        GLES20.glAttachShader(mGlProgram2, textureShader);
        GLES20.glLinkProgram(mGlProgram2);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Object first appears directly in front of user
        Matrix.setIdentityM(mModelCube, 0);
        Matrix.translateM(mModelCube, 0, 0, 0, -mObjectDistance);

        Matrix.setIdentityM(mModelFloor, 0);
        Matrix.translateM(mModelFloor, 0, 0, -mFloorDepth, 0); // Floor appears below user

        int glSurfaceTex = createSurfaceTexture(TEXTURE_WIDTH, TEXTURE_HEIGHT);
        surfaceTexture = new SurfaceTexture( glSurfaceTex );
        surfaceTexture.setDefaultBufferSize( TEXTURE_WIDTH, TEXTURE_HEIGHT );
        surface = new Surface( surfaceTexture);

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
        mLightPosParam = GLES20.glGetUniformLocation(mGlProgram, "u_LightPos");
        mModelViewParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVMatrix");
        mModelParam = GLES20.glGetUniformLocation(mGlProgram, "u_Model");
        mIsFloorParam = GLES20.glGetUniformLocation(mGlProgram, "u_IsFloor");

        // Build the Model part of the ModelView matrix.
        // Matrix.rotateM(mModelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(mHeadView, 0);

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

        synchronized ( this ) {
            surfaceTexture.updateTexImage(); // Update texture
        }


        mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
        mPositionParam2 = GLES20.glGetAttribLocation(mGlProgram2, "a_Position");
        mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
        mColorParam = GLES20.glGetAttribLocation(mGlProgram, "a_Color");
        mColorParam2 = GLES20.glGetAttribLocation(mGlProgram2, "a_Color");
        mUVParam = GLES20.glGetAttribLocation(mGlProgram2, "a_uv");

        GLES20.glEnableVertexAttribArray(mPositionParam);
        GLES20.glEnableVertexAttribArray(mPositionParam2);
        GLES20.glEnableVertexAttribArray(mNormalParam);
        GLES20.glEnableVertexAttribArray(mColorParam);
        GLES20.glEnableVertexAttribArray(mColorParam2);
        GLES20.glEnableVertexAttribArray(mUVParam);
        checkGLError("mColorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);

        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, mLightPosInWorldSpace, 0);
        GLES20.glUniform3f(mLightPosParam, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1],
                mLightPosInEyeSpace[2]);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelCube, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0, mModelView, 0);

        drawCube();
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
    public void drawCube() {
        GLES20.glUseProgram(mGlProgram2);
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

        if (isLookingAtObject()) {
            GLES20.glVertexAttribPointer(mColorParam2, 4, GLES20.GL_FLOAT, false,
                    0, mCubeFoundColors);
        } else {
            GLES20.glVertexAttribPointer(mColorParam2, 4, GLES20.GL_FLOAT, false,
                    0, mCubeColors);
        }
        GLES20.glVertexAttribPointer(mUVParam, 2, GLES20.GL_FLOAT, false, 0, mCubeUVs);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
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

    /**
     * Increment the score, hide the object, and give feedback if the user pulls the magnet while
     * looking at the object. Otherwise, remind the user what to do.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");
        float[] obj = new float[9];
        for (int i = 0; i < obj.length; i++) {
            obj[i] = DATA.CUBE_COORDS[i];
        }

        float[] center = {0,0,0};
        float[] mOwnView = new float[16];
        Matrix.multiplyMM(mOwnView, 0, mView, 0, mModelCube, 0); // don't need to map to eye frame
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
        float[] xy = Utils.getXY(untranslated, point);

        Log.d("click pos: ", String.format("%f, %f, %f, %f", xy[0], xy[1], xy[2], xy[3]));
        Utils.simulateTouch(mWebView, xy[0], xy[1], xy[2], xy[3]);
        mVibrator.vibrate(50);
    }

    /**
     * Find a new random position for the object.
     * We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
     */
    private void hideObject() {
        float[] rotationMatrix = new float[16];
        float[] posVec = new float[4];

        // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
        // the object's distance from the user.
        float angleXZ = (float) Math.random() * 180 + 90;
        //Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
        float oldObjectDistance = mObjectDistance;
        mObjectDistance = (float) Math.random() * 15 + 5;
        float objectScalingFactor = mObjectDistance / oldObjectDistance;
        Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor, objectScalingFactor);
        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, mModelCube, 12);

        // Now get the up or down angle, between -20 and 20 degrees
        float angleY = (float) Math.random() * 80 - 40; // angle in Y plane, between -40 and 40
        angleY = (float) Math.toRadians(angleY);
        float newY = (float)Math.tan(angleY) * mObjectDistance;

        Matrix.setIdentityM(mModelCube, 0);
        Matrix.translateM(mModelCube, 0, posVec[0], newY, posVec[2]);
    }


    public Tab getTab(ArrayList<Tab> tabs, float[] eye, float[] centerOfView) {
        if (tabs == null || tabs.size() == 0) {
            return null;
        }
        float best_angle = 1;
        int best_tab = 0;
        float[] mOwnView = new float[16];
        float[] centerOfCube = new float[4];
        float[] cubeStart = {0,0,0};
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

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     * @return
     */
    private boolean isLookingAtObject() {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

        // Convert object space to camera space. Use the headView from onNewFrame.
        Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelCube, 0);
        Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);

        float pitch = (float)Math.atan2(objPositionVec[1], -objPositionVec[2]);
        float yaw = (float)Math.atan2(objPositionVec[0], -objPositionVec[2]);


        return (Math.abs(pitch) < PITCH_LIMIT) && (Math.abs(yaw) < YAW_LIMIT);
    }

    private final int TEXTURE_WIDTH = 1920;
    private final int TEXTURE_HEIGHT = 1920;

    private Surface surface = null;
    private SurfaceTexture surfaceTexture = null;

    public class CustomWebView extends WebView {
        public CustomWebView(Context context) {
            super(context);

            setWebChromeClient(new WebChromeClient(){});
            setWebViewClient(new WebViewClient());

            setLayoutParams(new ViewGroup.LayoutParams(TEXTURE_WIDTH, TEXTURE_HEIGHT));
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

    int createSurfaceTexture(final int width, final int height ) {
        int[] glTextures = new int[1];

        GLES10.glGenTextures(1, glTextures, 0);

        int glTexture = glTextures[0];

        if ( glTexture > 0 ) {
            GLES10.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, glTexture );

            // Notice the use of GL_TEXTURE_2D for texture creation
            GLES10.glTexImage2D( GL10.GL_TEXTURE_2D, 0, GL10.GL_RGB, width, height, 0, GL10.GL_RGB, GL10.GL_UNSIGNED_BYTE, null );

            GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE );

            GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE );

            GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST );
            GLES10.glTexParameterx( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST );

            GLES10.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0 );

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
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }



}