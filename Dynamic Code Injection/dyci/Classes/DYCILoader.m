//
// Created by ASPCartman on 18/03/2017.
// Copyright (c) 2017 ASPCartman. All rights reserved.
//

#import "DYCILoader.h"
#import <dlfcn.h>
#import <sys/mman.h>
#import <mach/vm_types.h>
#import <mach/vm_map.h>
#import <mach/mach_init.h>
#include <libkern/OSCacheControl.h> // sys_icache_invalidate

static void *(*original_mmap)(void *addr, size_t len, int prot, int flags, int fd, off_t offset);

static void *mmap_hook(void *addr, size_t len, int prot, int flags, int fd, off_t offset)
{
	NSLog(@"mmap %p %lu bytes; prot:%d; flags:%d; fd:%d; offset:%lli", addr, len, prot, flags, fd, offset);
	return original_mmap(addr, len, prot, flags, fd, offset);
}


@implementation DYCILoader
{
	dispatch_once_t _hookMemoryMapCall;
}

+ (void) load
{
//	evil_init();
//	kern_return_t i = evil_override_ptr(mmap, mmap_hook, (void **) original_mmap);
//	NSLog(@"MMAP HOOK override returned %d", i);
//
//	int rebindError = rebind_symbols((struct rebinding[]) { { "mmap", (void *) mmap_hook, (void **) &original_mmap } }, 1);
//	if (rebindError == 0) {
//		NSLog(@"mmap() has been hooked");
//	} else {
//		NSLog(@"Failed to hook mmap()");
//	}
}

void test()
{
	NSLog(@"Бугагашеньки");
}


+ (instancetype) loader
{
	static dispatch_once_t _singletone;
	static DYCILoader      *loader = nil;
	dispatch_once(&_singletone, ^{
		loader = [DYCILoader new];
	});
	return loader;
}

- (void) load:(NSData *)data
{
	dispatch_async(dispatch_get_main_queue(), ^{
		[self _load:data];
	});
}

- (void) _load:(NSData *)data
{
	NSError  *error;
	NSString *path = [self writeInjectionDataToFile:data error:&error];
	if (error != nil) {
		NSLog(@"Failed writing injection data to temporary file: %@", error);
		return;
	}

	void *handle = dlopen([path cStringUsingEncoding:NSUTF8StringEncoding], RTLD_NOW | RTLD_GLOBAL);
	if (!handle) {
		NSLog(@"Failed to dlopen injection: %s", dlerror());
		return;
	}
}

- (NSString *) writeInjectionDataToFile:(NSData *)data error:(NSError **)error
{
	NSString *path = [NSTemporaryDirectory() stringByAppendingPathComponent:@"injection.dylib"];
	[data writeToFile:path options:0 error:error];
	return path;
}
@end