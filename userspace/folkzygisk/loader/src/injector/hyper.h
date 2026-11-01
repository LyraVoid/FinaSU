#ifndef FOLKZYGISK_HYPER_H
#define FOLKZYGISK_HYPER_H

#include <stdbool.h>
#include <stdint.h>

#include "hyper_protocol.h"

struct hyper_module {
  char id[128];
  int fd;
  uint32_t slot;
  bool companion;
};

struct hyper_plan {
  uint32_t generation;
  uint32_t count;
  struct hyper_module modules[FOLKZYGISK_HYPER_MAX_MODULES];
};

bool hyper_load(struct hyper_plan *plan);

#endif
