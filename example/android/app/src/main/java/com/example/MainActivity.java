package com.example;

import android.os.Bundle;

import com.akshetpandey.rncronet.RNCronetNetworkingModule;
import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.soloader.SoLoader;

import org.chromium.net.CronetEngine;

import java.io.File;
import java.net.URL;

public class MainActivity extends ReactActivity {

    /**
     * Returns the name of the main component registered from JavaScript.
     * This is used to schedule rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "example";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RNCronetNetworkingModule.setCustomCronetBuilder(context -> {
            File cacheDir = new File(context.getCacheDir(), "cronet-cache");
            cacheDir.mkdirs();
            CronetEngine cronetEngine = new CronetEngine.Builder(context)
                    .enableBrotli(true)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .setLibraryLoader(new CronetEngine.Builder.LibraryLoader() {
                        @Override
                        public void loadLibrary(String libName) {
                            SoLoader.loadLibrary(libName);
                        }
                    })
                    .setStoragePath(cacheDir.getAbsolutePath())
                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024)
                    .build();
            URL.setURLStreamHandlerFactory(cronetEngine.createURLStreamHandlerFactory());

            cronetEngine.startNetLogToFile(context.getCacheDir().getPath() + "/netlog.json", false);
            return cronetEngine;
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        CronetEngine engine = RNCronetNetworkingModule.cronetEngine();
        if (engine != null) {
            engine.stopNetLog();
        }
    }
}
