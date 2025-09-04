package com.hcifuture.producer.detector.taptap;

import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.util.Log;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TapTapAction {

    public interface Callback {
        void onComplete(Boolean result);
    }


    private String TAG = "TapTapAction";

    public static String ACTION = "action.taptap.action";
    public static String ACTION_UPLOAD = "action.taptap.action.upload";
    public static String ACTION_RECOGNIZED = "action.taptap.action.recognized";

    private long SAMPLINGINTERVALNS = 2500000L;
    private long WINDOW_NS = 160000000L;
    private int size = 64;  // WINDOW_NS / SAMPLINGINTERVALNS

    private boolean gotAcc = false;
    private boolean gotGyro = false;
    protected Highpass3C highpassAcc = new Highpass3C();
    protected Highpass3C highpassGyro = new Highpass3C();
    protected Lowpass3C lowpassAcc = new Lowpass3C();
    protected Lowpass3C lowpassGyro = new Lowpass3C();
    private Resample3C resampleAcc = new Resample3C();
    private Resample3C resampleGyro = new Resample3C();
    protected Slope3C slopeAcc = new Slope3C();
    protected Slope3C slopeGyro = new Slope3C();
    private long syncTime = 0L;
    private float[] xsAcc = new float[size];
    private float[] ysAcc = new float[size];
    private float[] zsAcc = new float[size];
    private float[] xsGyro = new float[size];
    private float[] ysGyro = new float[size];
    private float[] zsGyro = new float[size];
    private long lastTimestamp = 0L;

    private Highpass1C highpassKey = new Highpass1C();
    private Lowpass1C lowpassKey = new Lowpass1C();
    private PeakDetector peakDetectorPositive = new PeakDetector();
    private boolean wasPeakApproaching = true;
    private int seqLength;
    private Deque<Long> tapTimestamps = new ArrayDeque();
    private TfClassifier tflite;

    // filter related
    private boolean flag1 = true;
    private boolean flag2 = false;
    private boolean existTaptapSignal = false;
    private HorizontalFilter horizontalFilter = new HorizontalFilter();
    private static CombinedFilter combinedFilter = new CombinedFilter();

    private ThreadPoolExecutor threadPoolExecutor;
    private boolean isStarted = false;

    // save positive samples in guide
    private boolean inGuide = false;

    private Callback callback;

    public TapTapAction(AssetManager assetManager, String model, Callback callback) {
        this.callback = callback;
        init();
//        tflite = new TfClassifier(new File(ContextActionContainer.getSavePath() + "tap7cls_pixel4.tflite"));
        tflite = new TfClassifier(assetManager, model);
        seqLength = 50;
        threadPoolExecutor = new ThreadPoolExecutor(1, 1, 1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    private void init() {
        lowpassAcc.setPara(1.0F);
        lowpassGyro.setPara(1.0F);
        highpassAcc.setPara(0.05F);
        highpassGyro.setPara(0.05F);
        lowpassKey.setPara(0.2F);
        highpassKey.setPara(0.2F);
        peakDetectorPositive.setMinNoiseTolerate(0.05f);
        peakDetectorPositive.setWindowSize(64);
        inGuide = false;
    }

    private void reset() {
        gotAcc = false;
        gotGyro = false;
        syncTime = 0L;
        Arrays.fill(xsAcc, 0);
        Arrays.fill(ysAcc, 0);
        Arrays.fill(zsAcc, 0);
        Arrays.fill(xsGyro, 0);
        Arrays.fill(ysGyro, 0);
        Arrays.fill(zsGyro, 0);
        lastTimestamp = 0L;
        inGuide = false;
    }

    public synchronized void start() {
        if (isStarted) {
            Log.d(TAG, "Action is already started.");
            return;
        }
        isStarted = true;
        reset();
    }

    public synchronized void stop() {
        if (!isStarted) {
            Log.d(TAG, "Action is already stopped");
            return;
        }
        isStarted = false;
    }

    public void onIMUSensorEvent(SingleIMUData data) {
        // just for horizontal / static cases' record && upload
        horizontalFilter.onSensorChanged(data);
        if (horizontalFilter.passWithDelay(data.getTimestamp()) == -1) {
//            ActionResult actionResult = new ActionResult(ACTION_UPLOAD);
//            actionResult.setReason("Static");
//            for (ActionListener listener : actionListener) {
//                listener.onAction(actionResult);
//            }
        }
        combinedFilter.onSensorChanged(data);
        int tmp2 = combinedFilter.passWithDelay(data.getTimestamp());
        if (tmp2 == -1)
            existTaptapSignal = false;
        else if (tmp2 == 1)
            flag2 = true;
        if (existTaptapSignal && flag1 && flag2) {
//            if (actionListener != null) {
//                for (ActionListener listener: actionListener) {
//                    listener.onAction(new ActionResult(ACTION));
//                    if (inGuide) {
//                        ActionResult actionResult = new ActionResult(ACTION_UPLOAD);
//                        actionResult.setReason("Positive");
//                        listener.onAction(actionResult);
//                    }
//                }
//            }
            existTaptapSignal = false;
            flag2 = false;
        }

        if (data.getType() != Sensor.TYPE_GYROSCOPE && data.getType() != Sensor.TYPE_ACCELEROMETER)
            return;
        if (data.getType() == Sensor.TYPE_ACCELEROMETER) {
            gotAcc = true;
            if (0L == syncTime)
                resampleAcc.init(data.getValues().get(0), data.getValues().get(1), data.getValues().get(2), data.getTimestamp(), SAMPLINGINTERVALNS);
            if (!gotGyro)
                return;
        } else {
            gotGyro = true;
            if (0L == syncTime)
                resampleGyro.init(data.getValues().get(0), data.getValues().get(1), data.getValues().get(2), data.getTimestamp(), SAMPLINGINTERVALNS);
            if (!gotAcc)
                return;
        }
        if (0L == syncTime) {
            syncTime = data.getTimestamp();
            resampleAcc.setSyncTime(data.getTimestamp());
            resampleGyro.setSyncTime(syncTime);
            slopeAcc.init(resampleAcc.getResults().point);
            slopeGyro.init(resampleGyro.getResults().point);
            lowpassAcc.init(new Point3f(0.0F, 0.0F, 0.0F));
            lowpassGyro.init(new Point3f(0.0F, 0.0F, 0.0F));
            highpassAcc.init(new Point3f(0.0F, 0.0F, 0.0F));
            highpassGyro.init(new Point3f(0.0F, 0.0F, 0.0F));
            lowpassKey.init(0.0F);
            highpassKey.init(0.0F);
        } else {
            if (data.getType() == Sensor.TYPE_ACCELEROMETER)
                while(resampleAcc.update(data.getValues().get(0), data.getValues().get(1), data.getValues().get(2), data.getTimestamp()))
                    processAccAndKeySignal();
            else
                while(resampleGyro.update(data.getValues().get(0), data.getValues().get(1), data.getValues().get(2), data.getTimestamp()))
                    processGyro();
            threadPoolExecutor.execute(() -> {
                recognizeTapML(data.getTimestamp());
            });
        }
    }

    private void processAccAndKeySignal() {
        Sample3C sample = resampleAcc.getResults();
        Point3f point1 = slopeAcc.update(sample.point, 2500000.0F / (float)resampleAcc.getInterval());
        Point3f point3 = highpassAcc.update(lowpassAcc.update(point1));
        System.arraycopy(xsAcc, 1, xsAcc, 0, size - 1);
        System.arraycopy(ysAcc, 1, ysAcc, 0, size - 1);
        System.arraycopy(zsAcc, 1, zsAcc, 0, size - 1);
        xsAcc[size - 1] = point3.x;
        ysAcc[size - 1] = point3.y;
        zsAcc[size - 1] = point3.z;
        lastTimestamp = sample.t;
        peakDetectorPositive.update(highpassKey.update(lowpassKey.update(point1.z)));
    }

    private void processGyro() {
        Point3f point = resampleGyro.getResults().point;
        point = highpassGyro.update(lowpassGyro.update(slopeGyro.update(point, 2500000.0F / (float)resampleGyro.getInterval())));
        System.arraycopy(xsGyro, 1, xsGyro, 0, size - 1);
        System.arraycopy(ysGyro, 1, ysGyro, 0, size - 1);
        System.arraycopy(zsGyro, 1, zsGyro, 0, size - 1);
        xsGyro[size - 1] = point.x;
        ysGyro[size - 1] = point.y;
        zsGyro[size - 1] = point.z;
    }

    private ArrayList<Float> getInput(int accIdx, int gyroIdx) {
        ArrayList<Float> res = new ArrayList<>();
        addFeatureData(xsAcc, accIdx, 1, res);
        addFeatureData(ysAcc, accIdx, 1, res);
        addFeatureData(zsAcc, accIdx, 1, res);
        addFeatureData(xsGyro, gyroIdx, 10, res);
        addFeatureData(ysGyro, gyroIdx, 10, res);
        addFeatureData(zsGyro, gyroIdx, 10, res);
        return res;
    }

    private void addFeatureData(float[] list, int startIdx, int scale, List<Float> res) {
        for (int i = 0; i < seqLength; i++) {
            if (i + startIdx >= size)
                res.add(0.0F);
            else
                res.add(scale * list[i + startIdx]);
        }
    }

    private synchronized int checkDoubleTapTiming(long timestamp) {
        Iterator iter = tapTimestamps.iterator();
        while (iter.hasNext()) {
            if (timestamp - (Long)iter.next() > 500000000L) {
                iter.remove();
            }
        }
        int res = 0;
        if (!tapTimestamps.isEmpty()) {
            iter = tapTimestamps.iterator();
            while (true) {
                if (!iter.hasNext()) {
                    res = 1;
                    break;
                }
                if ((Long)tapTimestamps.getLast() - (Long)iter.next() > 100000000L) {
                    tapTimestamps.clear();
                    res = 2;
                    break;
                }
            }
        }
        return res;
    }

    public void recognizeTapML(long timestamp) {
        if (resampleAcc.getInterval() == 0)
            return;
        int result = 0;
        int diff = (int)((resampleAcc.getResults().t - resampleGyro.getResults().t) / resampleAcc.getInterval());
        int peakIdx = peakDetectorPositive.getIdMajorPeak();
        if (peakIdx > 12) {
            wasPeakApproaching = true;
        }
        int accIdx = peakIdx - 6;
        int gyroIdx = accIdx - diff;
        if (accIdx >= 0 && gyroIdx >= 0) {
            if (accIdx + seqLength < size && gyroIdx + seqLength < size && wasPeakApproaching && peakIdx <= 12) {
                wasPeakApproaching = false;
                result = Util.getMaxId(tflite.predict(getInput(accIdx, gyroIdx), 7).get(0));
            }
        }
        if (result == 1) {
            tapTimestamps.addLast(timestamp);
            int count = checkDoubleTapTiming(lastTimestamp);
            if (count == 2) {
                if (callback != null) {
//                    for (ActionListener listener : actionListener) {
//                        ActionResult actionResult = new ActionResult(ACTION_RECOGNIZED);
//                        listener.onAction(actionResult);
//                    }
                    callback.onComplete(true);
                    horizontalFilter.updateCondition();
                    combinedFilter.updateCondition();
                    this.updateCondition();
                }
            }
        }
    }

    private synchronized void updateCondition() {
        flag2 = false;
        existTaptapSignal = true;
    }

    public synchronized static void onConfirmed() {
        combinedFilter.confirmed();
    }

}