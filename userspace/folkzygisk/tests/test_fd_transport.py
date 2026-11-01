"""Verify the one-byte SCM_RIGHTS framing used by the daemon protocols."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FdTransportTest(unittest.TestCase):
    def test_multiple_fd_messages_do_not_overread(self):
        with tempfile.TemporaryDirectory(prefix="folkzygisk-fd-", dir="/tmp/opencode") as temporary:
            directory = Path(temporary)
            (directory / "logging.h").write_text(
                "#define LOGW(...) ((void)0)\n"
                "#define LOGE(...) ((void)0)\n"
                "#define PLOGE(...) ((void)0)\n"
            )
            source = directory / "fd_test.c"
            source.write_text(
                "#include <assert.h>\n"
                "#include <fcntl.h>\n"
                "#include <sys/socket.h>\n"
                "#include <unistd.h>\n"
                '#include "socket_utils.h"\n'
                "int main(void) {\n"
                "  int sockets[2];\n"
                "  int pipes[3][2];\n"
                "  assert(socketpair(AF_UNIX, SOCK_STREAM, 0, sockets) == 0);\n"
                "  for (int i = 0; i < 3; i++) {\n"
                "    assert(pipe(pipes[i]) == 0);\n"
                "    assert(write_fd(sockets[0], pipes[i][0]) == 1);\n"
                "  }\n"
                "  for (int i = 0; i < 3; i++) {\n"
                "    int received = read_fd(sockets[1]);\n"
                "    assert(received >= 0);\n"
                "    assert(fcntl(received, F_GETFD) >= 0);\n"
                "    close(received);\n"
                "  }\n"
                "  close(sockets[0]);\n"
                "  close(sockets[1]);\n"
                "  for (int i = 0; i < 3; i++) {\n"
                "    close(pipes[i][0]);\n"
                "    close(pipes[i][1]);\n"
                "  }\n"
                "  return 0;\n"
                "}\n"
            )
            binary = directory / "fd_test"
            subprocess.run(
                [
                    "cc",
                    "-std=c18",
                    "-D_GNU_SOURCE",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    f"-I{directory}",
                    f"-I{ROOT / 'loader/src/include'}",
                    str(source),
                    str(ROOT / "loader/src/common/socket_utils.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
            )
            subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    unittest.main()
