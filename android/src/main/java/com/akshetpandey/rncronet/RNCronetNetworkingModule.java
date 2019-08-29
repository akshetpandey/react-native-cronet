package com.akshetpandey.rncronet;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.modules.network.OkHttpClientFactory;
import com.facebook.react.modules.network.OkHttpClientProvider;
import com.facebook.react.modules.network.ReactCookieJarContainer;
import com.facebook.soloader.SoLoader;
import com.google.android.gms.net.CronetProviderInstaller;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.chromium.net.CronetEngine;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

public class RNCronetNetworkingModule extends ReactContextBaseJavaModule {

  private static OkHttpClient sharedClient;
  private static CustomCronetBuilder customCronetBuilder;
  private static CronetEngine cronetEngine;
  private static ExecutorService executorService = Executors.newSingleThreadExecutor();

  static {
    OkHttpClientProvider.setOkHttpClientFactory(new OkHttpClientFactory() {
      @Override
      public OkHttpClient createNewNetworkModuleClient() {
        if (sharedClient != null) {
          return sharedClient;
        }

        OkHttpClient.Builder clientBuilder =
                new OkHttpClient.Builder()
                        .connectTimeout(0, TimeUnit.MILLISECONDS)
                        .readTimeout(0, TimeUnit.MILLISECONDS)
                        .writeTimeout(0, TimeUnit.MILLISECONDS)
                        .cookieJar(new ReactCookieJarContainer())
                        .addInterceptor(new RNCronetInterceptor());

        try {
          Class ConscryptProvider = Class.forName("org.conscrypt.OpenSSLProvider");
          Security.insertProviderAt((Provider) ConscryptProvider.newInstance(), 1);

          sharedClient = clientBuilder.build();
        } catch (Exception e) {
          sharedClient = OkHttpClientProvider.enableTls12OnPreLollipop(clientBuilder).build();
        }

        return sharedClient;
      }
    });
  }

  private final ReactApplicationContext mReactContext;

  RNCronetNetworkingModule(ReactApplicationContext reactContext) {
    super(reactContext);
    mReactContext = reactContext;

    CronetProviderInstaller.installProvider(reactContext).addOnCompleteListener(new OnCompleteListener<Void>() {
      @Override
      public void onComplete(@NonNull Task<Void> task) {
        if (task.isSuccessful()) {
          initializeCronetEngine(mReactContext);
        }
      }
    });
  }

  public static void setCustomCronetBuilder(CustomCronetBuilder builder) {
    customCronetBuilder = builder;
  }

  public static CronetEngine cronetEngine() {
    return cronetEngine;
  }

  private static synchronized void initializeCronetEngine(ReactApplicationContext reactContext) {
    if (cronetEngine == null) {
      if (customCronetBuilder != null) {
        cronetEngine = customCronetBuilder.build(reactContext);
      } else {
        File cacheDir = new File(reactContext.getCacheDir(), "cronet-cache");
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        cronetEngine = new CronetEngine.Builder(reactContext)
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
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024) // 10 MegaBytes
                .build();
        URL.setURLStreamHandlerFactory(cronetEngine.createURLStreamHandlerFactory());
      }
    }
  }

  static UrlRequest buildRequest(Request request, UrlRequest.Callback callback) throws IOException {
    String url = request.url().toString();

    UrlRequest.Builder requestBuilder = cronetEngine.newUrlRequestBuilder(url, callback, executorService);
    requestBuilder.setHttpMethod(request.method());

    Headers headers = request.headers();
    for (int i = 0; i < headers.size(); i += 1) {
      requestBuilder.addHeader(headers.name(i), headers.value(i));
    }

    RequestBody requestBody = request.body();
    if (requestBody != null) {
      MediaType contentType = requestBody.contentType();
      if (contentType != null) {
        requestBuilder.addHeader("Content-Type", contentType.toString());
      }
      Buffer buffer = new Buffer();
      requestBody.writeTo(buffer);
      requestBuilder.setUploadDataProvider(UploadDataProviders.create(buffer.readByteArray()), executorService);
    }

    return requestBuilder.build();
  }


  @Override
  @NonNull
  public String getName() {
    return "RNCronet";
  }

  public interface CustomCronetBuilder {
    CronetEngine build(ReactApplicationContext context);
  }
}
