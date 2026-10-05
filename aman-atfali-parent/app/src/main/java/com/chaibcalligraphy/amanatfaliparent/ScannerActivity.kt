package com.chaibcalligraphy.amanatfaliparent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
class ScannerActivity:ComponentActivity(){
 private lateinit var preview:PreviewView;private val executor=Executors.newSingleThreadExecutor();private var done=false
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_scanner);preview=findViewById(R.id.previewView);start()}
 @SuppressLint("UnsafeOptInUsageError") private fun start(){val f=ProcessCameraProvider.getInstance(this);f.addListener({val p=f.get();val pre=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider};val scanner=BarcodeScanning.getClient();val a=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();a.setAnalyzer(executor){proxy->val img=proxy.image;if(img==null||done){proxy.close();return@setAnalyzer};scanner.process(InputImage.fromMediaImage(img,proxy.imageInfo.rotationDegrees)).addOnSuccessListener{codes->val v=codes.firstOrNull()?.rawValue;if(v!=null&&v.startsWith("AFT|")&&!done){done=true;runOnUiThread{setResult(RESULT_OK,Intent().putExtra("QR_CODE",v));finish()}}}.addOnCompleteListener{proxy.close()}};p.unbindAll();p.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,pre,a)},ContextCompat.getMainExecutor(this))}
 override fun onDestroy(){super.onDestroy();executor.shutdown()}
}