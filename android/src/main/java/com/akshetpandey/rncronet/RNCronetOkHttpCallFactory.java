package com.akshetpandey.rncronet;

import org.chromium.net.CronetEngine;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.internal.annotations.EverythingIsNonNull;

@EverythingIsNonNull
public class RNCronetOkHttpCallFactory implements Call.Factory {

  private final OkHttpClient client;

  RNCronetOkHttpCallFactory(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public Call newCall(Request request) {
    CronetEngine engine = RNCronetNetworkingModule.cronetEngine();
    if (engine != null) {
      return new RNCronetOkHttpCall(client, engine, request);
    } else {
      return client.newCall(request);
    }
  }
}
