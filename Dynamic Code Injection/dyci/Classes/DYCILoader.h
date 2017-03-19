//
// Created by ASPCartman on 18/03/2017.
// Copyright (c) 2017 ASPCartman. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface DYCILoader : NSObject
+ (instancetype) loader;
- (void) load:(NSData*)data;
@end