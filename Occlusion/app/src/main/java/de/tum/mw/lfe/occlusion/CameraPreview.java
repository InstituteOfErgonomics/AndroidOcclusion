package de.tum.mw.lfe.occlusion;

import java.io.IOException;
import java.util.List;

import android.app.Service;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

/*
Copyright (C) 2014  Michael Krause (krause@tum.de), Institute of Ergonomics, Technische Universität München

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

private static final String TAG = "LFEocclusion.CameraPreview";
private SurfaceHolder mSurfaceHolder;
private Camera mCamera;
private Context mContext;
private OccService mParent;

public SurfaceHolder getSurfaceHolder(){
	return mSurfaceHolder;
}

public CameraPreview(Context context, OccService parent, Camera camera) {
    super(context);
    mCamera = camera;
    mParent = parent;
    mContext = context;

    mSurfaceHolder = getHolder();
    mSurfaceHolder.addCallback(this);
    mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    
    
	WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
	LayoutParams params = new WindowManager.LayoutParams(
            	WindowManager.LayoutParams.FILL_PARENT,
            	WindowManager.LayoutParams.FILL_PARENT,
	            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
	            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
	            PixelFormat.TRANSLUCENT); 
	params.alpha = 0;
	//params.x = 0;
	//params.y = 0;
	wm.addView(this, params);
	

	//this.setZOrderOnTop(true);
	//mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);
	//mSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);

    
}

public void surfaceCreated(SurfaceHolder holder) {
    Log.d(TAG, "surfaceCreated() ");
    
    try {
        mCamera.setPreviewDisplay(mSurfaceHolder);
        mCamera.startPreview();
    } catch (Exception e) {
        Log.e(TAG, "Error setting camera preview: " + e.getMessage());
    }
    configs();
}

public void surfaceDestroyed(SurfaceHolder holder) {
    Log.d(TAG, "surfaceDestroyed() ");
    
    if (mCamera != null) {
    	mCamera.stopPreview();
    	mCamera.setPreviewCallback(null);
    	mCamera.release();
    	mCamera = null;
    }
}

public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    Log.d(TAG, "surfaceChanged() ");

    if (mSurfaceHolder.getSurface() == null){return;}

    try {mCamera.stopPreview();} catch (Exception e){}

    

    try {
        mCamera.setPreviewDisplay(mSurfaceHolder);
        mCamera.startPreview();
    } catch (Exception e){
        Log.d(TAG, "Error starting camera preview: " + e.getMessage());
    }
    
    configs();
}

public void configs(){
	Camera.Parameters cparams = mCamera.getParameters();
	//List<int[]> supportedPreviewFpsRange = cparams.getSupportedPreviewFpsRange();
	//cparams.setPreviewFpsRange(supportedPreviewFpsRange.get(0)[0], supportedPreviewFpsRange.get(0)[0]);
	
	//List<Size> supportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
	//int pw = supportedPreviewSizes.get(supportedPreviewSizes.size()-1).width; 
	//int ph = supportedPreviewSizes.get(supportedPreviewSizes.size()-1).height;
	
	Size previewSize = mCamera.getParameters().getPreviewSize();
	int pw = previewSize.width; 
	int ph = previewSize.height;
	cparams.setPreviewSize(pw,ph);//normally max

	//cparams.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
	//cparams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
	//cparams.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
	//cparams.setSceneMode(Camera.Parameters.SCENE_MODE_SPORTS);
	//cparams.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);

	mCamera.setParameters(cparams);
	
	mCamera.stopPreview();
	mCamera.setDisplayOrientation(mParent.mPrefs.getRotation());
	mCamera.startPreview();
	
	
	
	
	int pformat = mCamera.getParameters().getPreviewFormat();
	int bitsPerPixel =  ImageFormat.getBitsPerPixel(pformat);
	int pbytes = bitsPerPixel/8;
	if ((bitsPerPixel%8) !=0) pbytes++;//round up
	int bufferSize = pw * ph * pbytes;
	//Log.i(TAG, ">>>"+Integer.toString(bufferSize));
    for (int i=0; i < 4; i++) {
    	mCamera.addCallbackBuffer(new byte[bufferSize]);
	}	
    mCamera.setPreviewCallbackWithBuffer(mParent);
}

}