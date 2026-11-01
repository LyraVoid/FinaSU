#ifndef __KSU_H_KSUD
#define __KSU_H_KSUD

#include <asm/syscall.h>
#include <linux/compat.h>
#include <linux/uaccess.h>

#define KSUD_PATH "/data/adb/ksud"

struct user_arg_ptr {
#ifdef CONFIG_COMPAT
    bool is_compat;
#endif
    union {
        const char __user *const __user *native;
#ifdef CONFIG_COMPAT
        const compat_uptr_t __user *compat;
#endif
    } ptr;
};

void ksu_ksud_init();
void ksu_ksud_exit();

void ksu_handle_execveat_ksud(const char *path, struct user_arg_ptr *argv);
const char __user *ksu_get_user_arg_ptr(struct user_arg_ptr argv, int nr);
void ksu_execve_hook_ksud(const struct pt_regs *regs);
void ksu_execveat_hook_ksud(const struct pt_regs *regs);
void ksu_stop_input_hook_runtime(void);

#endif
