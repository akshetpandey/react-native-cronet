package com.example;

import android.app.Application;

import com.akshetpandey.rncronet.RNCronetFrescoImagePipelineConfig;
import com.akshetpandey.rncronet.RNCronetNetworkingPackage;

import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.react.PackageList;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.shell.MainPackageConfig;
import com.facebook.soloader.SoLoader;

import java.util.List;

public class MainApplication extends Application implements ReactApplication {

  private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
    @Override
    public boolean getUseDeveloperSupport() {
      return BuildConfig.DEBUG;
    }

    @Override
    protected List<ReactPackage> getPackages() {
      ImagePipelineConfig pipelineConfig = RNCronetFrescoImagePipelineConfig.build(getApplicationContext());
      MainPackageConfig config = new MainPackageConfig.Builder().setFrescoConfig(pipelineConfig).build();
      List<ReactPackage> packages = new PackageList(this, config).getPackages();
      // Packages that cannot be autolinked yet can be added manually here, for example:
      packages.add(new RNCronetNetworkingPackage());
      return packages;
    }

    @Override
    protected String getJSMainModuleName() {
      return "index";
    }
  };

  @Override
  public ReactNativeHost getReactNativeHost() {
    return mReactNativeHost;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    SoLoader.init(this, /* native exopackage */ false);
  }
}
