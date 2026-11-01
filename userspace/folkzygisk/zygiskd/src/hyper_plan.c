#include "hyper_plan.h"

#include <dirent.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define MAX_MODULE_DIRS 128
#define MAX_DECLARATION_LINE 4096

struct module_id {
  char *value;
};

static void set_reason(char *reason, size_t size, const char *text) {
  if (reason == NULL || size == 0) return;
  snprintf(reason, size, "%s", text);
}

static int compare_module_ids(const void *left, const void *right) {
  const struct module_id *a = left;
  const struct module_id *b = right;
  return strcmp(a->value, b->value);
}

static void free_module_ids(struct module_id *ids, size_t count) {
  for (size_t i = 0; i < count; i++) free(ids[i].value);
  free(ids);
}

static bool collect_module_ids(int root_fd, struct module_id **out_ids,
                               size_t *out_count, bool *truncated) {
  int dup_fd = dup(root_fd);
  if (dup_fd == -1) return false;
  DIR *directory = fdopendir(dup_fd);
  if (directory == NULL) {
    close(dup_fd);
    return false;
  }

  struct module_id *ids = NULL;
  size_t count = 0;
  struct dirent *entry;
  while ((entry = readdir(directory)) != NULL) {
    if (entry->d_name[0] == '.') continue;

    struct stat statbuf;
    if (fstatat(root_fd, entry->d_name, &statbuf, AT_SYMLINK_NOFOLLOW) != 0
        || !S_ISDIR(statbuf.st_mode)) {
      continue;
    }

    if (count == MAX_MODULE_DIRS) {
      *truncated = true;
      break;
    }

    struct module_id *grown = realloc(ids, (count + 1) * sizeof(*ids));
    if (grown == NULL) {
      free_module_ids(ids, count);
      closedir(directory);
      return false;
    }
    ids = grown;
    ids[count].value = strdup(entry->d_name);
    if (ids[count].value == NULL) {
      free_module_ids(ids, count);
      closedir(directory);
      return false;
    }
    count++;
  }

  closedir(directory);
  if (count > 1) qsort(ids, count, sizeof(*ids), compare_module_ids);
  *out_ids = ids;
  *out_count = count;
  return true;
}

static bool target_matches(const char *declaration, const char *target) {
  const char *target_name = strrchr(target, '/');
  target_name = target_name == NULL ? target : target_name + 1;

  if (strncmp(declaration, "path=", 5) == 0) {
    return strcmp(declaration + 5, target) == 0;
  }
  if (strncmp(declaration, "name=", 5) == 0) {
    return strcmp(declaration + 5, target_name) == 0;
  }
  return false;
}

static bool path_is_beneath(const char *path, const char *directory) {
  size_t length = strlen(directory);
  return strncmp(path, directory, length) == 0 && path[length] == '/';
}

static bool validate_elf(int fd) {
  unsigned char header[20];
  ssize_t size = pread(fd, header, sizeof(header), 0);
  if (size != (ssize_t)sizeof(header)
      || memcmp(header, ELFMAG, SELFMAG) != 0
      || header[EI_DATA] != ELFDATA2LSB) {
    return false;
  }

#if defined(__LP64__)
  if (header[EI_CLASS] != ELFCLASS64) return false;
#else
  if (header[EI_CLASS] != ELFCLASS32) return false;
#endif

  uint16_t type;
  uint16_t machine;
  memcpy(&type, header + 16, sizeof(type));
  memcpy(&machine, header + 18, sizeof(machine));
#if defined(__aarch64__)
  return type == ET_DYN && machine == EM_AARCH64;
#elif defined(__arm__)
  return type == ET_DYN && machine == EM_ARM;
#elif defined(__x86_64__)
  return type == ET_DYN && machine == EM_X86_64;
#elif defined(__i386__)
  return type == ET_DYN && machine == EM_386;
#else
  return false;
#endif
}

static bool duplicate_library(const struct hyper_plan *plan, const struct stat *statbuf) {
  for (uint32_t i = 0; i < plan->count; i++) {
    struct stat existing;
    if (fstat(plan->modules[i].fd, &existing) == 0
        && existing.st_dev == statbuf->st_dev
        && existing.st_ino == statbuf->st_ino) {
      return true;
    }
  }
  return false;
}

static int resolve_library(const char *module_path, const char *module_real,
                           const char *library, struct stat *statbuf,
                           char *reason, size_t reason_size) {
  char candidate[PATH_MAX];
  int written;
  if (library[0] == '/') {
    written = snprintf(candidate, sizeof(candidate), "%s", library);
  } else {
    written = snprintf(candidate, sizeof(candidate), "%s/%s", module_path, library);
  }
  if (written <= 0 || (size_t)written >= sizeof(candidate)) {
    set_reason(reason, reason_size, "ZN library path is too long");
    return -1;
  }

  char *real = realpath(candidate, NULL);
  if (real == NULL || !path_is_beneath(real, module_real)) {
    set_reason(reason, reason_size, "ZN library escapes its module directory");
    free(real);
    return -1;
  }

  int fd = open(real, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
  if (fd == -1 || fstat(fd, statbuf) != 0 || !S_ISREG(statbuf->st_mode)
      || statbuf->st_size == 0) {
    set_reason(reason, reason_size, "ZN library is not a readable regular file");
    if (fd != -1) close(fd);
    free(real);
    return -1;
  }
  if (!validate_elf(fd)) {
    set_reason(reason, reason_size, "ZN library is not a compatible ELF shared object");
    close(fd);
    free(real);
    return -1;
  }

  free(real);
  return fd;
}

static void add_declaration(const char *module_path, const char *module_real,
                            const char *line,
                            const char *target, struct hyper_plan *plan,
                            char *reason, size_t reason_size) {
  char copy[MAX_DECLARATION_LINE];
  snprintf(copy, sizeof(copy), "%s", line);

  char *save = NULL;
  char *declaration = strtok_r(copy, " \t\r\n", &save);
  if (declaration == NULL || !target_matches(declaration, target)) return;

  bool companion = false;
  char *library = NULL;
  char *token;
  while ((token = strtok_r(NULL, " \t\r\n", &save)) != NULL) {
    if (strcmp(token, "companion") == 0) companion = true;
    else library = token;
  }
  if (library == NULL) {
    set_reason(reason, reason_size, "ZN declaration has no library");
    return;
  }
  if (plan->count == FOLKZYGISK_HYPER_MAX_MODULES) {
    set_reason(reason, reason_size, "ZN plan reached its module limit");
    return;
  }

  struct stat statbuf;
  int fd = resolve_library(module_path, module_real, library, &statbuf, reason, reason_size);
  if (fd == -1 || duplicate_library(plan, &statbuf)) {
    if (fd != -1) close(fd);
    return;
  }

  struct hyper_plan_module *module = &plan->modules[plan->count];
  const char *module_id = strrchr(module_path, '/') + 1;
  if (strlen(module_id) >= sizeof(module->id)) {
    close(fd);
    set_reason(reason, reason_size, "ZN module id is too long");
    return;
  }
  snprintf(module->id, sizeof(module->id), "%s", module_id);
  module->fd = fd;
  module->slot = plan->count;
  module->companion = companion;
  plan->count++;
}

static void inspect_module(int root_fd, const char *module_id, const char *target,
                           struct hyper_plan *plan, char *reason, size_t reason_size) {
  int module_fd = openat(root_fd, module_id, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
  if (module_fd == -1) return;

  struct stat marker;
  if (fstatat(module_fd, "disable", &marker, AT_SYMLINK_NOFOLLOW) == 0
      || fstatat(module_fd, "remove", &marker, AT_SYMLINK_NOFOLLOW) == 0) {
    close(module_fd);
    return;
  }

  int declaration_fd = openat(module_fd, "zn_modules.txt", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
  if (declaration_fd == -1) {
    close(module_fd);
    return;
  }
  FILE *declarations = fdopen(declaration_fd, "r");
  if (declarations == NULL) {
    close(declaration_fd);
    close(module_fd);
    return;
  }

  char module_path[PATH_MAX];
  char root_path[PATH_MAX];
  char fd_path[64];
  snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", root_fd);
  ssize_t root_length = readlink(fd_path, root_path, sizeof(root_path) - 1);
  if (root_length < 0 || (size_t)root_length >= sizeof(root_path) - 1) {
    fclose(declarations);
    close(module_fd);
    return;
  }
  root_path[root_length] = '\0';
  int module_path_length = snprintf(module_path, sizeof(module_path), "%s/%s",
                                    root_path, module_id);
  if (module_path_length <= 0 || (size_t)module_path_length >= sizeof(module_path)) {
    fclose(declarations);
    close(module_fd);
    return;
  }

  char *module_real = realpath(module_path, NULL);
  if (module_real == NULL) {
    fclose(declarations);
    close(module_fd);
    return;
  }

  char line[MAX_DECLARATION_LINE];
  while (fgets(line, sizeof(line), declarations) != NULL) {
    if (strchr(line, '\n') == NULL && !feof(declarations)) {
      int character;
      while ((character = fgetc(declarations)) != '\n' && character != EOF) {}
      set_reason(reason, reason_size, "ZN declaration line is too long");
      continue;
    }
    add_declaration(module_path, module_real, line, target, plan, reason, reason_size);
  }

  free(module_real);
  fclose(declarations);
  close(module_fd);
}

bool hyper_plan_build(const char *modules_root, const char *target,
                      struct hyper_plan *plan, char *reason, size_t reason_size) {
  memset(plan, 0, sizeof(*plan));
  set_reason(reason, reason_size, "");

  int root_fd = open(modules_root, O_RDONLY | O_DIRECTORY | O_CLOEXEC);
  if (root_fd == -1) {
    set_reason(reason, reason_size, "cannot open module directory");
    return false;
  }

  bool truncated = false;
  size_t count = 0;
  struct module_id *ids = NULL;
  if (!collect_module_ids(root_fd, &ids, &count, &truncated)) {
    close(root_fd);
    set_reason(reason, reason_size, "cannot enumerate module directories");
    return false;
  }
  if (truncated) set_reason(reason, reason_size, "module directory list was truncated");

  for (size_t i = 0; i < count; i++) {
    inspect_module(root_fd, ids[i].value, target, plan, reason, reason_size);
  }
  free_module_ids(ids, count);
  close(root_fd);
  return true;
}

void hyper_plan_free(struct hyper_plan *plan) {
  for (uint32_t i = 0; i < plan->count; i++) {
    if (plan->modules[i].fd != -1) close(plan->modules[i].fd);
    plan->modules[i].fd = -1;
  }
  plan->count = 0;
}
