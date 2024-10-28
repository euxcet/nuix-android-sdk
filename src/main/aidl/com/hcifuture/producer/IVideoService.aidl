// IVideoService.aidl
package com.hcifuture.producer;

// Declare any non-default types here with import statements

interface IVideoService {
     void bindCamera(int cameraLens, in Surface previewSurface, int width, int height);
     void switchCameraLens(int cameraLens);
     void startRecord(String savedFile, boolean withAudio);
     void updatePreview(in Surface previewSurface);
     void stopRecord();
     void unbindCamera();
}