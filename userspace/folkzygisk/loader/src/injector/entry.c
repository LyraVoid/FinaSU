#include "daemon.h"
#include "hyper.h"
#include "logging.h"
#include "misc.h"

#include "hook.h"
#include "ptrace_clear.h"

__attribute__((visibility("default")))
void entry(void *addr, size_t size, int injection_kind) {
  bool is_tango = injection_kind == FOLKZYGISK_INJECT_TANGO;
  bool is_hyper = injection_kind == FOLKZYGISK_INJECT_HYPER;
  LOGD("ReZygisk%s%s library injected, version %s", is_tango ? " [TANGO]" : "",
       is_hyper ? " [HYPER]" : "", ZKSU_VERSION);

  start_addr = addr;
  block_size = size;

  struct kernel_version version = parse_kversion();
  if (version.major > 3 || (version.major == 3 && version.minor >= 8)) {
    LOGD("Supported kernel version %d.%d.%d, sending seccomp event", version.major, version.minor, version.patch);

    perform_ptrace_message_clear();
  }

  if (is_hyper) {
    struct hyper_plan plan;
    if (!rezygiskd_read_hyper_plan(&plan)) {
      LOGE("Failed to read the HyperOS Runtime plan");
      rezygiskd_report_hyper_status(FOLKZYGISK_HYPER_FAILED,
                                    "hyper plan unavailable");
      return;
    }
    if (!hyper_load(&plan)) {
      LOGE("HyperOS Runtime initialization failed");
      return;
    }
    return;
  }

  LOGD("start plt hooking");
  hook_functions();

  if (!rezygiskd_zygote_injected()) {
    LOGE("ReZygiskd is not running");

    return;
  }

  LOGD("Zygisk library execution done, addr: %p, size: %zu", addr, size);
}
