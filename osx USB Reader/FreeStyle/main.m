//
//  main.m
//  FreeStyle
//
//  Created by Victor on 3/11/14.
//  Copyright (c) 2014 SocialDiabetes, SL. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <IOKit/hid/IOHIDLib.h>
#include <stdlib.h>


void MyInputCallback(void *context, IOReturn result, void *sender,
                     IOHIDReportType type, uint32_t reportID, uint8_t *report, CFIndex reportLength)
{

    NSMutableString *t = [NSMutableString stringWithCapacity:reportLength];
    for (NSUInteger i = 0; i < reportLength; ++i)
        [t appendFormat:@"%c", report[i]];
    NSLog(@"String: %@ Tamaño: %ld", t, reportLength);
    
    for (int i=0; i < reportLength; i++) {
        NSLog(@"%02x %hhu %c", report[i], report[i], report[i]);
     //   [hex appendFormat:@"%02x", [report bytes][i]];
    }
}

int main(int argc, const char * argv[]) {
    @autoreleasepool {
        const long productId = 0x3650;
        const long vendorId = 0x1a61;
    

        NSMutableDictionary *dict = [NSMutableDictionary dictionary];
        [dict setObject:[NSNumber numberWithLong:productId] forKey:[NSString
                                                                    stringWithCString:kIOHIDProductIDKey encoding:NSUTF8StringEncoding]];
        [dict setObject:[NSNumber numberWithLong:vendorId] forKey:[NSString
                                                                   stringWithCString:kIOHIDVendorIDKey encoding:NSUTF8StringEncoding]];

        
        
        IOHIDManagerRef managerRef = IOHIDManagerCreate(kCFAllocatorDefault,
                                                        kIOHIDOptionsTypeNone);
        IOHIDManagerScheduleWithRunLoop(managerRef, CFRunLoopGetMain(),
                                        kCFRunLoopDefaultMode);
        IOHIDManagerOpen(managerRef, 0L);
        
        //2) Get your device:
        
        
        
        [dict setObject:[NSNumber numberWithLong:productId] forKey:[NSString
                                                                    stringWithCString:kIOHIDProductIDKey encoding:NSUTF8StringEncoding]];
        [dict setObject:[NSNumber numberWithLong:vendorId] forKey:[NSString
                                                                   stringWithCString:kIOHIDVendorIDKey encoding:NSUTF8StringEncoding]];
        
        IOHIDManagerSetDeviceMatching(managerRef, (CFMutableDictionaryRef)CFBridgingRetain(dict)); NSSet *allDevices = ((NSSet *)CFBridgingRelease(IOHIDManagerCopyDevices(managerRef)));
        
        NSArray *deviceRefs = [allDevices allObjects];
        IOHIDDeviceRef deviceRef = ([deviceRefs count]) ? (__bridge IOHIDDeviceRef)[deviceRefs objectAtIndex:0] : nil;
        
        if (deviceRef != NULL) {
            
            CFStringRef manufacturer, product_name;
            
            char string_buffer[1024];
            
            if ((manufacturer = (CFStringRef)IOHIDDeviceGetProperty(deviceRef, CFSTR(kIOHIDManufacturerKey)))!= NULL)
            {
                CFStringGetCString(manufacturer, string_buffer, sizeof(string_buffer), kCFStringEncodingUTF8);
                NSLog(@"Manufacturer: %s", string_buffer);
            }
            
            /*
             Get the product name (which is a string)
             */
            if ((product_name = (CFStringRef)IOHIDDeviceGetProperty(deviceRef, CFSTR(kIOHIDProductKey))) != NULL)
            {
                CFStringGetCString(product_name, string_buffer, sizeof(string_buffer), kCFStringEncodingUTF8);
                NSLog(@"Product Name: %s", string_buffer);
            }
            
            NSLog(@"%@", deviceRef);
            //4) Send your message to the device (I'm assuming report ID 0):
            
            size_t bufferSize = 64;
            char *inputBuffer = malloc(bufferSize);
            char *outputBuffer = malloc(3);
            
            for (int i = 0; i < 64; i++ )
            {
                inputBuffer[i] = 0x00;
            }
            
            outputBuffer[0] = 0x6d;
            outputBuffer[1] = 0x65;
            outputBuffer[2] = 0x6d;
            
            
            IOHIDDeviceRegisterInputReportCallback(deviceRef, (uint8_t *)inputBuffer, bufferSize, MyInputCallback, NULL);
            
            
            IOHIDDeviceSetReport(deviceRef, kIOHIDReportTypeOutput, 0, (uint8_t *)outputBuffer, 1);
            
            [[NSRunLoop mainRunLoop] run];
            
        } else {
            NSLog(@"Glucometer not found, connect and try again");
        }
        
    }
    return 0;
}
