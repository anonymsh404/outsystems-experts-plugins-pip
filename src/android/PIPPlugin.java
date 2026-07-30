package com.outsystems.experts.plugins.pip;

import android.content.Context;
import android.content.Intent;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.util.Log;
import android.os.Bundle;
import android.os.Build;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import android.content.res.Configuration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PIPPlugin extends CordovaPlugin {
    private final PictureInPictureParams.Builder pictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
    private CallbackContext callback = null;

    // Aspect ratio default -- harus sama persis sama yang dipakai JS
    // (chat.js manggil pip.enter(240, 320, ...)) biar konsisten baik
    // masuk PiP lewat JS maupun lewat jalur native langsung di bawah.
    private static final int DEFAULT_W = 240, DEFAULT_H = 320;

    // Sumber kebenaran status "lagi ada video call aktif", di-update dari
    // JS tiap kali call video mulai/selesai (lihat action
    // "setVideoCallActive" & syncNativeVideoCallState() di chat.js).
    // Dibaca langsung (synchronous, static, gak lewat exec bridge) oleh
    // MainActivity.onUserLeaveHint() -- karena momen itu KRITIS-WAKTU:
    // Android bisa motong akses kamera app yang di-background lebih
    // cepat daripada round-trip WebView JS -> exec bridge -> balik lagi
    // ke native buat manggil enterPictureInPictureMode(). Dengan flag
    // ini, MainActivity bisa langsung manggil PiP di tempat, sinkron,
    // di UI thread yang sama tempat onUserLeaveHint() sendiri jalan.
    public static volatile boolean activeVideoCall = false;

    // Dipanggil LANGSUNG dari MainActivity.onUserLeaveHint() (bukan lewat
    // exec/JS) -- makanya static & nerima Activity dari luar. Aman
    // dipanggil di UI thread (onUserLeaveHint() sendiri emang di UI thread).
    public static void enterPipFromNative(android.app.Activity activity) {
        if (activity == null) return;
        try {
            Rational aspectRatio = new Rational(DEFAULT_W, DEFAULT_H);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build();
            activity.enterPictureInPictureMode(params);
        } catch (Exception e) {
            Log.w("PIPPlugin", "enterPipFromNative gagal: " + Log.getStackTraceString(e));
        }
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if(action.equals("enter")){
            Double width = args.getDouble(0);
            Double height = args.getDouble(1);
            this.enterPip(width, height, callbackContext);
            return true;
        } else if(action.equals("isPip")){
            this.isPip(callbackContext);
            return true;
        } else if(action.equals("setVideoCallActive")){
            activeVideoCall = args.getBoolean(0);
            callbackContext.success();
            return true;
        } else if(action.equals("onPipModeChanged")){
            if(callback == null){
                callback = callbackContext; //save global callback for later callbacks
                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT); //send no result to execute the callbacks later
                pluginResult.setKeepCallback(true); // Keep callback
            }
            return true;
        } else if(action.equals("isPipModeSupported")){
            this.isPipModeSupported(callbackContext);
            return true;
        } 
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig){
        if(callback != null){
            try{
                if(this.cordova.getActivity().isInPictureInPictureMode()){
                    this.callbackFunction(true, "true");
                } else {
                    this.callbackFunction(true, "false");
                }
            } catch(Exception e){
                String stackTrace = Log.getStackTraceString(e);
                this.callbackFunction(false, stackTrace);
            }
        }
    }

    public void callbackFunction(boolean op, String str){
        if(op){
            PluginResult result = new PluginResult(PluginResult.Status.OK, str);
            result.setKeepCallback(true);
            callback.sendPluginResult(result);
        } else {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, str);
            result.setKeepCallback(true);
            callback.sendPluginResult(result);
        }
    }

    private void enterPip(Double width, Double height, CallbackContext callbackContext) {
        // enterPictureInPictureMode() itu operasi Window/Activity -- WAJIB
        // dipanggil dari UI thread. execute() plugin Cordova secara default
        // jalan di background thread pool (cordova.getThreadPool()), BUKAN
        // UI thread -- jadi tanpa runOnUiThread() di sini, panggilan di
        // bawah bisa kena thread violation. Kadang ke-tangkep try/catch
        // (gagal masuk PiP diam2), kadang exception-nya nembus di level
        // Window/ViewRootImpl yang gak bisa ditangkep dari thread ini --
        // itu yang bikin force close.
        this.cordova.getActivity().runOnUiThread(() -> {
            try{
                if(width != null && width > 0 && height != null && height > 0){
                    Rational aspectRatio = new Rational(Integer.valueOf(width.intValue()), Integer.valueOf(height.intValue()));
                    pictureInPictureParamsBuilder.setAspectRatio(aspectRatio).build();
                    this.cordova.getActivity().enterPictureInPictureMode(pictureInPictureParamsBuilder.build());

                    callbackContext.success("Scaled picture-in-picture mode started.");
                } else {
                    this.cordova.getActivity().enterPictureInPictureMode();

                    callbackContext.success("Default picture-in-picture mode started.");
                }
            } catch(Exception e){
                String stackTrace = Log.getStackTraceString(e);
                callbackContext.error(stackTrace);
            }
        });
    }

    public void isPip(CallbackContext callbackContext) {
        // Sama kayak enterPip() -- baca state Activity harusnya aman dari
        // thread manapun, tapi biar konsisten & jaga2 (beberapa OEM custom
        // ROM diketahui rewel soal ini), tetap dibungkus UI thread.
        this.cordova.getActivity().runOnUiThread(() -> {
            try{
                if(this.cordova.getActivity().isInPictureInPictureMode()){
                    callbackContext.success("true");
                } else {
                    callbackContext.success("false");
                }
            } catch(Exception e){
                String stackTrace = Log.getStackTraceString(e);
                callbackContext.error(stackTrace);
            }
        });
    }

    private void isPipModeSupported(CallbackContext callbackContext) {
        try{
            boolean supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O; //>= SDK 26 //Oreo

            if(supported){
                callbackContext.success("true");
            } else {
                callbackContext.success("false");
            }
        } catch(Exception e){
            String stackTrace = Log.getStackTraceString(e);
            callbackContext.error(stackTrace);
        }
    }
}
