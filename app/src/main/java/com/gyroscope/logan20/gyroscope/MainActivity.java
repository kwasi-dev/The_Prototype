package com.gyroscope.logan20.gyroscope;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.util.Log;
import android.widget.TextView;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity implements SensorEventListener{
    // sensor manager
    SensorManager senMgr;

    //sensor variables for each of the sensors
    Sensor senGyro, senAccel;

    //matrix to store x, y and z values,
    //the 0th row = accelerometer
    //1st row = gyroscope
    //2nd row = linear acceleration with low pass filter to isolate gravity and holding values above NOISECONSTANT
    //3rd row = "corrected" gyroscope readings holding values above NOISECONSTANT
    //4th row holds gravity in each axis used for low pass filter calculations for linear acceleration
    float[][] valMatrix = new float[5][3];

    //alpha is used to calculate the gravity in the low pass filter,
    //all sensor readings below ACTIONTHRESHOLD are 0
    //readings above ACTIONTHRESHOLD are used to calculate data
    final float alpha = 0.8f, ACTIONTHRESHOLD = 0.4f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Setup sensor manager and sensors on creation of the class
        senMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senGyro = senMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        senAccel = senMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    protected void onStop(){//relinquish control of the sensors when app is stopped
        super.onStop();
        senMgr.unregisterListener(this, senGyro);
        senMgr.unregisterListener(this, senAccel);
    }

    @Override
    protected void onPause(){//relinquish control of the sensors when app is paused
        super.onPause();
        senMgr.unregisterListener(this, senGyro);
        senMgr.unregisterListener(this, senAccel);
    }

    @Override
    protected void onResume(){//regain control of the sensors when app is resumed
        super.onResume();
        senMgr.registerListener(this, senGyro, SensorManager.SENSOR_DELAY_NORMAL);
        senMgr.registerListener(this, senAccel, SensorManager.SENSOR_DELAY_NORMAL);

    }
    @Override
    protected void onStart(){//regain control of the sensors when app is resumed
        super.onStart();
        senMgr.registerListener(this, senGyro, SensorManager.SENSOR_DELAY_NORMAL);
        senMgr.registerListener(this, senAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1){
    }

    @Override
    public void onSensorChanged(SensorEvent event){
        Sensor sensor = event.sensor;
        int row=0;//sets the row of the matrix to modify and update values, 0 is to be accelerometer values and 1 is gyroscope values

        if (sensor.getType()==Sensor.TYPE_GYROSCOPE) //if the gyroscope is the sensor who's readings are to be taken, modify the row to be written accordingly
            row = 1;
        else //if the sensor is the accelerometer, calculate the values of each axis gravity and linear acceleration
            for (int a=0;a<3;a++) {
                valMatrix[4][a] = alpha *  valMatrix[4][a] + (1-alpha) * event.values[a]; //gravity calculation
                valMatrix[2][a] = Math.abs(event.values[a] -  valMatrix[4][a])<ACTIONTHRESHOLD?0:(event.values[a] -  valMatrix[4][a]); //linear acceleration calculation
            }
        for (int a=0;a<3;a++)
            valMatrix[row][a]=event.values[a];//write values to their respective places

        updateText(); //call the update text method which will write to the screen, the updated values
    }
    private void updateText(){
        //update the accelerometer readings
        TextView txtview = (TextView)findViewById(R.id.accelText); ;
        txtview.setText("X: "+ String.format("%.2f",valMatrix[0][0])+"   Y: "+String.format("%.2f",valMatrix[0][1])+"   Z: "+String.format("%.2f",valMatrix[0][2]));

        //update gyroscope readings
        txtview = (TextView)findViewById(R.id.gyroText);
        txtview.setText("X: "+ String.format("%.2f",valMatrix[1][0])+"   Y: "+String.format("%.2f",valMatrix[1][1])+"   Z: "+String.format("%.2f",valMatrix[1][2]));

        //update orientation readings
        txtview = (TextView)findViewById(R.id.orienText);
        txtview.setText(getOrientationAsString(getOrientationValue()));

        //linear acceleration readings
        txtview = (TextView)findViewById(R.id.linAccelText);
        txtview.setText("X: "+ String.format("%.2f",valMatrix[2][0])+"   Y: "+String.format("%.2f",valMatrix[2][1])+"   Z: "+String.format("%.2f",valMatrix[2][2]));

        //corrected gyro readings
        setCorrectedGyroscope();
        txtview = (TextView)findViewById(R.id.angularRotText);
        txtview.setText("X: "+ String.format("%.2f",valMatrix[3][0])+"   Y: "+String.format("%.2f",valMatrix[3][1])+"   Z: "+String.format("%.2f",valMatrix[3][2]));

        //set current action text
        txtview = (TextView)findViewById(R.id.actionText);
        txtview.setText(getCurrentAction());
    }
    private void setCorrectedGyroscope(){
        for (int a=0;a<3;a++)
            valMatrix[3][a]=Math.abs(valMatrix[1][a])<ACTIONTHRESHOLD?0.0f:valMatrix[1][a];//stores the "corrected" gyroscope readings
    }
    private String getCurrentAction(){
        String action="";
        //consider phone is moving towards or away from user's body
        //linear acceleration will be greatest on Z axis and greater than action threshold
        //and all noise corrected gyroscope readings will be below action threshold
        if (Math.abs(valMatrix[2][2])> Math.abs(valMatrix[2][1])) { // if z axis greater than y axis
            if (Math.abs(valMatrix[2][2])> Math.abs(valMatrix[2][0])) {// if z axis greater than x axis
                if (Math.abs(valMatrix[2][2])>ACTIONTHRESHOLD){// if moving with a good enough speed
                    if (Math.abs(valMatrix[3][0])<ACTIONTHRESHOLD && Math.abs(valMatrix[3][1])<ACTIONTHRESHOLD && Math.abs(valMatrix[3][2])<ACTIONTHRESHOLD ){ //if user isn't rotating phone to a great extent
                        action = valMatrix[2][2]>0?"Phone moving away from body":"Phone moving towards body"; //set action based on whether user is pushing or pulling the phone
                    }
                }
            }
        }

        //consider phone is moving left or right
        //noise corrected gyroscope's y axis reading will be greatest
        //and all noise corrected gyroscope readings will be below action threshold
        if (Math.abs(valMatrix[3][1])>Math.abs(valMatrix[3][2])){ // y axis greater than z axis
            if (Math.abs(valMatrix[3][1])>Math.abs(valMatrix[3][0])) { // y axis greater than x axis
                if (Math.abs(valMatrix[3][0])<ACTIONTHRESHOLD && Math.abs(valMatrix[3][2])<ACTIONTHRESHOLD){
                    action = valMatrix[3][1]>0?"Phone moving to the left":"Phone moving to the right";
                }
            }

        }

        return action;
    }
    private int getOrientationValue(){
        /*  the orientation of the phone will be determined by using the accelerometer.
            Since the accelerometer measures accelerarion and the most dominating acceleration force is gravity. It may be safe to assume that the orientation
            of the phone can be determined by whichever axis's absolute value is closest to 9.81 metres per second squared.
        */
        float absX=Math.abs(valMatrix[0][0]), absY=Math.abs(valMatrix[0][1]), absZ=Math.abs(valMatrix[0][2]);
        if(absX>absY && absX>absZ)
            return valMatrix[0][0]>0?1:2; // if phone is in normal landscape (with front camera on the left hand side) return 1 else return 2 if in reverse landscape
        else if (absY>absX && absY>absZ)
            return valMatrix[0][1]>0?3:4; // if phone is in normal portrait (with front camera facing top side)  return 3 else return 4 if phone is up-side down
        return valMatrix[0][2]>0?5:6; //if phone is flat, facing up, return 5 else return 6
    }
    private String getOrientationAsString(int val){
        String txt;
        switch(val){
            case 1: txt="Normal landscape";
                break;
            case 2: txt="Reverse landscape";
                break;
            case 3: txt="Normal portrait";
                break;
            case 4: txt="Reverse portrait";
                break;
            case 5: txt="Screen facing upwards";
                break;
            case 6: txt="Screen facing downwards";
                break;
            default: txt="Invalid orientation";
                break;
        }
     return txt;
    }
}
