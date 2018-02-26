package nd.edu.bluenet_new;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattServer mGattServer;

    private boolean mScanning;


    public static final ParcelUuid TEST_SERVICE = ParcelUuid.fromString("00001809-0000-1000-8000-00805f9b34fb");
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final int REQUEST_ENABLE_BT = 1;
//    private static final long SCAN_PERIOD = 10000;

    private BluetoothGattService mTestGattService;
    private BluetoothGattCharacteristic mBatteryLevelCharacteristic;

    //    private LeDeviceListAdapter mLeDeviceListAdapter;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // check the permission of fine & coarse location, bluetooth and BLE capability
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

    }


    //********//
    //BLE SCAN//
    //********//
    private void scanLeDevice(final boolean enable, final long SCAN_PERIOD) {
        //scan settings
        ScanFilter ResultsFilter = new ScanFilter.Builder()
                //.setDeviceAddress(string)
                //.setDeviceName(string)
                //.setManufacturerData()
                //.setServiceData()
                .setServiceUuid(TEST_SERVICE)
                .build();

        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(ResultsFilter);

        //scan settings
        ScanSettings settings = new ScanSettings.Builder()
                //.setCallbackType() //int
                //.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) //AGGRESSIVE, STICKY  //require API 23
                //.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) //ONE, FEW, MAX  //require API 23
                //.setReportDelay(0) //0: no delay; >0: queue up
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //LOW_POWER, BALANCED, LOW_LATENCY
                .build();

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        } else {
            mScanning = false;
            mBluetoothLeScanner.stopScan(mScanCallback);
        }
        invalidateOptionsMenu();
    }

    private void stopScan() {
        if (mBluetoothLeScanner != null)
            mBluetoothLeScanner.stopScan(mScanCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(MainActivity.class.getSimpleName(), "onScanResult");
            processResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(MainActivity.class.getSimpleName(), "onBatchScanResults: " + results.size() + " results");
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(MainActivity.class.getSimpleName(), "LE Scan Failed: " + errorCode);
        }

        private void processResult(ScanResult result) {
            Log.v(MainActivity.class.getSimpleName(), "Process scan results");

            //rules based on the scanning results

//            In_Marker in_marker = new In_Marker(result.getScanRecord());
//            Log.v(TAG, "The lat of this is " + in_marker.getLatitude() +
//                    " event type is " + in_marker.getEvent_type() +
//                    " ROM is " + in_marker.getRom() +
//                    " ROI is" + searchDistance);


        }
    };

    //********//
    //BLE ADVERTISE//
    //********//
    private void startLeAdvertising(byte[] data_out){
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) //3 modes: LOW_POWER, BALANCED, LOW_LATENCY
                .setConnectable(false)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // ULTRA_LOW, LOW, MEDIUM, HIGH
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(TEST_SERVICE)
                .addServiceData(TEST_SERVICE,data_out)
                .build();

        if (mBluetoothLeAdvertiser == null)
            return;
        mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);

    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(MainActivity.class.getSimpleName(),  "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(MainActivity.class.getSimpleName(), "LE Advertise Failed: " + errorCode);
        }
    };

    public void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;
        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    public void restartAdvertising(byte[] data_out) {
        stopAdvertising();
        startLeAdvertising(data_out);
    }


    //Timer
    public void doWork() {
        runOnUiThread(new Runnable() {
            public void run() {
                try{
                    Calendar rightNow = Calendar.getInstance();
                    long mili = rightNow.getTimeInMillis() - (long) 1465830000000.00;
                    String curTime = "Current Time: " + mili;
                    //Log.v(TAG, curTime);
                }catch (Exception e) {}
            }
        });
    }

    class CountDownRunner implements Runnable{
        // @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()){
                try {
                    doWork();
                    Thread.sleep(1); // Pause of 1 miliSecond
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }catch(Exception e){
                }
            }
        }
    }

    //Packet translation
    public static byte[] intToByteArray(int a)
    {
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);
        return ret;
    }


    public static int ByteArrayToint(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }
}
