package com.hcifuture.producer.detector.taptap;

import static java.lang.Math.abs;

import android.hardware.Sensor;
import android.util.Log;

public class CloseAction {

    public interface Callback {
        void onComplete(Boolean result);
    }

    //判断自然动作所需的变量
    boolean register_flag;
    float gx,gy,gz; //陀螺仪在x,y,z上的
    float dist; //接近光的数
    long register_time; //看接近光传感器注册的时间
    boolean upright_gyro; // 看凑近嘴部的加速度
    long up_gyro_id; // 凑近嘴部的gyro的id
    long success_id; //成功时的id
    boolean success_flag;
    float bright;
    boolean send_flag; //假如没有传感器时，是否发送了日志
    boolean isStarted = false;

    Callback callback;

    public CloseAction(Callback callback) {
        this.callback = callback;
        reset();
    }

    //对变量进行初始化
    private void reset(){
        register_flag = false;
        register_time = -1;
        dist = -100;
        upright_gyro = false;
        up_gyro_id = -1;
        success_id = -1;
        success_flag = false;
        bright = -1;
    }

    public synchronized void start() {
        isStarted = true;
        send_flag = false;
        reset();

    }

    public synchronized void stop() {
        isStarted = false;
    }

    public void onIMUSensorEvent(SingleIMUData data) {
        int type = data.getType();
        switch (type) {
            case Sensor.TYPE_GYROSCOPE:
                // 需要将弧度转为角度
                gx = (float)Math.toDegrees(data.getValues().get(0));
                gy = (float)Math.toDegrees(data.getValues().get(1));
                gz = (float)Math.toDegrees(data.getValues().get(2));
                // check_close
                // gx要必须很大，且gy，gz不大，才可以说明是凑近嘴部。
                if (!upright_gyro) {
                    if (gx > 30 && abs(gx) - abs(gy) > 30 && abs(gx) - abs(gz) > 30) {
                        up_gyro_id = System.currentTimeMillis();
                        upright_gyro = true;
                    }
                }
                if(upright_gyro){
                    if((abs(abs(gy)-abs(gx))<30||abs(abs(gz)-abs(gx))<30||(abs(gz)>abs(gx))||abs(gy)>abs(gx))&&(abs(gy)>60||abs(gz)>60)){
                        upright_gyro = false;
                    }
                    if(System.currentTimeMillis()-up_gyro_id>4000){
                        upright_gyro = false;
                    }
                }
                if (success_id != -1) {
                    if(!success_flag) {
                        if (System.currentTimeMillis() - success_id > 100) {
                            callback.onComplete(true);
                            reset();
                        }
                    }
                }
                break;
            case Sensor.TYPE_PROXIMITY:
                dist = data.getValues().get(0);
                if(dist == 0 ) {
                    if (upright_gyro) {
                        success_id = System.currentTimeMillis();
                    }
                }
                if(dist==5) {
                    success_id = -1;
                    if (System.currentTimeMillis() - register_time > 10000 && register_flag) {
                        reset();
                        register_flag = false;
                    }
                }
                break;
        }

    }

    public synchronized void getAction() {
        if (!isStarted)
            return;
        if (success_flag) {
            reset();
//            for (ActionListener listener: actionListener) {
//                listener.onAction(new ActionResult("Close"));
//            }
        }
    }
}
