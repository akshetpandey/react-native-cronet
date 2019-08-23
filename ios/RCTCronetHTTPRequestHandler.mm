//
//  RCTCronetHTTPRequestHandler.m
//  RNCronet
//
//  Created by Akshet Pandey on 8/21/19.
//  Copyright © 2019 Facebook. All rights reserved.
//

#import "RCTCronetHTTPRequestHandler.h"

#import <mutex>

#import <React/RCTNetworking.h>
#import <Cronet/Cronet.h>

@interface RCTCronetHTTPRequestHandler () <NSURLSessionDataDelegate>

@end

@implementation RCTCronetHTTPRequestHandler {
  NSMapTable *_delegates;
  NSURLSession *_session;
  std::mutex _mutex;
}

@synthesize bridge = _bridge;
@synthesize methodQueue = _methodQueue;

RCT_EXPORT_MODULE()

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

- (instancetype)init {
  if (self = [super init]) {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
      [Cronet setHttp2Enabled:YES];
      [Cronet setQuicEnabled:YES];
      [Cronet setBrotliEnabled:YES];
      [Cronet setAcceptLanguages:@"en-US,en"];

      [Cronet setMetricsEnabled:YES];

      [Cronet start];

#if RNCRONET_REGISTER_DEFAULT_HANDLERS
      [Cronet registerHttpProtocolHandler];
#endif
    });
  }
  return self;
}

- (void)invalidate
{
  std::lock_guard<std::mutex> lock(_mutex);
  [self->_session invalidateAndCancel];
  self->_session = nil;
}

// Needs to lock before call this method.
- (BOOL)isValid
{
  // if session == nil and delegates != nil, we've been invalidated
  return _session || !_delegates;
}

- (float)handlerPriority {
  return 1.0;
}

#pragma mark - NSURLRequestHandler

- (BOOL)canHandleRequest:(NSURLRequest *)request
{
  static NSSet<NSString *> *schemes = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    // technically, RCTHTTPRequestHandler can handle file:// as well,
    // but it's less efficient than using RCTFileRequestHandler
    schemes = [[NSSet alloc] initWithObjects:@"http", @"https", nil];
  });
  return [schemes containsObject:request.URL.scheme.lowercaseString];
}

- (NSURLSessionDataTask *)sendRequest:(NSURLRequest *)request
                         withDelegate:(id<RCTURLRequestDelegate>)delegate
{
  std::lock_guard<std::mutex> lock(_mutex);
  // Lazy setup
  if (!_session && [self isValid]) {
    // You can override default NSURLSession instance property allowsCellularAccess (default value YES)
    //  by providing the following key to your RN project (edit ios/project/Info.plist file in Xcode):
    // <key>ReactNetworkForceWifiOnly</key>    <true/>
    // This will set allowsCellularAccess to NO and force Wifi only for all network calls on iOS
    // If you do not want to override default behavior, do nothing or set key with value false
    NSDictionary *infoDictionary = [[NSBundle mainBundle] infoDictionary];
    NSNumber *useWifiOnly = [infoDictionary objectForKey:@"ReactNetworkForceWifiOnly"];
    
    NSOperationQueue *callbackQueue = [NSOperationQueue new];
    callbackQueue.maxConcurrentOperationCount = 1;
    callbackQueue.underlyingQueue = [[_bridge networking] methodQueue];
    NSURLSessionConfiguration *configuration = [NSURLSessionConfiguration defaultSessionConfiguration];
    // Set allowsCellularAccess to NO ONLY if key ReactNetworkForceWifiOnly exists AND its value is YES
    if (useWifiOnly) {
      configuration.allowsCellularAccess = ![useWifiOnly boolValue];
    }
    [configuration setHTTPShouldSetCookies:YES];
    [configuration setHTTPCookieAcceptPolicy:NSHTTPCookieAcceptPolicyAlways];
    [configuration setHTTPCookieStorage:[NSHTTPCookieStorage sharedHTTPCookieStorage]];
    [Cronet installIntoSessionConfiguration:configuration];
    _session = [NSURLSession sessionWithConfiguration:configuration
                                             delegate:self
                                        delegateQueue:callbackQueue];

    _delegates = [[NSMapTable alloc] initWithKeyOptions:NSPointerFunctionsStrongMemory
                                           valueOptions:NSPointerFunctionsStrongMemory
                                               capacity:0];
  }
  NSURLSessionDataTask *task = [_session dataTaskWithRequest:request];
  [_delegates setObject:delegate forKey:task];
  [task resume];
  return task;
}

- (void)cancelRequest:(NSURLSessionDataTask *)task
{
  {
    std::lock_guard<std::mutex> lock(_mutex);
    [_delegates removeObjectForKey:task];
  }
  [task cancel];
}

#pragma mark - NSURLSession delegate

- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
   didSendBodyData:(int64_t)bytesSent
    totalBytesSent:(int64_t)totalBytesSent
totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend
{
  id<RCTURLRequestDelegate> delegate;
  {
    std::lock_guard<std::mutex> lock(_mutex);
    delegate = [_delegates objectForKey:task];
  }
  [delegate URLRequest:task didSendDataWithProgress:totalBytesSent];
}

- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
willPerformHTTPRedirection:(NSHTTPURLResponse *)response
        newRequest:(NSURLRequest *)request
 completionHandler:(void (^)(NSURLRequest *))completionHandler
{
  // Reset the cookies on redirect.
  // This is necessary because we're not letting iOS handle cookies by itself
  NSMutableURLRequest *nextRequest = [request mutableCopy];
  
  NSArray<NSHTTPCookie *> *cookies = [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookiesForURL:request.URL];
  nextRequest.allHTTPHeaderFields = [NSHTTPCookie requestHeaderFieldsWithCookies:cookies];
  completionHandler(nextRequest);
}

- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)task
didReceiveResponse:(NSURLResponse *)response
 completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler
{
  id<RCTURLRequestDelegate> delegate;
  {
    std::lock_guard<std::mutex> lock(_mutex);
    delegate = [_delegates objectForKey:task];
  }
  [delegate URLRequest:task didReceiveResponse:response];
  completionHandler(NSURLSessionResponseAllow);
}

- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)task
    didReceiveData:(NSData *)data
{
  id<RCTURLRequestDelegate> delegate;
  {
    std::lock_guard<std::mutex> lock(_mutex);
    delegate = [_delegates objectForKey:task];
  }
  [delegate URLRequest:task didReceiveData:data];
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error
{
  id<RCTURLRequestDelegate> delegate;
  {
    std::lock_guard<std::mutex> lock(_mutex);
    delegate = [_delegates objectForKey:task];
    [_delegates removeObjectForKey:task];
  }
  [delegate URLRequest:task didCompleteWithError:error];
}

@end
