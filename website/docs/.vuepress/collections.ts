import { defineCollection, defineCollections } from 'vuepress-theme-plume'

const guide = defineCollection({
  type: 'doc',
  dir: 'guide',
  linkPrefix: '/guide',
  title: '使用指南',
  sidebar: [
    {
      text: '开始使用',
      icon: 'ri:book-open-line',
      collapsed: false,
      items: [
        { text: 'FinaSU 简介', link: '/guide/intro/', icon: 'ri:information-line' },
        { text: '快速开始', link: '/guide/get-started/', icon: 'ri:rocket-line' },
        { text: '安装与刷写', link: '/guide/install/', icon: 'ri:download-line' },
      ],
    },
    {
      text: '迁移',
      icon: 'ri:swap-box-line',
      collapsed: true,
      items: [
        { text: '从 Magisk 切换', link: '/guide/switch/magisk/' },
        { text: '从 KernelSU 切换', link: '/guide/switch/kernelsu/' },
        { text: '从 APatch 切换', link: '/guide/switch/apatch/' },
      ],
    },
    {
      text: '维护',
      icon: 'ri:tools-line',
      collapsed: false,
      items: [
        { text: '更新 FinaSU', link: '/guide/update/', icon: 'ri:refresh-line' },
        { text: '启动循环救援', link: '/guide/rescue-bootloop/', icon: 'ri:first-aid-kit-line' },
        { text: '越狱与延迟加载', link: '/guide/jailbreak/', icon: 'ri:flashlight-line' },
      ],
    },
    {
      text: '参考',
      icon: 'ri:question-line',
      collapsed: false,
      items: [
        { text: '基础概念', link: '/guide/information/', icon: 'ri:book-2-line' },
        { text: '常见问题', link: '/guide/faq/', icon: 'ri:question-answer-line' },
      ],
    },
  ],
})

const manager = defineCollection({
  type: 'doc',
  dir: 'manager',
  linkPrefix: '/manager',
  title: '管理器',
  sidebar: [
    {
      text: '管理',
      icon: 'ri:smartphone-line',
      collapsed: false,
      items: [
        { text: '管理器总览', link: '/manager/feature/' },
        { text: '超级用户', link: '/manager/superuser/' },
        { text: 'App Profile', link: '/manager/app-profile/' },
        { text: '模块管理', link: '/manager/module/' },
        { text: '模块仓库', link: '/manager/module-store/' },
        { text: 'Lua 插件', link: '/manager/plugin/' },
        { text: '内核模块', link: '/manager/kernel-module/' },
      ],
    },
    {
      text: '数据与权限',
      icon: 'ri:database-2-line',
      collapsed: false,
      items: [
        { text: 'SU 日志', link: '/manager/su-log/' },
        { text: '备份与 WebDAV', link: '/manager/backup/' },
        { text: '动态管理器', link: '/manager/dynamic-manager/' },
        { text: '/data/adb 存储结构', link: '/manager/storage/' },
      ],
    },
  ],
})

const builtin = defineCollection({
  type: 'doc',
  dir: 'builtin',
  linkPrefix: '/builtin',
  title: '内置能力',
  sidebar: [
    {
      text: '内置引擎',
      icon: 'ri:apps-2-line',
      collapsed: false,
      items: [
        { text: 'Folk Mount', link: '/builtin/folk-mount/' },
        { text: 'FolkZygisk', link: '/builtin/folkzygisk/' },
        { text: 'Sui', link: '/builtin/sui/' },
        { text: 'NoMount', link: '/builtin/nomount/' },
        { text: 'SUSFS 与内核级隐藏', link: '/builtin/susfs/' },
      ],
    },
  ],
})

export const zhCollections = defineCollections([guide, manager, builtin])
