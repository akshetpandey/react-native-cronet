package com.akshetpandey.rncronet;

import android.content.Context;

import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.imagepipeline.listener.RequestListener;
import com.facebook.react.modules.fresco.SystraceRequestListener;
import com.facebook.react.modules.network.OkHttpClientProvider;

import java.util.HashSet;

import okhttp3.OkHttpClient;

public class RNCronetFrescoImagePipelineConfig {
  public static ImagePipelineConfig build(Context context) {
    HashSet<RequestListener> requestListeners = new HashSet<>();
    requestListeners.add(new SystraceRequestListener());

    OkHttpClient client = OkHttpClientProvider.createClient();

    return OkHttpImagePipelineConfigFactory.newBuilder(context.getApplicationContext(), client)
            .setNetworkFetcher(new RNCronetOkHttpNetworkFetcher(client))
            .setDownsampleEnabled(false)
            .setRequestListeners(requestListeners)
            .build();
  }
}
