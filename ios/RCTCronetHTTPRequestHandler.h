//
//  RCTCronetHTTPRequestHandler.h
//  RNCronet
//
//  Created by Akshet Pandey on 8/21/19.
//  Copyright © 2019 Facebook. All rights reserved.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTInvalidating.h>
#import <React/RCTURLRequestHandler.h>

NS_ASSUME_NONNULL_BEGIN

@interface RCTCronetHTTPRequestHandler : NSObject <RCTURLRequestHandler, RCTInvalidating>

@end

NS_ASSUME_NONNULL_END
