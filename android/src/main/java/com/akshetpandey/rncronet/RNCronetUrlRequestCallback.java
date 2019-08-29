package com.akshetpandey.rncronet;

import android.os.ConditionVariable;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.modules.network.OkHttpClientProvider;

import org.chromium.net.CronetException;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.EventListener;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.connection.Transmitter;

class RNCronetUrlRequestCallback extends UrlRequest.Callback {
  private static final String TAG = "Callback";

  private static final int MAX_FOLLOW_COUNT = 20;

  private Request originalRequest;
  private Call mCall;
  private EventListener eventListener;
  @Nullable
  private Callback responseCallback;

  private int followCount;
  private Response mResponse;

  @Nullable
  private IOException mException;
  private ConditionVariable mResponseConditon = new ConditionVariable();

  private ByteArrayOutputStream mBytesReceived = new ByteArrayOutputStream();
  private WritableByteChannel mReceiveChannel = Channels.newChannel(mBytesReceived);

  RNCronetUrlRequestCallback(Request request, Call call) {
    this(request, call, null, null);
  }

  RNCronetUrlRequestCallback(Request request, Call call, @Nullable EventListener eventListener, @Nullable Callback responseCallback) {
    originalRequest = request;
    mCall = call;
    mResponse = new Response.Builder()
            .sentRequestAtMillis(System.currentTimeMillis())
            .request(request)
            .protocol(Protocol.HTTP_1_0)
            .code(0)
            .message("")
            .build();
    this.responseCallback = responseCallback;

    if (eventListener != null) {
      this.eventListener = eventListener;
    } else {
      try {
        Field transmitterField = call.getClass().getDeclaredField("transmitter");
        transmitterField.setAccessible(true);

        Field eventListenerField = Transmitter.class.getDeclaredField("eventListener");
        eventListenerField.setAccessible(true);

        Transmitter transmitter = (Transmitter) transmitterField.get(mCall);
        this.eventListener = (EventListener) eventListenerField.get(transmitter);
      } catch (NoSuchFieldException e) {
        Log.e(TAG, "Invalid Reflection. Library may need to be updated");
      } catch (IllegalAccessException e) {
        Log.e(TAG, "Invalid Reflection. Library may need to be updated");
      }
    }
  }

  private static Protocol protocolFromNegotiatedProtocol(UrlResponseInfo responseInfo) {
    String negotiatedProtocol = responseInfo.getNegotiatedProtocol().toLowerCase();
    if (negotiatedProtocol.contains("quic")) {
      return Protocol.QUIC;
    } else if (negotiatedProtocol.contains("spdy")) {
      return Protocol.SPDY_3;
    } else if (negotiatedProtocol.contains("h2")) {
      return Protocol.HTTP_2;
    } else if (negotiatedProtocol.contains("1.1")) {
      return Protocol.HTTP_1_1;
    } else {
      return Protocol.HTTP_1_0;
    }
  }

  private static Headers headersFromResponse(UrlResponseInfo responseInfo) {
    List<Map.Entry<String, String>> headers = responseInfo.getAllHeadersAsList();

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

    return headerBuilder.build();
  }

  private static Response responseFromResponse(Response response, UrlResponseInfo responseInfo) {
    Protocol protocol = protocolFromNegotiatedProtocol(responseInfo);
    Headers headers = headersFromResponse(responseInfo);

    return response.newBuilder()
            .receivedResponseAtMillis(System.currentTimeMillis())
            .protocol(protocol)
            .code(responseInfo.getHttpStatusCode())
            .message(responseInfo.getHttpStatusText())
            .headers(headers)
            .build();
  }

  Response waitForDone() throws IOException {
    mResponseConditon.block();
    if (mException != null) {
      throw mException;
    }
    return mResponse;
  }

  @Override
  public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
    if (followCount > MAX_FOLLOW_COUNT) {
      request.cancel();
    }
    followCount += 1;
    OkHttpClient client = OkHttpClientProvider.createClient();
    if (originalRequest.url().isHttps() && newLocationUrl.startsWith("http://") && client.followSslRedirects()) {
      request.followRedirect();
    } else if (!originalRequest.url().isHttps() && newLocationUrl.startsWith("https://") && client.followSslRedirects()) {
      request.followRedirect();
    } else if (client.followRedirects()) {
      request.followRedirect();
    } else {
      request.cancel();
    }
  }

  @Override
  public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
    mResponse = responseFromResponse(mResponse, info);

    eventListener.responseHeadersEnd(mCall, mResponse);
    eventListener.responseBodyStart(mCall);

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
    eventListener.responseBodyEnd(mCall, info.getReceivedByteCount());

    String contentTypeString = mResponse.header("content-type");
    MediaType contentType = MediaType.parse(contentTypeString != null ? contentTypeString : "text/plain; charset=\"utf-8\"");

    ResponseBody responseBody = ResponseBody.create(contentType, mBytesReceived.toByteArray());
    Request newRequest = originalRequest.newBuilder().url(info.getUrl()).build();
    mResponse = mResponse.newBuilder().body(responseBody).request(newRequest).build();

    mResponseConditon.open();

    eventListener.callEnd(mCall);

    if (responseCallback != null) {
      try {
        responseCallback.onResponse(mCall, mResponse);
      } catch (IOException e) {
        // Pass?
      }
    }
  }

  @Override
  public void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error) {
    IOException e = new IOException("Cronet Exception Occurred", error);
    mException = e;
    mResponseConditon.open();
    eventListener.callFailed(mCall, e);
    if (responseCallback != null) {
      responseCallback.onFailure(mCall, e);
    }
  }

  @Override
  public void onCanceled(UrlRequest request, UrlResponseInfo info) {
    mResponseConditon.open();
    eventListener.callEnd(mCall);
  }
}
