#pragma once
#include <vulkan/vulkan.h>

struct VulkanBridge {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamily = UINT32_MAX;

    bool init();
    void shutdown();
    const char* gpuName() const;
};
