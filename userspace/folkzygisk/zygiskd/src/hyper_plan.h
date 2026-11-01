#ifndef FOLKZYGISK_HYPER_PLAN_H
#define FOLKZYGISK_HYPER_PLAN_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "hyper_protocol.h"

struct hyper_plan_module {
  char id[128];
  int fd;
  uint32_t slot;
  bool companion;
};

struct hyper_plan {
  uint32_t generation;
  uint32_t count;
  struct hyper_plan_module modules[FOLKZYGISK_HYPER_MAX_MODULES];
};

bool hyper_plan_build(const char *modules_root, const char *target,
                      struct hyper_plan *plan, char *reason, size_t reason_size);

void hyper_plan_free(struct hyper_plan *plan);

#endif
