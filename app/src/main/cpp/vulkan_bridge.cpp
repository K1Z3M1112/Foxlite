#include "vulkan_bridge.h"
#include <cstring>

bool VulkanBridge::init() {
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "NativeWinRuntime";
    app.applicationVersion = 1;
    app.pEngineName = "NativeWinRuntime";
    app.engineVersion = 1;
    app.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ci.pApplicationInfo = &app;
    if (vkCreateInstance(&ci, nullptr, &instance) != VK_SUCCESS) return false;

    uint32_t count = 0;
    if (vkEnumeratePhysicalDevices(instance, &count, nullptr) != VK_SUCCESS || count == 0)
        return false;

    VkPhysicalDevice devices[16]{};
    count = count > 16 ? 16 : count;
    vkEnumeratePhysicalDevices(instance, &count, devices);

    physicalDevice = devices[0];
    uint32_t qcount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &qcount, nullptr);
    VkQueueFamilyProperties q[32]{};
    qcount = qcount > 32 ? 32 : qcount;
    vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &qcount, q);

    for (uint32_t i = 0; i < qcount; ++i) {
        if (q[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
            graphicsQueueFamily = i;
            break;
        }
    }
    return graphicsQueueFamily != UINT32_MAX;
}

void VulkanBridge::shutdown() {
    if (device) vkDestroyDevice(device, nullptr);
    device = VK_NULL_HANDLE;
    if (instance) vkDestroyInstance(instance, nullptr);
    instance = VK_NULL_HANDLE;
}

const char* VulkanBridge::gpuName() const {
    static char name[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE]{};
    if (!physicalDevice) return "unknown";
    VkPhysicalDeviceProperties p{};
    vkGetPhysicalDeviceProperties(physicalDevice, &p);
    std::strncpy(name, p.deviceName, sizeof(name) - 1);
    return name;
}
