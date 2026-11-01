#ifndef FOLKZYGISK_HYPER_REGISTRY_H
#define FOLKZYGISK_HYPER_REGISTRY_H

#include <stdbool.h>
#include <stddef.h>

#include "zygisk_next_api.h"

#define HYPER_MAX_RUNTIME_MODULES 8

struct hyper_registry {
  struct ZygiskNextHyosModule modules[HYPER_MAX_RUNTIME_MODULES];
  size_t count;
};

int hyper_registry_add(struct hyper_registry *registry,
                       const struct ZygiskNextHyosModule *module);

void hyper_registry_deliver(struct hyper_registry *registry,
                            const struct ZnHyosAppSpecializeArgs *args);

#endif
