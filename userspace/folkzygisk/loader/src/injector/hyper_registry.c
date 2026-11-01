#include "hyper_registry.h"

#include <string.h>

int hyper_registry_add(struct hyper_registry *registry,
                       const struct ZygiskNextHyosModule *module) {
  if (registry == NULL || module == NULL || module->onAppSpecialized == NULL
      || module->target_api_version <= 0
      || module->target_api_version > ZYGISK_NEXT_HYOS_API_VERSION) {
    return ZN_FAILED;
  }
  for (size_t i = 0; i < registry->count; i++) {
    if (registry->modules[i].onAppSpecialized == module->onAppSpecialized) {
      return ZN_FAILED;
    }
  }
  if (registry->count == HYPER_MAX_RUNTIME_MODULES) return ZN_FAILED;

  registry->modules[registry->count++] = *module;
  return ZN_SUCCESS;
}

void hyper_registry_deliver(struct hyper_registry *registry,
                            const struct ZnHyosAppSpecializeArgs *args) {
  if (registry == NULL || args == NULL) return;
  for (size_t i = 0; i < registry->count; i++) {
    registry->modules[i].onAppSpecialized(args);
  }
}
