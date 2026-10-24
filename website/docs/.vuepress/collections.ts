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

const setting = defineCollection({
  type: 'doc',
  dir: 'setting',
  linkPrefix: '/setting',
  title: '设置',
  sidebar: [
    {
      text: '设置索引',
      icon: 'ri:list-check',
      collapsed: false,
      items: [
        { text: '设置总览', link: '/setting/overview/' },
        { text: '更新与版本', link: '/setting/update/' },
        { text: '语言', link: '/setting/language/' },
        { text: 'App Profile 模板', link: '/setting/profile-template/' },
      ],
    },
    {
      text: '外观',
      icon: 'ri:palette-line',
      collapsed: true,
      items: [
        { text: '主题与颜色', link: '/setting/theme/' },
        { text: '壁纸', link: '/setting/wallpaper/' },
        { text: '字体', link: '/setting/font/' },
        { text: '主题商店', link: '/setting/theme-store/' },
        { text: '首页与布局', link: '/setting/home/' },
      ],
    },
    {
      text: '行为',
      icon: 'ri:gesture-line',
      collapsed: true,
      items: [
        { text: '导航', link: '/setting/navigation/' },
        { text: '悬浮导航栏', link: '/setting/floating-bar/' },
        { text: '交互与兼容', link: '/setting/interaction/' },
        { text: '模块卡片与横幅', link: '/setting/module-cards/' },
      ],
    },
    {
      text: '功能与安全',
      icon: 'ri:shield-keyhole-line',
      collapsed: true,
      items: [
        { text: 'Root 与挂载策略', link: '/setting/root-policy/' },
        { text: '隐藏与内核安全', link: '/setting/hiding/' },
        { text: '访问控制', link: '/setting/access/' },
        { text: '内置能力', link: '/setting/builtin/' },
        { text: '身份验证', link: '/setting/authentication/' },
      ],
    },
    {
      text: '数据与维护',
      icon: 'ri:database-2-line',
      collapsed: true,
      items: [
        { text: '多媒体', link: '/setting/multimedia/' },
        { text: '备份', link: '/setting/backup/' },
        { text: '维护与危险操作', link: '/setting/maintenance/' },
        { text: '诊断与反馈', link: '/setting/diagnostics/' },
        { text: '关于', link: '/setting/about/' },
      ],
    },
  ],
})

const theme = defineCollection({
  type: 'doc',
  dir: 'theme',
  linkPrefix: '/theme',
  title: '主题',
  sidebar: [
    {
      text: '主题基础',
      icon: 'ri:palette-line',
      collapsed: false,
      items: [
        { text: '主题预览与使用', link: '/theme/theme-use/' },
        { text: '成为主题开发者', link: '/theme/theme-dev/' },
        { text: '导出主题', link: '/theme/export/info/' },
        { text: '分享主题', link: '/theme/export/share/' },
      ],
    },
    {
      text: '全局特性',
      icon: 'ri:contrast-2-line',
      collapsed: true,
      items: [
        { text: '夜间模式与颜色', link: '/theme/global/theme-style/' },
        { text: '背景模糊', link: '/theme/global/blur/' },
        { text: '透明度', link: '/theme/global/transparency/' },
        { text: '自定义字体', link: '/theme/global/font/' },
        { text: '背景音乐', link: '/theme/global/music/' },
        { text: '音效', link: '/theme/global/sound/' },
        { text: '震动触感', link: '/theme/global/haptic/' },
        { text: '视频背景', link: '/theme/global/video/' },
      ],
    },
    {
      text: 'CircleUI',
      icon: 'ri:circle-line',
      collapsed: true,
      items: [
        { text: '概览', link: '/theme/theme-circle/start/' },
        { text: '基础样式', link: '/theme/theme-circle/basic/' },
        { text: '背景', link: '/theme/theme-circle/background/' },
        { text: '工作卡片', link: '/theme/theme-circle/working-card/' },
        { text: '组件隐藏', link: '/theme/theme-circle/component-hiding/' },
        { text: '交互', link: '/theme/theme-circle/interaction/' },
      ],
    },
    {
      text: 'GridUI',
      icon: 'ri:layout-grid-line',
      collapsed: true,
      items: [
        { text: '概览', link: '/theme/theme-grid/' },
        { text: '基础样式', link: '/theme/theme-grid/basic/' },
        { text: '背景', link: '/theme/theme-grid/background/' },
        { text: '工作卡片', link: '/theme/theme-grid/working-card/' },
        { text: '组件隐藏', link: '/theme/theme-grid/component-hiding/' },
        { text: '交互', link: '/theme/theme-grid/interaction/' },
      ],
    },
    {
      text: 'FocusUI',
      icon: 'ri:focus-3-line',
      collapsed: true,
      items: [
        { text: '概览', link: '/theme/theme-focus/' },
        { text: '基础样式', link: '/theme/theme-focus/basic/' },
        { text: '硬件信息', link: '/theme/theme-focus/hardware/' },
        { text: '平板适配', link: '/theme/theme-focus/tablet/' },
        { text: '交互', link: '/theme/theme-focus/interaction/' },
      ],
    },
    {
      text: 'DashboardUI',
      icon: 'ri:dashboard-line',
      collapsed: true,
      items: [
        { text: '概览', link: '/theme/theme-dashboard/start/' },
        { text: '基础样式', link: '/theme/theme-dashboard/basic/' },
        { text: '状态信息', link: '/theme/theme-dashboard/hardware/' },
        { text: '统计入口', link: '/theme/theme-dashboard/statistics/' },
        { text: '交互', link: '/theme/theme-dashboard/interaction/' },
      ],
    },
    {
      text: 'StatsUI',
      icon: 'ri:bar-chart-2-line',
      collapsed: true,
      items: [
        { text: '概览', link: '/theme/theme-statsui/start/' },
        { text: '基础样式', link: '/theme/theme-statsui/basic/' },
        { text: '硬件监控', link: '/theme/theme-statsui/hardware/' },
        { text: '统计信息', link: '/theme/theme-statsui/statistics/' },
        { text: '交互', link: '/theme/theme-statsui/interaction/' },
      ],
    },
  ],
})

export const zhCollections = defineCollections([guide, manager, builtin, setting, theme])
