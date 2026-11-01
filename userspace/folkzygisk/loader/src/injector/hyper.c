#include "hyper.h"

#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <csoloader.h>
#include <plti.h>

#include "daemon.h"
#include "hyper_registry.h"
#include "logging.h"
#include "misc.h"
#include "zygisk_next_api.h"

#define HYPER_MAX_LOADED_MODULES FOLKZYGISK_HYPER_MAX_MODULES
#define ZN_HANDLE_MAGIC 0x5a4e4831u

#define ALIGN_UP(value, alignment) (((value) + (alignment) - 1) & ~((alignment) - 1))

_Static_assert(sizeof(struct ZygiskNextAPI) == 10 * sizeof(void *),
               "ZygiskNextAPI layout changed");
_Static_assert(offsetof(struct ZygiskNextAPI, pltHook) == 0 * sizeof(void *),
               "ZygiskNextAPI pltHook position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, inlineHook) == 1 * sizeof(void *),
               "ZygiskNextAPI inlineHook position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, inlineUnhook) == 2 * sizeof(void *),
               "ZygiskNextAPI inlineUnhook position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, newSymbolResolver) == 3 * sizeof(void *),
               "ZygiskNextAPI newSymbolResolver position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, freeSymbolResolver) == 4 * sizeof(void *),
               "ZygiskNextAPI freeSymbolResolver position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, getBaseAddress) == 5 * sizeof(void *),
               "ZygiskNextAPI getBaseAddress position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, symbolLookup) == 6 * sizeof(void *),
               "ZygiskNextAPI symbolLookup position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, forEachSymbols) == 7 * sizeof(void *),
               "ZygiskNextAPI forEachSymbols position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, connectCompanion) == 8 * sizeof(void *),
               "ZygiskNextAPI connectCompanion position changed");
_Static_assert(offsetof(struct ZygiskNextAPI, getRuntime) == 9 * sizeof(void *),
               "ZygiskNextAPI getRuntime position changed");
_Static_assert(sizeof(struct ZygiskNextModule) == 2 * sizeof(void *),
               "ZygiskNextModule layout changed");
_Static_assert(offsetof(struct ZygiskNextModule, onModuleLoaded) == sizeof(void *),
               "ZygiskNextModule callback position changed");
_Static_assert(sizeof(struct ZnHyosAppSpecializeArgs) == 3 * sizeof(void *),
               "ZnHyosAppSpecializeArgs layout changed");
_Static_assert(sizeof(struct ZygiskNextHyosModule) == 2 * sizeof(void *),
               "ZygiskNextHyosModule layout changed");
_Static_assert(offsetof(struct ZygiskNextHyosModule, onAppSpecialized) == sizeof(void *),
               "ZygiskNextHyosModule callback position changed");
_Static_assert(sizeof(struct ZygiskNextRuntime)
                   == ALIGN_UP(2 * sizeof(int) + sizeof(void *), sizeof(void *)),
               "ZygiskNextRuntime layout changed");
_Static_assert(offsetof(struct ZygiskNextRuntime, registerModule)
                   == ALIGN_UP(2 * sizeof(int), sizeof(void *)),
               "ZygiskNextRuntime callback position changed");
_Static_assert(offsetof(struct ZygiskNextCompanionModule, onModuleConnected)
                   == 2 * sizeof(void *),
               "ZygiskNextCompanionModule layout changed");

struct zn_module_handle {
  uint32_t magic;
  uint32_t generation;
  uint32_t slot;
  uint8_t companion;
};

struct loaded_zn_module {
  struct csoloader library;
  struct zn_module_handle *handle;
  bool loaded;
};

static struct plti hyper_plti;
static bool hyper_plti_ready;
static bool hyper_active;
static bool hyper_hooks_ready;
static bool hyper_atfork_ready;
static bool hyper_in_child;
static atomic_bool hyper_callback_fired;
static uint32_t hyper_generation;
static pid_t hyper_spawner_pid;
static int (*hyper_original_setcontext)(uid_t uid, int is_system_server,
                                       const char *se_info, const char *package_name);

static struct loaded_zn_module loaded_modules[HYPER_MAX_LOADED_MODULES];
static size_t loaded_module_count;
static struct hyper_registry runtime_modules;

static bool find_spawner_image(char *path, size_t path_size, uintptr_t *base) {
  ssize_t length = readlink("/proc/self/exe", path, path_size - 1);
  if (length <= 0 || (size_t)length >= path_size) return false;
  path[length] = '\0';
  if (strcmp(path, FOLKZYGISK_HYPER_TARGET) != 0) return false;

  struct maps_info *maps = parse_maps_safe("self");
  if (maps == NULL) return false;
  bool found = false;
  for (size_t i = 0; i < maps->length; i++) {
    struct map_entry *entry = &maps->maps[i];
    if (entry->offset == 0 && entry->path != NULL
        && strcmp(entry->path, FOLKZYGISK_HYPER_TARGET) == 0) {
      *base = entry->start;
      found = true;
      break;
    }
  }
  free_maps(maps);
  return found;
}

static int hyper_setcontext_hook(uid_t uid, int is_system_server,
                                 const char *se_info, const char *package_name) {
  int result = hyper_original_setcontext == NULL
                   ? -1
                   : hyper_original_setcontext(uid, is_system_server, se_info, package_name);
  pid_t current_pid = (pid_t)syscall(SYS_getpid);
  if (result != 0 || current_pid == hyper_spawner_pid || runtime_modules.count == 0) {
    return result;
  }

  bool expected = false;
  if (!atomic_compare_exchange_strong(&hyper_callback_fired, &expected, true)) return result;

  char process_name[256];
  if (prctl(PR_GET_NAME, process_name, 0, 0, 0) != 0 || process_name[0] == '\0') {
    snprintf(process_name, sizeof(process_name), "hyos_app");
  }
  const struct ZnHyosAppSpecializeArgs args = {
    .process_name = process_name,
    .package_name = package_name == NULL ? "" : package_name,
    .se_info = se_info == NULL ? "" : se_info,
  };
  hyper_registry_deliver(&runtime_modules, &args);

  rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_CALLBACK_DELIVERED, "");
  return result;
}

static void hyper_atfork_child(void) {
  hyper_in_child = true;
  atomic_store(&hyper_callback_fired, false);
}

static bool hyper_install_hooks(void) {
  if (hyper_hooks_ready) return true;
  hyper_plti_ready = plti_init(&hyper_plti);
  if (!hyper_plti_ready) return false;

  if (!hyper_atfork_ready) {
    if (pthread_atfork(NULL, NULL, hyper_atfork_child) != 0) {
      LOGE("HyperOS Runtime: cannot register fork child handler");
      return false;
    }
    hyper_atfork_ready = true;
  }

  char spawner_path[PATH_MAX];
  uintptr_t spawner_base = 0;
  if (!find_spawner_image(spawner_path, sizeof(spawner_path), &spawner_base)) {
    LOGE("HyperOS Runtime: cannot find the exact spawner image");
    return false;
  }
  if (!plti_add_manual_lib(&hyper_plti, spawner_path, spawner_base)) {
    LOGE("HyperOS Runtime: cannot parse the spawner ELF image");
    return false;
  }
  if (!plti_add_hook(&hyper_plti, spawner_path, "selinux_android_setcontext",
                     (void *)hyper_setcontext_hook, (void **)&hyper_original_setcontext)) {
    LOGE("HyperOS Runtime: cannot hook spawner selinux_android_setcontext");
    return false;
  }

  hyper_hooks_ready = true;
  return true;
}

static int hyper_plt_hook(void *base_addr, const char *symbol,
                          void *hook_handler, void **original) {
  if (!hyper_active || !hyper_plti_ready || base_addr == NULL
      || symbol == NULL || hook_handler == NULL) {
    return ZN_FAILED;
  }

  struct maps_info *maps = parse_maps_safe("self");
  if (maps == NULL) return ZN_FAILED;
  char *path = NULL;
  for (size_t i = 0; i < maps->length; i++) {
    struct map_entry *entry = &maps->maps[i];
    if (entry->offset == 0 && entry->start == (uintptr_t)base_addr
        && entry->path != NULL) {
      path = strdup(entry->path);
      break;
    }
  }
  free_maps(maps);
  if (path == NULL) return ZN_FAILED;

  bool parsed = plti_add_manual_lib(&hyper_plti, path, (uintptr_t)base_addr);
  bool hooked = parsed && plti_add_hook(&hyper_plti, path, symbol, hook_handler, original);
  free(path);
  return hooked ? ZN_SUCCESS : ZN_FAILED;
}

static int hyper_inline_hook(void *target, void *addr, void **original) {
  (void)target;
  (void)addr;
  if (original != NULL) *original = NULL;
  LOGE("HyperOS Runtime: inline hooks are not supported by this build");
  return ZN_FAILED;
}

static int hyper_inline_unhook(void *target) {
  (void)target;
  LOGE("HyperOS Runtime: inline unhook is not supported by this build");
  return ZN_FAILED;
}

static struct ZnSymbolResolver *hyper_new_symbol_resolver(const char *path, void *base_addr) {
  (void)path;
  (void)base_addr;
  LOGE("HyperOS Runtime: symbol resolver is not supported by this build");
  return NULL;
}

static void hyper_free_symbol_resolver(struct ZnSymbolResolver *resolver) {
  (void)resolver;
}

static void *hyper_get_base_address(struct ZnSymbolResolver *resolver) {
  (void)resolver;
  return NULL;
}

static void *hyper_symbol_lookup(struct ZnSymbolResolver *resolver, const char *name,
                                 bool prefix, size_t *size) {
  (void)resolver;
  (void)name;
  (void)prefix;
  if (size != NULL) *size = 0;
  return NULL;
}

static void hyper_for_each_symbols(
    struct ZnSymbolResolver *resolver,
    bool (*callback)(const char *name, void *addr, size_t size, void *data),
    void *data) {
  (void)resolver;
  (void)callback;
  (void)data;
}

static int hyper_connect_companion(void *handle) {
  const struct zn_module_handle *module = handle;
  if (module == NULL || module->magic != ZN_HANDLE_MAGIC) return -1;
  LOGE("HyperOS Runtime: ZN companion is not supported by this build");
  return -1;
}

static const struct ZygiskNextRuntime *hyper_get_runtime(void);

static const struct ZygiskNextAPI hyper_api = {
  .pltHook = hyper_plt_hook,
  .inlineHook = hyper_inline_hook,
  .inlineUnhook = hyper_inline_unhook,
  .newSymbolResolver = hyper_new_symbol_resolver,
  .freeSymbolResolver = hyper_free_symbol_resolver,
  .getBaseAddress = hyper_get_base_address,
  .symbolLookup = hyper_symbol_lookup,
  .forEachSymbols = hyper_for_each_symbols,
  .connectCompanion = hyper_connect_companion,
  .getRuntime = hyper_get_runtime,
};

static int hyper_register_module(const void *opaque) {
  if (!hyper_active || !hyper_hooks_ready || hyper_in_child) return ZN_FAILED;
  return hyper_registry_add(&runtime_modules, opaque);
}

static const struct ZygiskNextRuntime hyper_runtime = {
  .type = ZN_RUNTIME_HYOS,
  .api_version = ZYGISK_NEXT_HYOS_API_VERSION,
  .registerModule = hyper_register_module,
};

static const struct ZygiskNextRuntime *hyper_get_runtime(void) {
  return hyper_active ? &hyper_runtime : NULL;
}

static bool load_one_module(struct hyper_module *plan_module) {
  if (loaded_module_count == HYPER_MAX_LOADED_MODULES) return false;

  char path[64];
  snprintf(path, sizeof(path), "/proc/self/fd/%d", plan_module->fd);

  struct loaded_zn_module *loaded = &loaded_modules[loaded_module_count];
  memset(loaded, 0, sizeof(*loaded));
  if (!csoloader_load(&loaded->library, path)) {
    LOGE("HyperOS Runtime: cannot load ZN module %s", plan_module->id);
    return false;
  }
  loaded->loaded = true;

  struct ZygiskNextModule *entry = csoloader_get_symbol(&loaded->library, "zn_module");
  if (entry == NULL || entry->onModuleLoaded == NULL
      || entry->target_api_version < 1
      || entry->target_api_version > ZYGISK_NEXT_API_VERSION) {
    LOGE("HyperOS Runtime: %s has no compatible zn_module", plan_module->id);
    csoloader_unload(&loaded->library);
    memset(loaded, 0, sizeof(*loaded));
    return false;
  }

  struct zn_module_handle *handle = calloc(1, sizeof(*handle));
  if (handle == NULL) {
    csoloader_unload(&loaded->library);
    memset(loaded, 0, sizeof(*loaded));
    return false;
  }
  handle->magic = ZN_HANDLE_MAGIC;
  handle->generation = hyper_generation;
  handle->slot = plan_module->slot;
  handle->companion = plan_module->companion ? 1 : 0;
  loaded->handle = handle;

  loaded_module_count++;
  entry->onModuleLoaded(handle, &hyper_api);
  return true;
}

bool hyper_load(struct hyper_plan *plan) {
  if (hyper_active) return false;
  if (plan->count == 0) {
    rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_SKIPPED,
                                  "no matching ZN module");
    return false;
  }

  hyper_active = true;
  hyper_generation = plan->generation;
  hyper_spawner_pid = (pid_t)syscall(SYS_getpid);
  atomic_store(&hyper_callback_fired, false);
  if (!hyper_install_hooks()) {
    rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_FAILED, "setcontext hook unavailable");
    for (uint32_t i = 0; i < plan->count; i++) {
      if (plan->modules[i].fd >= 0) close(plan->modules[i].fd);
      plan->modules[i].fd = -1;
    }
    return false;
  }

  for (uint32_t i = 0; i < plan->count; i++) {
    load_one_module(&plan->modules[i]);
    if (plan->modules[i].fd >= 0) close(plan->modules[i].fd);
    plan->modules[i].fd = -1;
  }
  if (loaded_module_count == 0) {
    rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_FAILED, "no ZN module loaded");
    return false;
  }

  rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_INJECTED, "");
  if (runtime_modules.count == 0) {
    rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_FAILED, "no Hyper runtime module registered");
    return false;
  }
  rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_RUNTIME_READY, "");
  return true;
}
