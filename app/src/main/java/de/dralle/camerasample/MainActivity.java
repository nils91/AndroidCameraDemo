package de.dralle.camerasample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final int CAMERA_PERMISSION_REQUEST = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CameraManager cm = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                try {
                    cm.openCamera(backFacingCamId, camOpenCallback, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private TextureView targetView;
    private SurfaceTexture targetTexture;
    private CameraDevice camera;
    private String backFacingCamId;
    private CameraDevice.StateCallback camOpenCallback;
    private CaptureRequest.Builder previewRequestBuilder;
    private CameraCaptureSession previewSession;
    private CameraCaptureSession.CaptureCallback previewCaptureCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        final Context context=this;
        targetView = (TextureView) findViewById(R.id.preview);
        targetView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.i(TAG,"Target ready: "+width+"x"+height);
                backFacingCamId = findBackFacingCamId(context);
                CameraDevice backFacingCamera = tryGetCameraDevice(context, backFacingCamId, 20000);
                camera=backFacingCamera;
                try {
                    previewRequestBuilder=backFacingCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                } catch (CameraAccessException e) {
                    Log.e(TAG,"Couldnt build builder",e);
                }
                previewRequestBuilder.addTarget(new Surface(surface));
                tryConfigureCaptureSession(backFacingCamera,new Surface(surface),20000);
                runCaptureSessions();

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(camera!=null){
            camera.close();
        }
    }

    private void runCaptureSessions(){
        previewCaptureCallback=new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
                Log.i(TAG,"Preview capture started");
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                Log.i(TAG,"Preview capture completed");
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.e(TAG,"Preview capture session failed: "+failure.getReason());
            }
        };
        HandlerThread runCapSessionEventDispatcher=new HandlerThread(TAG+"_runcapturesession");
        runCapSessionEventDispatcher.start();
        try {
            previewSession.setRepeatingRequest(previewRequestBuilder.build(),previewCaptureCallback,new Handler(runCapSessionEventDispatcher.getLooper()));
        } catch (CameraAccessException e) {
            Log.e(TAG,"Starting preview capture session failed",e);
        }

    }

    private void tryConfigureCaptureSession(CameraDevice camera, Surface target, int timeout){
        List<Surface> targets=new ArrayList<>();
        targets.add(target);
        tryConfigureCaptureSession(camera,targets,timeout);
    }
    private void tryConfigureCaptureSession(CameraDevice camera, List<Surface> targets, int timeout){
        final boolean[] configureSuccess = {false};
        CameraCaptureSession.StateCallback captureStateCallback=new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.i(TAG,"CaptureSession configured");
                previewSession=session;
                configureSuccess[0] =true;
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Log.i(TAG,"CaptureSession configure failed");
            }
        };
        try {
            HandlerThread capSessionEventDispatcher=new HandlerThread(TAG+"_confcapturesession");
            capSessionEventDispatcher.start();
            camera.createCaptureSession(targets,captureStateCallback,new Handler(capSessionEventDispatcher.getLooper()));
            for(int i=0;i<timeout;i++){
                if(configureSuccess[0]){
                    break;
                }
                Thread.sleep(1);
            }
            if(!configureSuccess[0]){
                Log.e(TAG,"Create CaptureSession Failed. Timeout");
            }

        } catch (CameraAccessException e) {
            Log.e(TAG,"Create CaptureSession Failed",e);
        } catch (InterruptedException e) {
            Log.e(TAG,"Create CaptureSession Failed",e);
        }
    }

    private CameraDevice tryGetCameraDevice(Context context, String camId, int timeout) {
        CameraManager cm = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        final CameraDevice[] cam = {null};
        camOpenCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cam[0] = camera;
                Log.i(TAG,"Camera opened");
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {

                Log.i(TAG,"Camera closed");
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {

                Log.e(TAG,"Camera error");
            }
        };
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION_REQUEST);
            }else{
                HandlerThread camerOpenEventDispatcher=new HandlerThread(TAG+"_camera");
                camerOpenEventDispatcher.start();
                cm.openCamera(camId, camOpenCallback, new Handler(camerOpenEventDispatcher.getLooper()));
                Log.i(TAG,"Attempting to open camera");
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        for(int i=0;i<timeout;i++){
            if(cam[0]!=null){
                break;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(cam[0]==null){
            Log.e(TAG,"Camera open timeout");
        }
        return cam[0];
    }

    private String findBackFacingCamId(Context context){
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String[] camIds = new String[0];
        try {
            camIds = cm.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        String backFacingCamId = null;
        for (String candiddateId : camIds) {
            CameraCharacteristics characteristics = null;
            try {
                characteristics = cm.getCameraCharacteristics(candiddateId);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK) {
                backFacingCamId = candiddateId;
            }
        }
        if(backFacingCamId==null){
            Log.e(TAG,"No back cam");
        }else{
            Log.i(TAG,"Back cam id: "+backFacingCamId );
        }
        return backFacingCamId;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
