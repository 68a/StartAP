package com.example.admin.startap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class MainActivity extends AppCompatActivity {

    private static final int BUFF_LEN = 10240 ;
    private boolean ap_state = false;

    private Switch switch_wifi;
    private Switch switch_mobi;
    private Switch switch_hotspot;
    private Switch switch_ss;
    private boolean in_receive = false;

    public String getExecCommandResult(String command) throws IOException {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "system/bin/sh"});
        DataOutputStream stdin = new DataOutputStream(p.getOutputStream());
//from here all commands are executed with su permissions
        stdin.writeBytes(command); // \n executes the command
        InputStream stdout = p.getInputStream();
        byte[] buffer = new byte[BUFF_LEN];
        int read;
        String out = new String();
//read method will wait forever if there is nothing in the stream
//so we need to read it in another way than while((read=stdout.read(buffer))>0)
        while(true){
            read = stdout.read(buffer);
            out += new String(buffer, 0, read);
            if(read<BUFF_LEN){
                //we have read everything
                break;
            }
        }
        return out;
    }

    public void getNetworkInfo() throws IOException {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) { // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                // connected to wifi
                this.switch_wifi.setChecked(true);
                System.err.println("connected to wifi");
            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                // connected to the mobile provider's data plan
                this.switch_mobi.setChecked(true);
                System.err.println("connected to mobile");
            }
        } else {
// not connected to the internet
            this.switch_wifi.setChecked(false);
            this.switch_mobi.setChecked(false);
            System.err.println("no connection");
        }
        String hotspot_status = getExecCommandResult("ndc softap status\n");
        if (hotspot_status.contains("Softap service is running")){
            switch_hotspot.setChecked(true);
        }
        else {
            switch_hotspot.setChecked(false);
        }
        System.err.println(hotspot_status);

        String shadowsocks_status = getExecCommandResult("ps reds\n");
        System.err.println(shadowsocks_status);
        if (shadowsocks_status.contains("redsocks")) {
            switch_ss.setChecked(true);
        }
        else {
            switch_ss.setChecked(false);
        }

    }


    BroadcastReceiver networkChangeReceiver;

    {
        networkChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // do stuff...

                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null) { // connected to the internet
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                        // connected to wifi
                        System.err.println("connected to wifi");
                        switch_wifi.setChecked(true);
                    } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                        // connected to the mobile provider's data plan
                        System.err.println("connected to mobile");
                        switch_mobi.setChecked(true);
                    }
                } else {
// not connected to the internet
                    System.err.println("no connection.");
                    switch_wifi.setChecked(false);
                    switch_mobi.setChecked(false);
                }
                in_receive = true;
                getWindow().getDecorView().findViewById(android.R.id.content).invalidate();
            }
        };
    }


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switch_mobi = (Switch) findViewById(R.id.switch_mobi);
        switch_wifi = (Switch) findViewById(R.id.switch_wifi);
        switch_hotspot = (Switch) findViewById(R.id.switch_hotspot);
        switch_ss = (Switch) findViewById(R.id.switch_ss);

        try {
            getNetworkInfo();
        } catch (IOException e) {
            e.printStackTrace();
        }

        registerReceiver(networkChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")); // register the receiver

        switch_mobi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                ConnectivityManager dataManager;
                dataManager  = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                Method dataMtd = null;

                try {
                    dataMtd = ConnectivityManager.class.getDeclaredMethod("setMobileDataEnabled", boolean.class);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }

                if (dataMtd != null) {
                    dataMtd.setAccessible(true);
                }
                try {
                    dataMtd.invoke(dataManager, isChecked);        //True - to enable data connectivity .
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        });
        switch_hotspot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    try {
                        execCommand("/system/xbin/su -c /system/bin/start_ap&");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        execCommand("/system/xbin/su -c /system/bin/stop_ap&");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        switch_ss.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    try {
                        execCommand("/system/xbin/su -c /system/bin/start_ss&");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        execCommand("/system/xbin/su -c /system/bin/stop_ss&");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkChangeReceiver!= null) {
            unregisterReceiver(networkChangeReceiver);
            networkChangeReceiver=null;
        }
       // unregisterReceiver(networkChangeReceiver); // unregister the receiver

    }

    public void execCommand(String command) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        Process proc = runtime.exec(command);
        try {
            if (proc.waitFor() != 0) {
                System.err.println("exit value = " + proc.exitValue());
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));
            StringBuffer stringBuffer = new StringBuffer();
            String line = null;
            while ((line = in.readLine()) != null) {
                stringBuffer.append(line+"-");
            }
            System.out.println(stringBuffer.toString());

        } catch (InterruptedException e) {
            System.err.println(e);
        }
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
