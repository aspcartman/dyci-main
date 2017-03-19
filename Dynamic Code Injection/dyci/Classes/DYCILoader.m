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

#if __thumb__
#define START_OF_FUNC(x) ((void*)((long)x & (-2)))
#define ADDR_FROM_BLOCK(x) ((void*)((long)x | 1))
#else
#define START_OF_FUNC(x) (x)
#define ADDR_FROM_BLOCK(x) (x)
#endif

- (void) testInjection
{
// now try to create a page where foo() was
	vm_address_t  addr = 0;
	kern_return_t r    = vm_allocate(mach_task_self(), &addr, 4096, VM_FLAGS_ANYWHERE);
	if (r != KERN_SUCCESS) {
		NSLog(@"vm_allocate returned %d", r);
		return;
	}

	void *codeBlock = (void *) (addr);
	memcpy(codeBlock, START_OF_FUNC(test), 4096);
	sys_dcache_flush(codeBlock, 4096);
	sys_icache_invalidate(codeBlock, 4096);
	mprotect(codeBlock, 4096, PROT_READ | PROT_EXEC);
	vm_protect(mach_task_self(), addr, 4096, PAGE_SIZE, PROT_READ | PROT_EXEC);

	void (*func)() = ADDR_FROM_BLOCK(codeBlock);
	func();
}
/*
extern kern_return_t vm_region
		(
				vm_map_t target_task,
				vm_address_t *address,
				vm_size_t *size,
				vm_region_flavor_t flavor,
				vm_region_info_t info,
				mach_msg_type_number_t *infoCnt,
				mach_port_t *object_name
		) __attribute__((weak_import, weak));

#define kerncall(x) ({ \
    kern_return_t _kr = (x); \
    if(_kr != KERN_SUCCESS) \
        fprintf(stderr, "%s failed with error code: 0x%x\n", #x, _kr); \
    _kr; \
})

bool patch32(void *dst, void* data, uint len)
{
	mach_port_t                 task;
	vm_region_basic_info_data_t info;
	mach_msg_type_number_t      info_count = VM_REGION_BASIC_INFO_COUNT;
	vm_region_flavor_t          flavor     = VM_REGION_BASIC_INFO;

	vm_address_t region      = (vm_address_t) dst;
	vm_size_t    region_size = 0;

	*//* Get region boundaries *//*
	if (kerncall(vm_region(mach_task_self(), &region, &region_size, flavor, (vm_region_info_t) &info, (mach_msg_type_number_t *) &info_count, (mach_port_t *) &task))) {return false;}
	*//* Change memory protections to rw- *//*
	if (kerncall(vm_protect(mach_task_self(), region, region_size, false, VM_PROT_READ | VM_PROT_WRITE | VM_PROT_COPY))) {return false;}

	*//* Actually perform the write *//*
	memcpy(region, START_OF_FUNC(test), len);

	*//* Flush CPU data cache to save write to RAM *//*
	sys_dcache_flush(dst, region_size);
	*//* Invalidate instruction cache to make the CPU read patched instructions from RAM *//*
	sys_icache_invalidate(dst, region_size);

	*//* Change memory protections back to r-x *//*
	kerncall(vm_protect(mach_task_self(), region, region_size, false, VM_PROT_EXECUTE | VM_PROT_READ));
	return true;
}*/

- (NSString *) writeInjectionDataToFile:(NSData *)data error:(NSError **)error
{
	NSString *path = [NSTemporaryDirectory() stringByAppendingPathComponent:@"injection.dylib"];
	[data writeToFile:path options:0 error:error];
	return path;
}
@end