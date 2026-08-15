#include <jni.h>
#include <string>
#include <sys/system_properties.h>
#include <vulkan/vulkan.h>

static std::string prop(const char* key) {
    char v[PROP_VALUE_MAX]{};
    __system_property_get(key, v);
    return std::string(v);
}

static std::string gpuName() {
    VkInstance instance = VK_NULL_HANDLE;
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "NativeWinRuntime";
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ci.pApplicationInfo = &app;
    if (vkCreateInstance(&ci, nullptr, &instance) != VK_SUCCESS) return "Vulkan unavailable";
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    std::string result = count ? "Vulkan GPU detected" : "No Vulkan GPU";
    if (count) {
        VkPhysicalDevice pd{};
        vkEnumeratePhysicalDevices(instance, &count, &pd);
        VkPhysicalDeviceProperties p{};
        vkGetPhysicalDeviceProperties(pd, &p);
        result = p.deviceName;
    }
    vkDestroyInstance(instance, nullptr);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nativewinruntime_MainActivity_nativeRuntimeInfo(JNIEnv* env, jobject) {
    std::string s;
    s += "SoC: " + prop("ro.soc.manufacturer") + " " + prop("ro.soc.model") + "\n";
    s += "GPU: " + gpuName() + "\n";
    s += "Graphics: Android Vulkan direct path\n";
    s += "CPU: ARM64 host + Box64/FEX target\n";
    s += "Windows API: Wine target\n";
    s += "D3D: DXVK / VKD3D-Proton target\n";
    s += "GPU emulation: OFF\n";
    return env->NewStringUTF(s.c_str());
}
