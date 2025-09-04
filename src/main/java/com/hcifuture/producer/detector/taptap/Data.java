package com.hcifuture.producer.detector.taptap;

public abstract class Data {
    public enum DataType {
        BluetoothData,
        SingleBluetoothData,
        IMUData,
        SingleIMUData,
        NonIMUData,
        LocationData,
        WeatherData,
        WifiData,
        SingleWifiData,
        GPSData,
        LogData
    }

    public abstract DataType dataType();
}
