package de.dralle.camerasample;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.support.annotation.NonNull;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

public class CameraRunnable implements Runnable {
    private final static String TAG =CameraRunnable.class.getName();

    public CameraCaptureSession.StateCallback getCaptureSessionCallback() {
        return captureSessionCallback;
    }

    public void setCaptureSessionCallback(CameraCaptureSession.StateCallback captureSessionCallback) {
        this.captureSessionCallback = captureSessionCallback;
    }

    private CameraCaptureSession.StateCallback captureSessionCallback;
    public CameraDevice getDevice() {
        return device;
    }

    public void setDevice(CameraDevice device) {
        this.device = device;
    }

    private CameraDevice device;

    public CameraRunnable() {
        this.previewTargets = new ArrayList<>();
    }

    private List<Surface> previewTargets;

    public void addPreviewTarget(Surface target){
        previewTargets.add(target);
    }
    @Override
    public void run() {
        if(device!=null){
            try {
                device.createCaptureSession(previewTargets,captureSessionCallback,null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
