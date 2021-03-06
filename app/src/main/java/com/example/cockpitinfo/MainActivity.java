package com.example.cockpitinfo;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView acc_out;
    private TextView loc_out;
    private TextView ori_out;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private float[] gravity = new float[3];
    private float[] mfield  = new float[3];

    private long acc_lasttime = 0;
    private long ori_lasttime = 0;

    private boolean acc_log = false;
    private boolean loc_log = false;
    private boolean ori_log = false;

    private PrintWriter acc_pw;
    private PrintWriter loc_pw;
    private PrintWriter ori_pw;

    private DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TextViews
        acc_out = (TextView) findViewById(R.id.acc_out);
        loc_out = (TextView) findViewById(R.id.loc_out);
        ori_out = (TextView) findViewById(R.id.ori_out);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Accelerometer
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Magnetometer
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer , SensorManager.SENSOR_DELAY_NORMAL);

        // GPS
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        updateLocInfo(lastKnownLocation);

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                updateLocInfo(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30*1000, 0, locationListener);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity[0] = sensorEvent.values[0];
            gravity[1] = sensorEvent.values[1];
            gravity[2] = sensorEvent.values[2];

            long curTime = System.currentTimeMillis();

            if ((curTime - acc_lasttime) > 1000) {
                float acc = (float)Math.sqrt(gravity[0]*gravity[0]+
                                             gravity[1]*gravity[1]+
                                             gravity[2]*gravity[2]);
                float g = acc/9.81f;
                acc_out.setText(String.format("%.2f", acc)+" m/s "+
                                String.format("%.2f", g)+" g");
                acc_lasttime = curTime;

                // Log it!
                if (acc_log) {
                    acc_pw.println(df.format(new Date())+"  "+String.format("%.2f", acc)+" m/s");
                }
            }
        }

        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

            long curTime = System.currentTimeMillis();
            if ((curTime - ori_lasttime) > 1000) {
                mfield[0] = sensorEvent.values[0];
                mfield[1] = sensorEvent.values[1];
                mfield[2] = sensorEvent.values[2];

                float R[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, gravity, mfield);

                if (success) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    float heading = (float) Math.toDegrees(orientation[0]);
                    float pitch = (float) Math.toDegrees(orientation[1]);
                    float roll = (float) Math.toDegrees(orientation[2]);

                    heading = (heading + 360) % 360;
                    pitch *= -1;

                    ori_out.setText(String.format("%.1f", pitch) + "°  " +
                            String.format("%.1f", roll) + "°  " +
                            String.format("%.1f", heading) + "°");

                    if (ori_log) {
                        ori_pw.println(df.format(new Date())+"   "+
                                String.format("%.1f", pitch) + "°  " +
                                String.format("%.1f", roll) + "°  " +
                                String.format("%.1f", heading) + "°");
                    }
                }

                ori_lasttime = curTime;
            }
        }
    }

    void updateLocInfo (Location location) {
        if (location != null) {
            float latitude = (float) location.getLatitude();
            float longitude = (float) location.getLongitude();
            float altitude = (float) location.getAltitude();
            float altitude_feet = altitude * 3.28f;
            float speed = (float) location.getSpeed();
            float speed_knots = speed*1.94f;

            String latitude_str = String.format("%.1f", Math.abs(latitude)) + '°' + (latitude >= 0 ? 'N' : 'S');
            String longitude_str = String.format("%.1f", Math.abs(longitude)) + '°' + (longitude >= 0 ? 'E' : 'W');

            String altitude_str, altitude_feet_str;
            if (altitude > 1000) altitude_str = String.format("%.1f",(altitude/1000f)) + " km";
            else altitude_str = String.format("%.1f",altitude) + " m";

            if (altitude_feet > 1000) altitude_feet_str = String.format("%.1f",(altitude_feet/1000f)) + "k feet";
            else altitude_feet_str = String.format("%.1f",altitude_feet) + " feet";

            String speed_str = (int)speed + " m/s";
            String speed_knots_str = (int)speed_knots + " knots";

            loc_out.setText(latitude_str + "  " + longitude_str + "\n\n"+
                            altitude_str + "  " + altitude_feet_str + "\n\n"+
                            speed_str + "  " + speed_knots_str);

            if (loc_log) {
                loc_pw.println(df.format(new Date())+"   "+latitude_str+"  "+longitude_str+"  "+altitude_str+"  "+speed_str);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing.
    }

    public void onCheckClick(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        ((CheckBox)view).setText(checked ? "Logging" : "Not Logging");

        switch(view.getId()) {
            case R.id.acc_log:
                if (checked) {
                    try {
                        File acc_path = new File(Environment.getExternalStorageDirectory(), "/CockpitInfo/acc.log");
                        if (!acc_path.exists()) {
                            acc_path.getParentFile().mkdirs();
                            acc_path.createNewFile();
                        }
                        FileWriter acc_fw = new FileWriter(acc_path, true);
                        BufferedWriter acc_bw = new BufferedWriter(acc_fw);
                        acc_pw = new PrintWriter(acc_bw);
                        acc_log = true;
                    } catch (Exception e) {
                        System.out.println("File Error"+e);
                        acc_log = false;
                    }
                } else {
                    acc_pw.close();
                    acc_log = false;
                }
                break;
            case R.id.loc_log:
                if (checked) {
                    try {
                        File loc_path = new File(Environment.getExternalStorageDirectory(), "/CockpitInfo/loc.log");
                        if (!loc_path.exists()) {
                            loc_path.getParentFile().mkdirs();
                            loc_path.createNewFile();
                        }
                        FileWriter loc_fw = new FileWriter(loc_path, true);
                        BufferedWriter loc_bw = new BufferedWriter(loc_fw);
                        loc_pw = new PrintWriter(loc_bw);
                        loc_log = true;
                    } catch (Exception e) {
                        loc_log = false;
                    }
                } else {
                    loc_pw.close();
                    loc_log = false;
                }
                break;
            case R.id.ori_log:
                if (checked) {
                    try {
                        File ori_path = new File(Environment.getExternalStorageDirectory(), "/CockpitInfo/ori.log");
                        if (!ori_path.exists()) {
                            ori_path.getParentFile().mkdirs();
                            ori_path.createNewFile();
                        }
                        FileWriter ori_fw = new FileWriter(ori_path, true);
                        BufferedWriter ori_bw = new BufferedWriter(ori_fw);
                        ori_pw = new PrintWriter(ori_bw);
                        ori_log = true;
                    } catch (Exception e) {
                        ori_log = false;
                    }
                } else {
                    ori_pw.close();
                    ori_log = false;
                }
                break;
        }
    }

    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(locationListener);
    }

    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer , SensorManager.SENSOR_DELAY_NORMAL);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30*1000, 0, locationListener);
    }
}
