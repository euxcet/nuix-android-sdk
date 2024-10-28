// IAudioService.aidl
package com.hcifuture.producer;

// Declare any non-default types here with import statements

interface IAudioService {
    void startRecord(String savedFile);
    void stopRecord();
}