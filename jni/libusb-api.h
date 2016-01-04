#include <libusb-1.0/libusb.h>


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
int HotplugDeviceArrivedCallback( struct libusb_context* context, 
                                  struct libusb_device* device_libusb, 
                                  struct libusb_hotplug_event event, 
                                  void* data);
int is_interesting(struct Device* device);
int DeviceArrived( struct Device* device);
int OnDeviceArrived(struct Device* device);
int DeviceHandle(struct Device* device);
int UpdateDeviceList();
int OnDeviceListUpdated();
int DeviceConnect(struct Device* device);
int FindEndpoints(struct Device* device);
int PostOutTransfer(libusb_device_handle* device_handle_libusb);
int PostInTransfer(struct Device * device);
void InTransferCallback(struct libusb_transfer* transfer);
void OutTransferCallback(struct libusb_transfer* transfer);
int CAMERACORE_libusb_ReceiveData(struct Device* device);
void OnDeviceConnect();