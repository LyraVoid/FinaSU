"""Exercise HyperOS Runtime module discovery and path confinement."""
from pathlib import Path
import os
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def elf_shared_object():
    class_id = 2 if struct.calcsize("P") == 8 else 1
    machine = 62 if class_id == 2 else 3
    header = bytearray(20)
    header[:6] = b"\x7fELF" + bytes((class_id, 1))
    struct.pack_into("<H", header, 16, 3)
    struct.pack_into("<H", header, 18, machine)
    return bytes(header)


class HyperPlanTest(unittest.TestCase):
    def compile_harness(self, directory):
        source = directory / "harness.c"
        source.write_text(
            "#include <stdio.h>\n"
            "#include <string.h>\n"
            "#include <unistd.h>\n"
            '#include "hyper_plan.h"\n'
            "int main(int argc, char **argv) {\n"
            "  (void)argc;\n"
            "  struct hyper_plan plan;\n"
            "  char reason[256];\n"
            "  if (!hyper_plan_build(argv[1], argv[2], &plan, reason, sizeof(reason))) {\n"
            '    printf("error=%s\\n", reason);\n'
            "    return 2;\n"
            "  }\n"
            '  printf("count=%u reason=%s\\n", plan.count, reason);\n'
            "  for (uint32_t i = 0; i < plan.count; i++)\n"
            '    printf("%u:%s:%d\\n", plan.modules[i].slot, plan.modules[i].id,\n'
            "           plan.modules[i].companion);\n"
            "  hyper_plan_free(&plan);\n"
            "  return 0;\n"
            "}\n"
        )
        binary = directory / "harness"
        subprocess.run(
            [
                "cc",
                "-std=c18",
                "-D_GNU_SOURCE",
                "-Wall",
                "-Wextra",
                "-Werror",
                f"-I{ROOT / 'zygiskd/src'}",
                f"-I{ROOT / 'include'}",
                str(source),
                str(ROOT / "zygiskd/src/hyper_plan.c"),
                "-o",
                str(binary),
            ],
            check=True,
        )
        return binary

    def test_selection_confinement_dedup_and_order(self):
        with tempfile.TemporaryDirectory(prefix="folkzygisk-hyper-", dir="/tmp/opencode") as temporary:
            root = Path(temporary)
            modules = root / "modules"
            outside = root / "outside.so"
            outside.write_bytes(elf_shared_object())
            elf = elf_shared_object()

            a = modules / "a"
            (a / "lib").mkdir(parents=True)
            (a / "lib/module.so").write_bytes(elf)
            (a / "lib/absolute.so").write_bytes(elf)
            os.symlink(outside, a / "lib/escape.so")
            (a / "zn_modules.txt").write_text(
                "name=other lib/module.so\n"
                "path=/system_ext/bin/hyos_spawner lib/module.so companion\n"
                "path=/system_ext/bin/hyos_spawner lib/module.so\n"
                "name=hyos_spawner lib/escape.so\n"
                f"name=hyos_spawner {a / 'lib/absolute.so'}\n"
                f"name=hyos_spawner {outside}\n"
            )

            b = modules / "b"
            b.mkdir()
            os.link(a / "lib/module.so", b / "duplicate.so")
            (b / "zn_modules.txt").write_text(
                "name=hyos_spawner duplicate.so\n"
            )

            disabled = modules / "c"
            disabled.mkdir()
            (disabled / "lib.so").write_bytes(elf)
            (disabled / "zn_modules.txt").write_text("name=hyos_spawner lib.so\n")
            (disabled / "disable").write_text("")

            binary = self.compile_harness(root)
            result = subprocess.run(
                [str(binary), str(modules), "/system_ext/bin/hyos_spawner"],
                check=True,
                text=True,
                capture_output=True,
            )
            self.assertEqual(
                result.stdout,
                "count=2 reason=ZN library escapes its module directory\n"
                "0:a:1\n"
                "1:a:0\n",
            )

    def test_missing_root_and_no_matching_target(self):
        with tempfile.TemporaryDirectory(prefix="folkzygisk-hyper-", dir="/tmp/opencode") as temporary:
            root = Path(temporary)
            binary = self.compile_harness(root)
            missing = subprocess.run(
                [str(binary), str(root / "missing"), "/system_ext/bin/hyos_spawner"],
                text=True,
                capture_output=True,
            )
            self.assertEqual(missing.returncode, 2)
            self.assertEqual(missing.stdout, "error=cannot open module directory\n")

            modules = root / "modules"
            module = modules / "one"
            module.mkdir(parents=True)
            (module / "lib.so").write_bytes(elf_shared_object())
            (module / "zn_modules.txt").write_text("name=adbd lib.so\n")
            no_match = subprocess.run(
                [str(binary), str(modules), "/system_ext/bin/hyos_spawner"],
                check=True,
                text=True,
                capture_output=True,
            )
            self.assertEqual(no_match.stdout, "count=0 reason=\n")

    def test_wrong_architecture_and_missing_library_are_skipped(self):
        with tempfile.TemporaryDirectory(prefix="folkzygisk-hyper-", dir="/tmp/opencode") as temporary:
            root = Path(temporary)
            modules = root / "modules"
            module = modules / "bad"
            module.mkdir(parents=True)
            header = bytearray(elf_shared_object())
            struct.pack_into("<H", header, 18, 0xffff)
            (module / "wrong.so").write_bytes(header)
            (module / "zn_modules.txt").write_text(
                "name=hyos_spawner missing.so\n"
                "name=hyos_spawner wrong.so\n"
            )
            binary = self.compile_harness(root)
            result = subprocess.run(
                [str(binary), str(modules), "/system_ext/bin/hyos_spawner"],
                check=True,
                text=True,
                capture_output=True,
            )
            self.assertEqual(
                result.stdout,
                "count=0 reason=ZN library is not a compatible ELF shared object\n",
            )


if __name__ == "__main__":
    unittest.main()
