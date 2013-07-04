package net.cruciblesoftware.spinnaz;

import java.io.IOException;
import java.text.DecimalFormat;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

public class SpinnazActivity extends Activity implements SensorEventListener {
    private static final String TAG = "SPZ";

    private CheckBox transmitCheckbox;
    private TextView xAxisText, yAxisText, zAxisText, dumpText;
    private DecimalFormat formatter;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    float[] inR = new float[16];
    float[] outR= new float[16];
    float[] I = new float[16];
    float[] gravity, geomagnetic;
    float[] orientation = new float[3];

    boolean transmitAllowed;
    int transmitInProgress = 0;
    long currentTime, lastSendTime;

    class ServerUpdateTask extends AsyncTask<String, Integer, Integer> {

        @Override
        protected Integer doInBackground(String... params) {
            if(!transmitAllowed)
                return null;

            try {
                long time = System.currentTimeMillis();
                String urlReq = //
                        "http://cruciblesoftware.net/spinnaz/set_orientation.php?" +
                        "xrot=" + params[0] + "&yrot=" + params[1] +
                        "&zrot=" + params[2] + "&time=" + time;
                Log.i(TAG, transmitInProgress + " url request in progress at " + time);
                HttpClient client = new DefaultHttpClient();
                transmitInProgress++;
                publishProgress(new Integer[] { transmitInProgress });
                client.execute(new HttpGet(urlReq));
            } catch(ClientProtocolException e) {
                Log.e(TAG, "ERROR: " + e.getLocalizedMessage(), e);
            } catch(IOException e) {
                Log.e(TAG, "ERROR: " + e.getLocalizedMessage(), e);
            } catch(Exception e) {
                Log.e(TAG, "ERROR: " + e.getLocalizedMessage(), e);
            }
            transmitInProgress--;
            return transmitInProgress;
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            dumpText.setText("http requests: " + progress[0]);
        }

        protected void onPostExecute(Integer...progress) {
            dumpText.setText("http requests: " + progress[0]);
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spinnaz);
        transmitCheckbox = (CheckBox)findViewById(R.id.transmitCheckbox);
        xAxisText = (TextView)findViewById(R.id.xAxisText);
        yAxisText = (TextView)findViewById(R.id.yAxisText);
        zAxisText = (TextView)findViewById(R.id.zAxisText);
        dumpText = (TextView)findViewById(R.id.dumpText);
        formatter = new DecimalFormat(" 000.000000;-000.000000");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        transmitAllowed = false;
        transmitCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.i(TAG, "setting transmitAllowed=" + isChecked);
                transmitAllowed = isChecked;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        transmitAllowed = false;
        transmitCheckbox.setChecked(false);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        transmitAllowed = false;
        transmitCheckbox.setChecked(false);
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "got accuracy change from sensor " + sensor.getName() + " to " + accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        /*
        if(event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.w(TAG, "sensor " + event.sensor.getName() + " self-reporting as unreliable");
            return;
        }
         */

        // store the sensor's reported values
        switch(event.sensor.getType()) {
        case Sensor.TYPE_ACCELEROMETER:
            gravity = event.values.clone();
            break;
        case Sensor.TYPE_MAGNETIC_FIELD:
            geomagnetic = event.values.clone();
            break;
        }

        // calculate orientation if we have gravity and geomag vales
        if(gravity != null && geomagnetic != null) {
            boolean gotMatrices = SensorManager.getRotationMatrix(inR, I, gravity, geomagnetic);
            if(gotMatrices) {
                SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Y, outR);
                SensorManager.getOrientation(outR, orientation);
                float yawVal = orientation[0];
                float pitchVal = orientation[1];
                float rollVal = orientation[2];

                //String yaw = String.format("%03.6f", yawVal);
                //String pitch = String.format("%03.6f", pitchVal);
                //String roll = String.format("%03.6f", rollVal);
                String yaw = formatter.format(Math.toDegrees(yawVal));
                String pitch = formatter.format(Math.toDegrees(pitchVal));
                String roll = formatter.format(Math.toDegrees(rollVal));

                xAxisText.setText(yaw);
                yAxisText.setText(pitch);
                zAxisText.setText(roll);

                currentTime = System.currentTimeMillis();
                if(transmitInProgress < 30 /*&& currentTime - lastSendTime > 250*/) {
                    new ServerUpdateTask().execute(new String[]{ Float.toString(yawVal), Float.toString(pitchVal), Float.toString(rollVal) });
                    lastSendTime = currentTime;
                }
            }
        }
    }
}
