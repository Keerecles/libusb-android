#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/wait.h>
#include <pthread.h>
#include <errno.h>
//#include <dlt/dlt.h>
#include <libusb-1.0/libusb.h>
#include "cameradae.h" 


#define in_endpoint_max_packet_size		2764800  //The sise of one frame of 720P video is 2764800Byte
#define out_endpoint_max_packet_size	2764800
#define kUsbConfiguration			   	    1
#define kAoaVid  						          0x18d1
#define kAoaPid1  						        0x2d00
#define kAoaPid2  						        0x2d01
#define kAoaInterfaceSubclass  			  0xff

#define kAppleVid  						0x05ac
#define kApplePid1  					0x1290 // iPhone
#define kApplePid2  					0x1292 // iPhone 3G
#define kApplePid3  					0x1294 // iPhone 3GS
#define kApplePid4  					0x1297 // iPhone 4
#define kApplePid5  					0x129a // iPad
#define kApplePid6  					0x129f // iPad 2
#define kApplePid7  					0x12a0 // iPhone 4S
#define kApplePid8  					0x12a8 // iPhone 5


struct Device
{
  	uint8_t bus_number;
  	uint8_t device_address;
  	uint16_t vendor_id;
  	uint16_t product_id;
  	uint8_t in_endpoint;
	  uint8_t out_endpoint;
    struct libusb_context* libusb_context_cameracore;
  	struct libusb_device_descriptor* device_descriptor;
    struct libusb_device_handle* device_handle_libusb;
  	struct libusb_device* device_libusb;
};


int CAMERACORE_libusb_init(struct Device* device);

int HotplugDeviceArrivedCallback( libusb_context* context, 
                                  libusb_device* device_libusb, 
                                  enum libusb_hotplug_event event, 
                                  void* data);

int HotplugDeviceLifedCallback(   libusb_context* context, 
                                  libusb_device* device_libusb, 
                                  enum libusb_hotplug_event event, void* data);

int DeviceArrived(  struct Device* device);
int OnDeviceArrived(struct Device* device);

int DeviceLifed(  struct Device* device);

int TurnIntoAccessoryMode(struct Device* device);

int IsGoogleAccessory(const struct Device* device);

int IsAppleDevice(const struct Device* device);

int GoogleDeviceHandle(struct Device* device);

int AppleDeviceHandle(struct Device* device);

int DeviceHandle(struct Device* device);

int UpdateDeviceList();

int OnDeviceListUpdated();
int DeviceConnect();

int FindEndpoints();
int PostOutTransfer(libusb_device_handle* device_handle_libusb);

int PostInTransfer(libusb_device_handle* device_handle_libusb);

void InTransferCallback(struct libusb_transfer* transfer);

void OutTransferCallback(struct libusb_transfer* transfer);

int CAMERACORE_libusb_SendData();

void CloseDeviceHandle(libusb_device_handle* device_handle_libusb);

void AbortConnection();

void OnDeviceConnect();






