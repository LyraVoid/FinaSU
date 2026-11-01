"""Exercise the actual monitor JSON writer on the host without ptracing init."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class StatusTest(unittest.TestCase):
    def test_atomic_boot_scoped_status(self):
        source = (ROOT / "loader/src/ptracer/monitor.c").read_text()
        declarations = source[source.index("const char *monitor_stop_reason"):source.index("int monitor_epoll_fd;")]
        declarations = declarations.replace("static struct environment_information environment_information64;", "")
        declarations = declarations.replace("static struct environment_information environment_information32;", "")
        writer = source[source.index("static char boot_id[64];"):source.index("static bool prepare_environment()")]
        with tempfile.TemporaryDirectory(prefix="folkzygisk-status-", dir="/tmp/opencode") as temporary:
            directory = Path(temporary)
            harness = directory / "status.c"
            harness.write_text(
                '#include <stdio.h>\n#include <stdlib.h>\n#include <stdbool.h>\n'
                '#include <stdint.h>\n#include <string.h>\n#include <unistd.h>\n'
                '#include <errno.h>\n#include <sys/types.h>\n'
                '#define FOLKZYGISK_STATE_FILE "state.json"\n'
                + declarations + writer + '''
int main(void) {
  strcpy(boot_id, "01234567-89ab-cdef-0123-456789abcdef");
  monitor_ready = true;
  status64.supported = true;
  status64.daemon_running = true;
  status64.daemon_pid = getpid();
  status64.zygote_pid = getpid();
  status64.daemon_start_ticks = process_start_ticks(getpid());
  status64.zygote_start_ticks = process_start_ticks(getpid());
  status64.zygote_injected = true;
  status64.daemon_error_info = "quote\\\" slash\\\\ newline\\n";
  if (!update_status(NULL)) return 1;
  if (rename("state.json", "first.json")) return 2;
  status64.zygote_injected = false;
  status32.supported = true;
  tracing_state = STOPPED;
  if (!update_status("stopped")) return 3;
  return 0;
}
'''
            )
            binary = directory / "status"
            subprocess.run(["cc", "-std=c18", "-D_GNU_SOURCE", "-Wall", "-Wextra", "-Werror",
                            str(harness), "-o", str(binary)], check=True)
            subprocess.run([str(binary)], cwd=directory, check=True)
            first = json.loads((directory / "first.json").read_text())
            final = json.loads((directory / "state.json").read_text())
            self.assertTrue(first["ready"])
            self.assertGreater(first["monitor_start_ticks"], 0)
            self.assertEqual(first["boot_id"], "01234567-89ab-cdef-0123-456789abcdef")
            self.assertTrue(first["abis"]["64"]["injected"])
            self.assertFalse(first["abis"]["32"]["supported"])
            self.assertGreater(first["abis"]["64"]["zygote_start_ticks"], 0)
            self.assertEqual(first["abis"]["64"]["reason"], 'quote" slash\\ newline\n')
            self.assertFalse(final["ready"])
            self.assertFalse(final["abis"]["64"]["injected"])
            self.assertTrue(final["abis"]["32"]["supported"])
            self.assertEqual(final["reason"], "stopped")
            self.assertEqual((directory / "state.json").stat().st_mode & 0o777, 0o600)
            self.assertEqual(list(directory.glob("state.json.*")), [])


if __name__ == "__main__":
    unittest.main()
