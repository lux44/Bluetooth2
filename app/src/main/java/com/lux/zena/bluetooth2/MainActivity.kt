package com.lux.zena.bluetooth2

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.lux.zena.bluetooth2.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {


    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val bluetoothManager by lazy { this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    //val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter}

    private fun PackageManager.missingSystemFeature(name: String) : Boolean = !hasSystemFeature(name)

    //다중 퍼미션 배열 - 동적 퍼미션 필요
    private val permissions:Array<String> = arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

    //request code - 아무숫자
    private val multiplePermissionCode = 100
    // 퍼미션 체크 함수
    private fun runtimeCheckSelfPermission(context: Context, permissions: Array<String>): Boolean {
        if (context!=null && permissions!=null){
            permissions.forEach {
                if (ActivityCompat.checkSelfPermission(context, it)!=PackageManager.PERMISSION_GRANTED){
                    return false
                }
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            multiplePermissionCode->{
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Log.i("권한 테스트","사용자가 권한 부여")
                }else{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package ${applicationContext.packageName}"))
                    startActivity(intent)
                }
            }
        }
    }


    //블루투스 스캔 변수 & 함수
    private lateinit var bluetoothLeScanner : BluetoothLeScanner
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val scan_period = 10000L

    // scan filter 담는 배열
    var filters: MutableList<ScanFilter> = mutableListOf()

    // MAC address
    private var deviceMacAddresses:String=""

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            //Log.i("blog","$callbackType, $result")
            if (result!=null){
                deviceMacAddresses = result.device.toString()
            }
        }
    }
    // Scan Filter 를 사용하지 않고 찾는 scan 함수
//    private fun scanLeDevice(){
//        if (!scanning){
//            handler.postDelayed({
//                scanning=false
//                if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED){
//                    bluetoothLeScanner.stopScan(leScanCallback)
//                }
//            },scan_period)
//            scanning=true
//            bluetoothLeScanner.startScan(leScanCallback)
//        }else{
//            scanning = false
//            bluetoothLeScanner.stopScan(leScanCallback)
//        }
//    }

    // Scan Filter 사용

    private fun scanLeDevice(){
        if (!scanning){
            handler.postDelayed({
                scanning=false
                if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED){
                    bluetoothLeScanner.stopScan(leScanCallback)
                }
            }, scan_period)
            scanning=true
            val scanFilter:ScanFilter = ScanFilter.Builder()
//                .setDeviceAddress("C6:AF:2E:CA:EE:1E")
                .setServiceUuid(ParcelUuid(
                    UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")))
                .build()
            val scanSettings = ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                            or ScanSettings.SCAN_MODE_LOW_POWER
                ).build()
            filters.add(scanFilter)
            bluetoothLeScanner.startScan(filters,scanSettings,leScanCallback)
        }else{
            scanning=false
            bluetoothLeScanner.stopScan(leScanCallback)
        }
    }

    private fun displayGattServices(gattServices:List<BluetoothGattService>?){
        if (gattServices==null){
            Log.i("blog","gattService==null")
            return
        }
        var serviceUUID:String?
        var characteristicUUID:String?
        // Loops through available GATT services
        gattServices.forEach{
            serviceUUID=it.uuid.toString()
            val gattCharacteristics: MutableList<BluetoothGattCharacteristic> = it.characteristics
            Log.i("blog","Service : $serviceUUID")

            gattCharacteristics.forEach{ gattCharacteristics->
                characteristicUUID=gattCharacteristics.uuid.toString()
                Log.i("blog","Characteristic: $characteristicUUID")
                Log.i("blog","${gattCharacteristics.writeType}, ${gattCharacteristics.permissions}, ${gattCharacteristics.properties}")
            }
            Log.i("blog","~~~~~~~~~~~~~~~")
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status==BluetoothGatt.GATT_SUCCESS){
                Log.i("blog","gat success")
                if (gatt!=null){
                    displayGattServices(gatt.services)
                }else Log.i("displayGattService","")
            }else Log.e("blog","got failed")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED ->{
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
                        Log.i("tag","connected")
                    }
                    if (ActivityCompat.checkSelfPermission(this@MainActivity,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED) gatt?.discoverServices()

                }
                BluetoothProfile.STATE_DISCONNECTED ->{
                    Log.i("blog","disconnected")
                    Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var bluetoothGatt:BluetoothGatt? =null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        setContentView(binding.root)

        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            Toast.makeText(this, "ble not supported", Toast.LENGTH_SHORT).show()
            finish()
        }

        if (!runtimeCheckSelfPermission(this, permissions)){
            ActivityCompat.requestPermissions(this,permissions,multiplePermissionCode)
        }else{
            Log.i("권한 테스트","권한 있음.")
        }




        val bluetoothAdapter:BluetoothAdapter?=
            if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S){
                val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothManager.adapter
            }else{
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }

        var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
            {
                if(it.resultCode== Activity.RESULT_OK){
                    Log.e("bluetooth","ok")
                }else{
                    Log.e("bluetooth","canceled")
                }
            }

        if (bluetoothAdapter?.isEnabled==false){
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            resultLauncher.launch(enableIntent)
        }

        if (bluetoothAdapter == null){
            Toast.makeText(this, "device does not support bluetooth", Toast.LENGTH_SHORT).show()
        }else{
            //Device bluetooth 연결 가능
            //Toast.makeText(this, "device can support bluetooth", Toast.LENGTH_SHORT).show()
            if(!bluetoothAdapter.isEnabled){
                var enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
                ) {
                    if (it.resultCode== RESULT_OK){
                        enableIntent= it.data!!
                    }
                }
                resultLauncher.launch(enableIntent)

            }else{
                AlertDialog.Builder(this).setMessage("Device auto connect").create().show()
            }
        }

//        val filter:IntentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
//        registerReceiver(receiver,filter)

        bluetoothLeScanner= bluetoothAdapter!!.bluetoothLeScanner
        binding.tv.setOnClickListener {
            scanLeDevice()
        }

        binding.tvConnect.setOnClickListener {
            if (deviceMacAddresses!=""){
                val bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceMacAddresses)

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt = bluetoothDevice.connectGatt(applicationContext,false,bluetoothGattCallback)
                    Log.i("blog","${bluetoothGatt.toString()}")
                }else Log.i("blog","connection fail")

            }
        }

    }


    override fun onStop() {
        super.onStop()
        bluetoothGatt?.let {
            if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED) {
                it.close()
            }
            bluetoothGatt=null
        }
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        unregisterReceiver(receiver)
//
//    }

//    private val receiver = object : BroadcastReceiver() {
//        override fun onReceive(p0: Context?, p1: Intent?) {
//            if (ActivityCompat.checkSelfPermission(this@MainActivity,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){
//                when(p1?.action) {
//                    BluetoothDevice.ACTION_FOUND->{
//                        val device:BluetoothDevice? = p1.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
//                        val deviceName = device?.name
//                        val deviceAddress = device?.address
//
//                    }
//                }
//            }
//        }

    //askdflaksjdf
}
