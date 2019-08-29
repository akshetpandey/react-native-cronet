package com.akshetpandey.rncronet;

import org.chromium.net.UrlRequest;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.annotations.EverythingIsNonNull;

@EverythingIsNonNull
class RNCronetInterceptor implements okhttp3.Interceptor {
  @Override
  public Response intercept(Chain chain) throws IOException {
    if (RNCronetNetworkingModule.cronetEngine() != null) {
      return proceedWithCronet(chain.request(), chain.call());
    } else {
      return chain.proceed(chain.request());
    }
  }

  private Response proceedWithCronet(Request request, Call call) throws IOException {
    RNCronetUrlRequestCallback callback = new RNCronetUrlRequestCallback(request, call);

    UrlRequest urlRequest = RNCronetNetworkingModule.buildRequest(request, callback);
    urlRequest.start();

    return callback.waitForDone();
  }
}
