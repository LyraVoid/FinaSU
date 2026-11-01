"""Exercise runtime registration validation and structure copying."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HyperRegistryTest(unittest.TestCase):
    def test_registration_contract(self):
        with tempfile.TemporaryDirectory(prefix="folkzygisk-registry-", dir="/tmp/opencode") as temporary:
            directory = Path(temporary)
            source = directory / "registry_test.c"
            callbacks = "\n".join(
                f"static void cb{i}(const struct ZnHyosAppSpecializeArgs *args) {{ "
                f"assert(args != NULL && args->process_name != NULL); calls[{i}]++; }}"
                for i in range(9)
            )
            source.write_text(
                "#include <assert.h>\n"
                '#include "hyper_registry.h"\n'
                "static int calls[9];\n"
                + callbacks
                + """
int main(void) {
  struct hyper_registry registry = { 0 };
  struct ZygiskNextHyosModule invalid = { 0 };
  assert(hyper_registry_add(&registry, NULL) == ZN_FAILED);
  assert(hyper_registry_add(&registry, &invalid) == ZN_FAILED);
  invalid.target_api_version = ZYGISK_NEXT_HYOS_API_VERSION + 1;
  invalid.onAppSpecialized = cb0;
  assert(hyper_registry_add(&registry, &invalid) == ZN_FAILED);

  struct ZygiskNextHyosModule first = {
    .target_api_version = ZYGISK_NEXT_HYOS_API_VERSION,
    .onAppSpecialized = cb0,
  };
  assert(hyper_registry_add(&registry, &first) == ZN_SUCCESS);
  first.onAppSpecialized = cb1;
  assert(hyper_registry_add(&registry, &first) == ZN_SUCCESS);
  assert(hyper_registry_add(&registry, &first) == ZN_FAILED);

  for (int i = 2; i < 8; i++) {
    struct ZygiskNextHyosModule module = {
      .target_api_version = ZYGISK_NEXT_HYOS_API_VERSION,
      .onAppSpecialized = (i == 2 ? cb2 : i == 3 ? cb3 : i == 4 ? cb4 :
                           i == 5 ? cb5 : i == 6 ? cb6 : cb7),
    };
    assert(hyper_registry_add(&registry, &module) == ZN_SUCCESS);
  }
  struct ZygiskNextHyosModule overflow = {
    .target_api_version = ZYGISK_NEXT_HYOS_API_VERSION,
    .onAppSpecialized = cb8,
  };
  assert(hyper_registry_add(&registry, &overflow) == ZN_FAILED);
  assert(registry.count == HYPER_MAX_RUNTIME_MODULES);

  struct ZnHyosAppSpecializeArgs args = {
    .process_name = "demo",
    .package_name = "example.package",
    .se_info = "default",
  };
  hyper_registry_deliver(&registry, &args);
  assert(calls[0] == 1 && calls[1] == 1 );
  for (int i = 0; i < 8; i++) assert(calls[i] == 1);
  assert(calls[8] == 0);
  return 0;
}
"""
            )
            binary = directory / "registry_test"
            subprocess.run(
                [
                    "cc",
                    "-std=c18",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    f"-I{ROOT / 'loader/src/injector'}",
                    str(source),
                    str(ROOT / "loader/src/injector/hyper_registry.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
            )
            subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    unittest.main()
