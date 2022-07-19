package com.lux.zena.bluetooth2

import android.Manifest
import android.annotation.SuppressLint
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
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {
    
    companion object{
        //request code - 아무숫자
        private const val MULTIPLE_PERMISSION_CODE = 100
    }

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }


    private fun PackageManager.missingSystemFeature(name: String) : Boolean = !hasSystemFeature(name)

    //다중 퍼미션 배열 - 동적 퍼미션 필요
    private val permissions:Array<String> = arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

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

    val bluetoothAdapter:BluetoothAdapter by lazy {
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S){
            val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }else{
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            MULTIPLE_PERMISSION_CODE->{
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
    private val scan_period = 5000L

    // scan filter 담는 배열
    var filters: MutableList<ScanFilter> = mutableListOf()

    // MAC address
    private var bluetoothGatt:BluetoothGatt? =null


    private val leScanCallback: ScanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            //Log.i("blog","$callbackType, $result")
            result?.let {

                // 내가 찾는 조건이 존재할 때
                if(it.device.name != null && it.device.name.contains("BC")){
                    // 연결
                    val device :BluetoothDevice? = bluetoothAdapter.getRemoteDevice(it.device.address)

                    Log.i("blog","연결 하는 맥 어드레스 ${result.device.address}")
                    bluetoothGatt = device?.connectGatt(this@MainActivity ,true, bluetoothGattCallback)
                    bluetoothLeScanner.stopScan(this)
                    Log.e("STOP","STOP SCAN")
                }else {
                    return
                }
            }
        }
    }

    

    private fun scanLeDevice(){
        if (!scanning){
//            handler.postDelayed({
//                scanning=false
//                if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED){
//
//                    bluetoothLeScanner.stopScan(leScanCallback)
//
//                    Log.i("over","scan over  $deviceMacAddresses")
//                    if(deviceMacAddresses.isNotEmpty()){
//                        val device :BluetoothDevice? = bluetoothAdapter.getRemoteDevice(deviceMacAddresses)
//                        bluetoothGatt=device?.connectGatt(this,true,bluetoothGattCallback)
//
//                    }else{
//                        Log.e("TAG", "MAC ADDR IS NULL")
//                    }
//
//
//                    Log.e("TAG", "CONNECT GATT")
//
//                }
//            }, scan_period)
            scanning=true
//            val scanFilter:ScanFilter = ScanFilter.Builder()
//                .setDeviceAddress("C6:AF:2E:CA:EE:1E")
////                .setServiceUuid(ParcelUuid(
////                    UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")))
//                //.setDeviceName("BC-03")
//                .build()
//            val scanSettings = ScanSettings.Builder()
//                .setScanMode(
//                    ScanSettings.SCAN_MODE_LOW_LATENCY
//                            or ScanSettings.SCAN_MODE_LOW_POWER
//                ).build()
//            filters.add(scanFilter)
            //bluetoothLeScanner.startScan(filters,scanSettings,leScanCallback)
            bluetoothLeScanner.startScan(leScanCallback)
        }else{
            scanning=false
            bluetoothLeScanner.stopScan(leScanCallback)
            Log.i("over","scan over")
        }
    }

    @SuppressLint("MissingPermission")
    private fun displayGattServices(serviceUUID: String, gatt: BluetoothGatt){
        
        service = gatt.getService(UUID.fromString(serviceUUID))
        readCharacteristic = service.getCharacteristic(UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb"))

        val notifyDescriptor = readCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

        notifyDescriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

        gatt.apply {
            Log.i("blog","write Descriptor")
            writeDescriptor(notifyDescriptor)
            setCharacteristicNotification(readCharacteristic, true)
        }
    }


    fun getParsingTemperature(byteArray: ByteArray?): String {
        if(byteArray == null)
            return "0.0"

        if(byteArray.size > 5) {
            //sb.append(String.format("%02x ", byteArray[idx] and 0xff.toByte()))
            val sb = StringBuffer()
            sb.append(String.format("%02x", byteArray[3] and 0xff.toByte()))
            sb.append(String.format("%02x", byteArray[2] and 0xff.toByte()))
            sb.append(String.format("%02x", byteArray[1] and 0xff.toByte()))

            val temperature = Integer.parseInt(sb.toString(), 16)

            val value: Float = if(String.format("%02x", byteArray[4] and 0xff.toByte()) == "ff") {
                temperature.toFloat() / 10.toFloat()
            } else
                temperature.toFloat() / 100.0f
            return value.toString()
        }
        else {
            return "0.0"
        }
    }


    // 데이터 읽기
    // 데이터 읽기 위한 변수
    private lateinit var service:BluetoothGattService
    private lateinit var service2:BluetoothGattService
    private lateinit var readCharacteristic:BluetoothGattCharacteristic
    private lateinit var readCharacteristic2: BluetoothGattCharacteristic

    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.i("blog","characteristic changed : ${characteristic.toString()}, value : ${characteristic?.value.toString()}")
            var tp = getParsingTemperature(characteristic?.value)
            Log.i("blog","$tp")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.i("blog","characteristic write")
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            Log.i("blog","onCharacteristicRead")
//            if (status==BluetoothGatt.GATT_SUCCESS){
//                Log.i("blog","onCharacteristicRead")
//                val readValue:ByteArray = characteristic!!.value
//                val decodeValue:String = readValue.toString(
//                    StandardCharsets.UTF_8
//                )
//                Log.i("blog","$decodeValue")
//            }else Log.i("blog","fail to Read")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.i("blog","discovered")
            if (status==BluetoothGatt.GATT_SUCCESS){
                Log.i("blog","success")
                if (gatt!=null){
                    Log.e("TAG", "${gatt.services} , $gatt @@@@")

                    gatt.services.forEach { service ->

                        if(service.uuid.toString() == "00001809-0000-1000-8000-00805f9b34fb"){
                            displayGattServices(service.uuid.toString(),gatt)
                        }
                    }


                    Log.i("blog","after read ")
                    //gatt.readCharacteristic(readCharacteristic)
                }
            }
        }
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED ->{
//                    runOnUiThread {
//                        Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
//                        Log.i("tag","connected")
//                    }
                    Log.i("blog","connected")
                    runOnUiThread { Toast.makeText(this@MainActivity, "connect", Toast.LENGTH_SHORT).show() }
                    if (ActivityCompat.checkSelfPermission(this@MainActivity,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){
                        Log.e("TAG", "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED ->{
                    Log.i("blog","disconnected")
                    //Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        setContentView(binding.root)

        // 1.퍼미션 검사
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            Toast.makeText(this, "ble not supported", Toast.LENGTH_SHORT).show()
            finish()
        }

        if (!runtimeCheckSelfPermission(this, permissions)){
            ActivityCompat.requestPermissions(this,permissions,MULTIPLE_PERMISSION_CODE)
        }else{

            Log.i("권한 테스트","권한 있음.")
        }

        // 2. 버튼 동작
        if(!bluetoothAdapter.isEnabled){

            var enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
            ) {
                if (it.resultCode== RESULT_OK){
                    enableIntent= it.data!!
                }
            }
            resultLauncher.launch(enableIntent)

        }else{
            AlertDialog.Builder(this).setMessage("Device auto connect").create().show()
        }



        bluetoothLeScanner= bluetoothAdapter!!.bluetoothLeScanner
        binding.tv.setOnClickListener {
            scanLeDevice()
        }

//        binding.tvRead.setOnClickListener {
//            service = bluetoothGatt!!.getService(UUID.fromString("00001809-0000-1000-8000-00805f9b34fb"))
//
//            readCharacteristic = service.getCharacteristic(UUID.fromString("00002a1c-0000-1000-8000-00805F9B34FB"))
//
//            val notifyDescriptor:BluetoothGattDescriptor = readCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
//
//            notifyDescriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//
//            if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)
//                bluetoothGatt!!.readCharacteristic(readCharacteristic)
//        }
    }

}
