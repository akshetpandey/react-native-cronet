package com.akshetpandey.rncronet;

import android.util.Log;

import androidx.annotation.Nullable;

import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.annotations.EverythingIsNonNull;
import okhttp3.internal.connection.Transmitter;
import okio.Buffer;

@EverythingIsNonNull
class RNCronetInterceptor implements okhttp3.Interceptor {
  @Override
  public Response intercept(Chain chain) throws IOException {
    if (RNCronetNetworkingModule.cronetEngine() != null) {
      return proceedWithCronet(chain.call());
    } else {
      return chain.proceed(chain.request());
    }
  }

  private Response proceedWithCronet(Call call) throws IOException {
    Callback callback = new Callback(call);


    UrlRequest.Builder builder = RNCronetNetworkingModule.cronetEngine().newUrlRequestBuilder(call.request().url().toString(), callback, RNCronetNetworkingModule.executorService())
            .allowDirectExecutor()
            .setHttpMethod(call.request().method());

    Headers headers = call.request().headers();
    for (int i = 0; i < headers.size(); i += 1) {
      builder.addHeader(headers.name(i), headers.value(i));
    }

    RequestBody requestBody = call.request().body();
    if (requestBody != null) {
      MediaType contentType = requestBody.contentType();
      if (contentType != null) {
        builder.addHeader("Content-Type", contentType.toString());
      }
      Buffer buffer = new Buffer();
      requestBody.writeTo(buffer);
      builder.setUploadDataProvider(UploadDataProviders.create(buffer.readByteArray()), RNCronetNetworkingModule.executorService());
    }

    builder.build().start();

    try {
      return callback.mResponseFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else {
        throw new IOException("Cronet Error", e);
      }
    }
  }

  private class Callback extends UrlRequest.Callback {
    private static final String TAG = "Callback";

    private Call mCall;
    private Response mResponse;
    @Nullable
    private CronetException mException;

    private FutureTask<Response> mResponseFuture = new FutureTask<>(new Callable<Response>() {
      @Override
      public Response call() throws Exception {
        if (mException != null) {
          throw mException;
        }
        return mResponse;
      }
    });

    private ByteArrayOutputStream mBytesReceived = new ByteArrayOutputStream();
    private WritableByteChannel mReceiveChannel = Channels.newChannel(mBytesReceived);

    Callback(Call call) {
      mCall = call;
      mResponse = new Response.Builder()
              .sentRequestAtMillis(System.currentTimeMillis())
              .request(call.request())
              .protocol(Protocol.HTTP_1_0)
              .code(0)
              .message("")
              .build();
    }

    @Override
    public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
      request.followRedirect();
    }

    @Override
    public void onResponseStarted(UrlRequest request, UrlResponseInfo info) throws NoSuchFieldException, IllegalAccessException {
      String negotiatedProtocol = info.getNegotiatedProtocol().toLowerCase();
      Protocol protocol = Protocol.HTTP_1_0;
      if (negotiatedProtocol.contains("quic")) {
        protocol = Protocol.QUIC;
      } else if (negotiatedProtocol.contains("spdy")) {
        protocol = Protocol.SPDY_3;
      } else if (negotiatedProtocol.contains("h2")) {
        protocol = Protocol.HTTP_2;
      } else if (negotiatedProtocol.contains("1.1")) {
        protocol = Protocol.HTTP_1_1;
      }

      List<Map.Entry<String, String>> headers = info.getAllHeadersAsList();

      Headers.Builder headerBuilder = new Headers.Builder();
      for (Map.Entry<String, String> entry : headers) {
        try {
          if (entry.getKey().equalsIgnoreCase("content-encoding")) {
            // Strip all content encoding headers as decoding is done handled by cronet
            continue;
          }
          headerBuilder.add(entry.getKey(), entry.getValue());
        } catch (Exception e) {
          Log.w(TAG, "Invalid HTTP header/value: " + entry.getKey() + entry.getValue());
          // Ignore that header
        }
      }

      mResponse = mResponse.newBuilder()
              .receivedResponseAtMillis(System.currentTimeMillis())
              .protocol(protocol)
              .code(info.getHttpStatusCode())
              .message(info.getHttpStatusText())
              .headers(headerBuilder.build())
              .build();

      try {
        Field transmitterField = mCall.getClass().getDeclaredField("transmitter");
        transmitterField.setAccessible(true);

        Field eventListenerField = Transmitter.class.getDeclaredField("eventListener");
        eventListenerField.setAccessible(true);

        Transmitter transmitter = (Transmitter)transmitterField.get(mCall);
        EventListener eventListener = (EventListener) eventListenerField.get(transmitter);

        eventListener.responseHeadersEnd(mCall, mResponse);
      } catch (NoSuchFieldException e) {
        Log.e(TAG, "Invalid Reflection. Library may need to be updated");
        throw e;
      } catch (IllegalAccessException e) {
        Log.e(TAG, "Invalid Reflection. Library may need to be updated");
        throw e;
      }

      request.read(ByteBuffer.allocateDirect(32 * 1024));
    }

    @Override
    public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) throws Exception {
      byteBuffer.flip();

      try {
        mReceiveChannel.write(byteBuffer);
      } catch (IOException e) {
        Log.i(TAG, "IOException during ByteBuffer read. Details: ", e);
        throw e;
      }

      byteBuffer.clear();

      request.read(byteBuffer);
    }

    @Override
    public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
      String contentTypeString = mResponse.header("content-type");

      MediaType contentType = MediaType.parse(contentTypeString != null ? contentTypeString : "text/plain; charset=\"utf-8\"");

      ResponseBody responseBody = ResponseBody.create(contentType, mBytesReceived.toByteArray());
      Request newRequest = mResponse.request().newBuilder().url(info.getUrl()).build();
      mResponse = mResponse.newBuilder().body(responseBody).request(newRequest).build();
      mResponseFuture.run();
    }

    @Override
    public void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error) {
      mException = error;
      mResponseFuture.run();
    }
  }
}
