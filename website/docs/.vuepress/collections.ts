import {
  defineCollection,
  defineCollections,
  type ThemeSidebarItem,
} from 'vuepress-theme-plume'

/**
 * tip: 推荐、常用或核心；warning: 前置条件或需谨慎；
 * danger: 高风险或紧急；info: 进阶、按需或诊断。
 */
export const docsSidebar: ThemeSidebarItem[] = [
  {
    text: '从这里开始',
    icon: 'ri:book-open-line',
    collapsed: false,
    items: [
      { text: '介绍', link: '/guide/intro/', icon: 'ri:article-line' },
      {
        text: '快速开始',
        link: '/guide/get-started/',
        icon: 'ri:rocket-line',
        badge: { text: '必看', type: 'warning' },
      },
      {
        text: '安装与刷写',
        icon: 'ri:download-line',
        badge: { text: '推荐', type: 'tip' },
        collapsed: false,
        items: [
          {
            text: '方案概览',
            link: '/guide/install/',
            icon: 'ri:list-check',
            badge: { text: '先看', type: 'info' },
          },
          {
            text: 'LKM',
            link: '/guide/install/lkm/',
            icon: 'ri:download-line',
            badge: { text: '推荐', type: 'tip' },
          },
          {
            text: 'Built-In',
            link: '/guide/install/built-in/',
            icon: 'ri:code-box-line',
            badge: { text: '进阶', type: 'info' },
          },
          {
            text: '越狱模式',
            link: '/guide/install/jailbreak/',
            icon: 'ri:lock-unlock-line',
            badge: { text: '尝鲜', type: 'warning' },
          },
        ],
      },
      {
        text: '切换到 FinaSU',
        icon: 'ri:swap-line',
        badge: { text: '按需', type: 'info' },
        collapsed: false,
        items: [
          {
            text: 'Magisk',
            link: '/guide/switch/magisk/',
            icon: 'material-symbols:security',
          },
          {
            text: 'KernelSU',
            link: '/guide/switch/kernelsu/',
            icon: 'material-symbols:developer-mode',
          },
          {
            text: 'APatch',
            link: '/guide/switch/apatch/',
            icon: 'material-symbols:extension',
          },
        ],
      },
      {
        text: '救砖',
        link: '/guide/rescue-bootloop/',
        icon: 'ri:first-aid-kit-line',
        badge: { text: '重要', type: 'danger' },
      },
      {
        text: '更新 FinaSU',
        link: '/guide/update/',
        icon: 'ri:refresh-line',
        badge: { text: '维护', type: 'info' },
      },
      {
        text: '常见问题与解答',
        link: '/guide/faq/',
        icon: 'material-symbols:help-outline',
        badge: { text: '常用', type: 'info' },
      },
      {
        text: '基础概念',
        link: '/guide/information/',
        icon: 'material-symbols:menu-book',
        badge: { text: '入门', type: 'tip' },
      },
    ],
  },
  {
    text: '设置',
    icon: 'ri:settings-3-line',
    collapsed: true,
    items: [
      {
        text: '设置总览',
        link: '/setting/overview/',
        icon: 'ri:dashboard-line',
      },
      {
        text: '通用',
        icon: 'ri:settings-4-line',
        collapsed: true,
        items: [
          { text: '通用设置', link: '/setting/general/', icon: 'ri:settings-4-line' },
          { text: '更新与版本', link: '/setting/update/', icon: 'ri:refresh-line' },
          { text: '语言', link: '/setting/language/', icon: 'ri:translate-2' },
          {
            text: 'App Profile 模板',
            link: '/setting/profile-template/',
            icon: 'ri:file-user-line',
            badge: { text: '进阶', type: 'info' },
          },
        ],
      },
      {
        text: '外观',
        icon: 'ri:palette-line',
        collapsed: true,
        items: [
          { text: '外观设置', link: '/setting/appearance/', icon: 'ri:palette-line' },
          { text: '主题与颜色', link: '/setting/theme/', icon: 'ri:palette-line' },
          { text: '壁纸', link: '/setting/wallpaper/', icon: 'ri:image-line' },
          { text: '字体', link: '/setting/font/', icon: 'ri:font-size-2' },
          {
            text: '主题商店',
            link: '/setting/theme-store/',
            icon: 'ri:store-3-line',
            badge: { text: '推荐', type: 'tip' },
          },
          { text: '首页与布局', link: '/setting/home/', icon: 'ri:home-4-line' },
        ],
      },
      {
        text: '行为',
        icon: 'ri:hand-coin-line',
        collapsed: true,
        items: [
          { text: '行为设置', link: '/setting/behavior/', icon: 'ri:hand-coin-line' },
          { text: '导航', link: '/setting/navigation/', icon: 'ri:compass-3-line' },
          {
            text: '悬浮导航栏',
            link: '/setting/floating-bar/',
            icon: 'ri:layout-bottom-line',
          },
          { text: '交互与兼容', link: '/setting/interaction/', icon: 'ri:hand-coin-line' },
          {
            text: '模块卡片与横幅',
            link: '/setting/module-cards/',
            icon: 'ri:image-edit-line',
          },
        ],
      },
      {
        text: '功能',
        icon: 'ri:apps-2-line',
        collapsed: true,
        items: [
          { text: '功能设置', link: '/setting/function/', icon: 'ri:apps-2-line' },
          {
            text: 'Root 与挂载策略',
            link: '/setting/root-policy/',
            icon: 'ri:shield-keyhole-line',
            badge: { text: '进阶', type: 'info' },
          },
          {
            text: '自定义卸载挂载点',
            link: '/setting/umount-paths/',
            icon: 'ri:attachment-line',
            badge: { text: '入门', type: 'tip' },
          },
        ],
      },
      {
        text: '安全',
        icon: 'ri:shield-keyhole-line',
        collapsed: true,
        items: [
          { text: '安全设置', link: '/setting/security/', icon: 'ri:shield-keyhole-line' },
          { text: '隐藏与内核安全', link: '/setting/hiding/', icon: 'ri:eye-off-line' },
          {
            text: '访问控制',
            link: '/setting/access/',
            icon: 'ri:lock-password-line',
            badge: { text: '谨慎', type: 'warning' },
          },
        ],
      },
      {
        text: '验证',
        link: '/setting/authentication/',
        icon: 'ri:fingerprint-line',
        badge: { text: '推荐', type: 'tip' },
      },
      {
        text: '多媒体',
        link: '/setting/multimedia/',
        icon: 'ri:volume-up-line',
      },
      {
        text: '备份',
        link: '/setting/backup/',
        icon: 'ri:archive-line',
        badge: { text: '推荐', type: 'tip' },
      },
      {
        text: '设置首页入口',
        icon: 'ri:image-line',
        collapsed: true,
        items: [
          {
            text: '壁纸图鉴',
            link: '/setting/wallpaper-gallery/',
            icon: 'ri:image-line',
          },
          {
            text: '诊断与反馈',
            link: '/setting/diagnostics/',
            icon: 'ri:bug-line',
            badge: { text: '反馈', type: 'info' },
          },
          {
            text: '维护与危险操作',
            link: '/setting/maintenance/',
            icon: 'ri:tools-line',
            badge: { text: '危险', type: 'danger' },
          },
          { text: '关于', link: '/setting/about/', icon: 'ri:information-line' },
        ],
      },
    ],
  },
  {
    text: '管理器',
    icon: 'ri:smartphone-line',
    collapsed: true,
    items: [
      {
        text: '管理器总览',
        link: '/manager/feature/',
        icon: 'ri:lightbulb-line',
      },
      {
        text: '应用与权限',
        icon: 'ri:admin-line',
        collapsed: true,
        items: [
          { text: '超级用户', link: '/manager/superuser/', icon: 'ri:admin-line' },
          {
            text: 'App Profile',
            link: '/manager/app-profile/',
            icon: 'ri:user-settings-line',
            badge: { text: '进阶', type: 'info' },
          },
          {
            text: '动态管理器',
            link: '/manager/dynamic-manager/',
            icon: 'ri:shield-keyhole-line',
            badge: { text: '进阶', type: 'info' },
          },
        ],
      },
      {
        text: '模块与扩展',
        icon: 'ri:apps-2-line',
        collapsed: true,
        items: [
          {
            text: '模块管理',
            link: '/manager/module/',
            icon: 'ri:puzzle-line',
            badge: { text: '常用', type: 'tip' },
          },
          {
            text: '模块仓库',
            link: '/manager/module-store/',
            icon: 'ri:store-3-line',
            badge: { text: '推荐', type: 'tip' },
          },
          {
            text: 'Lua 插件',
            link: '/manager/plugin/',
            icon: 'ri:braces-line',
            badge: { text: '进阶', type: 'info' },
          },
          {
            text: '内核模块',
            link: '/manager/kernel-module/',
            icon: 'ri:cpu-line',
            badge: { text: '危险', type: 'danger' },
          },
        ],
      },
      {
        text: '诊断与数据',
        icon: 'ri:database-2-line',
        collapsed: true,
        items: [
          {
            text: 'SU 日志',
            link: '/manager/su-log/',
            icon: 'ri:file-list-3-line',
            badge: { text: '诊断', type: 'info' },
          },
          {
            text: '备份与 WebDAV',
            link: '/manager/backup/',
            icon: 'ri:cloud-line',
            badge: { text: '推荐', type: 'tip' },
          },
          {
            text: '/data/adb 存储结构',
            link: '/manager/storage/',
            icon: 'ri:folder-settings-line',
            badge: { text: '勿改', type: 'danger' },
          },
        ],
      },
    ],
  },
  {
    text: '内置能力',
    icon: 'ri:apps-2-line',
    badge: { text: '核心', type: 'tip' },
    collapsed: true,
    items: [
      {
        text: '内置能力总览',
        link: '/builtin/',
        icon: 'ri:apps-2-line',
      },
      {
        text: 'Folk Mount',
        link: '/builtin/folk-mount/',
        icon: 'ri:hard-drive-3-line',
      },
      {
        text: 'FolkZygisk',
        link: '/builtin/folkzygisk/',
        icon: 'ri:apps-2-line',
      },
      {
        text: 'Sui 服务',
        link: '/builtin/sui/',
        icon: 'ri:water-flash-line',
        badge: { text: '需 Zygisk', type: 'warning' },
      },
      {
        text: 'UTS View',
        link: '/builtin/uts-view/',
        icon: 'ri:text',
      },
      {
        text: '网络隔离',
        link: '/builtin/net-isolate/',
        icon: 'ri:wifi-off-line',
        badge: { text: '谨慎', type: 'warning' },
      },
      {
        text: 'NoMount',
        link: '/builtin/nomount/',
        icon: 'ri:folder-forbid-line',
        badge: { text: '进阶', type: 'info' },
      },
      {
        text: 'SUSFS',
        link: '/builtin/susfs/',
        icon: 'ri:shield-keyhole-line',
        badge: { text: '需内核', type: 'warning' },
      },
    ],
  },
  {
    text: '主题',
    icon: 'ri:brush-3-line',
    badge: { text: '自定义', type: 'tip' },
    collapsed: true,
    items: [
      {
        text: '主题基础',
        icon: 'ri:palette-line',
        collapsed: true,
        items: [
          {
            text: '主题预览与使用',
            link: '/theme/theme-use/',
            icon: 'ri:eye-line',
          },
          {
            text: '成为主题开发者',
            link: '/theme/theme-dev/',
            icon: 'ri:brush-3-line',
            badge: { text: '进阶', type: 'info' },
          },
          {
            text: '导出主题',
            link: '/theme/export/info/',
            icon: 'material-symbols:save-as-outline',
          },
          {
            text: '分享主题',
            link: '/theme/export/share/',
            icon: 'material-symbols:share-outline',
          },
        ],
      },
      {
        text: '全局特性',
        icon: 'ri:contrast-2-line',
        collapsed: true,
        items: [
          {
            text: '夜间模式、颜色与标题',
            link: '/theme/global/theme-style/',
            icon: 'material-symbols:palette-outline',
          },
          {
            text: '背景模糊',
            link: '/theme/global/blur/',
            icon: 'material-symbols:blur-on',
          },
          {
            text: '透明度与表面',
            link: '/theme/global/transparency/',
            icon: 'material-symbols:opacity',
          },
          {
            text: '自定义字体',
            link: '/theme/global/font/',
            icon: 'material-symbols:font-download-outline',
          },
          {
            text: '背景音乐',
            link: '/theme/global/music/',
            icon: 'material-symbols:headphones',
          },
          {
            text: '音效',
            link: '/theme/global/sound/',
            icon: 'material-symbols:volume-up',
          },
          {
            text: '震动触感',
            link: '/theme/global/haptic/',
            icon: 'material-symbols:vibration',
          },
          {
            text: '视频背景',
            link: '/theme/global/video/',
            icon: 'material-symbols:movie',
          },
        ],
      },
      {
        text: 'CircleUI',
        icon: 'ri:circle-line',
        collapsed: true,
        items: [
          { text: '概览', link: '/theme/theme-circle/start/', icon: 'ri:home-4-line' },
          {
            text: '基础样式',
            link: '/theme/theme-circle/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          { text: '背景', link: '/theme/theme-circle/background/', icon: 'ri:image-line' },
          {
            text: '工作卡片',
            link: '/theme/theme-circle/working-card/',
            icon: 'ri:empathize-line',
          },
          {
            text: '组件隐藏',
            link: '/theme/theme-circle/component-hiding/',
            icon: 'ri:eye-off-line',
          },
          {
            text: '交互',
            link: '/theme/theme-circle/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'GridUI',
        icon: 'ri:layout-grid-line',
        collapsed: true,
        items: [
          { text: '概览', link: '/theme/theme-grid/', icon: 'ri:home-4-line' },
          {
            text: '基础样式',
            link: '/theme/theme-grid/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          { text: '背景', link: '/theme/theme-grid/background/', icon: 'ri:image-line' },
          {
            text: '工作卡片',
            link: '/theme/theme-grid/working-card/',
            icon: 'ri:empathize-line',
          },
          {
            text: '组件隐藏',
            link: '/theme/theme-grid/component-hiding/',
            icon: 'ri:eye-off-line',
          },
          {
            text: '交互',
            link: '/theme/theme-grid/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'FocusUI',
        icon: 'ri:focus-3-line',
        collapsed: true,
        items: [
          { text: '概览', link: '/theme/theme-focus/', icon: 'ri:home-4-line' },
          {
            text: '基础样式',
            link: '/theme/theme-focus/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          { text: '硬件信息', link: '/theme/theme-focus/hardware/', icon: 'ri:cpu-line' },
          { text: '平板适配', link: '/theme/theme-focus/tablet/', icon: 'ri:tablet-line' },
          {
            text: '交互',
            link: '/theme/theme-focus/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'DashboardUI',
        icon: 'ri:dashboard-line',
        collapsed: true,
        items: [
          {
            text: '概览',
            link: '/theme/theme-dashboard/start/',
            icon: 'ri:home-4-line',
          },
          {
            text: '基础样式',
            link: '/theme/theme-dashboard/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          {
            text: '状态信息',
            link: '/theme/theme-dashboard/hardware/',
            icon: 'ri:cpu-line',
          },
          {
            text: '统计入口',
            link: '/theme/theme-dashboard/statistics/',
            icon: 'ri:pie-chart-line',
          },
          {
            text: '交互',
            link: '/theme/theme-dashboard/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'StatsUI',
        icon: 'ri:bar-chart-2-line',
        collapsed: true,
        items: [
          { text: '概览', link: '/theme/theme-statsui/start/', icon: 'ri:home-4-line' },
          {
            text: '基础样式',
            link: '/theme/theme-statsui/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          {
            text: '硬件监控',
            link: '/theme/theme-statsui/hardware/',
            icon: 'ri:cpu-line',
          },
          {
            text: '统计信息',
            link: '/theme/theme-statsui/statistics/',
            icon: 'ri:pie-chart-line',
          },
          {
            text: '交互',
            link: '/theme/theme-statsui/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
    ],
  },
  {
    text: '开发',
    icon: 'ri:code-s-slash-line',
    badge: { text: '进阶', type: 'info' },
    collapsed: true,
    items: [
      {
        text: '开始开发',
        icon: 'ri:rocket-2-line',
        collapsed: true,
        items: [
          { text: '开发总览', link: '/developer/', icon: 'ri:book-open-line' },
          { text: '构建与发布', link: '/developer/build/', icon: 'ri:hammer-line' },
        ],
      },
      {
        text: '内核与接口',
        icon: 'ri:cpu-line',
        collapsed: true,
        items: [
          {
            text: 'Built-In 内核集成',
            link: '/developer/builtin-integration/',
            icon: 'ri:terminal-box-line',
            badge: { text: '高风险', type: 'danger' },
          },
          { text: 'SuperCall 与 UAPI', link: '/developer/supercall/', icon: 'ri:plug-line' },
        ],
      },
      {
        text: '标准模块',
        icon: 'ri:box-3-line',
        collapsed: true,
        items: [
          { text: '标准模块开发', link: '/developer/module-development/', icon: 'ri:file-code-line' },
          { text: '模块配置', link: '/developer/module-config/', icon: 'ri:settings-4-line' },
        ],
      },
      {
        text: '扩展开发',
        icon: 'ri:puzzle-2-line',
        collapsed: true,
        items: [
          { text: 'Lua 插件包与生命周期', link: '/developer/lua-plugin/', icon: 'ri:braces-line' },
          { text: 'Lua API 参考', link: '/developer/lua-api/', icon: 'ri:code-line' },
          { text: '主题包结构', link: '/developer/theme-package/', icon: 'ri:folder-settings-line' },
        ],
      },
    ],
  },
  {
    text: '排错',
    icon: 'ri:first-aid-kit-line',
    badge: { text: '按需', type: 'info' },
    collapsed: true,
    items: [
      { text: '排错总览', link: '/troubleshoot/', icon: 'ri:first-aid-kit-line' },
      {
        text: '无法开机',
        link: '/troubleshoot/bootloop/',
        icon: 'ri:error-warning-line',
        badge: { text: '紧急', type: 'danger' },
      },
      {
        text: '设备兼容性',
        link: '/troubleshoot/compatibility/',
        icon: 'ri:smartphone-line',
        badge: { text: '先查', type: 'info' },
      },
      {
        text: '检测与隐藏',
        link: '/troubleshoot/detection/',
        icon: 'ri:eye-off-line',
        badge: { text: '进阶', type: 'info' },
      },
      {
        text: '过检测',
        link: '/troubleshoot/pass-detection/',
        icon: 'ri:shield-check-line',
      },
      {
        text: '管理器认证失败',
        link: '/troubleshoot/manager-auth/',
        icon: 'ri:shield-keyhole-line',
      },
      {
        text: 'Root 授权请求不出现',
        link: '/troubleshoot/root-request/',
        icon: 'ri:admin-line',
      },
      { text: '驱动运行异常', link: '/troubleshoot/runtime/', icon: 'ri:cpu-line' },
      {
        text: '日志与反馈',
        link: '/troubleshoot/logs/',
        icon: 'ri:file-list-3-line',
        badge: { text: '反馈', type: 'info' },
      },
      { text: '提交问题报告', link: '/troubleshoot/report/', icon: 'ri:bug-line' },
    ],
  },
]

const guide = defineCollection({
  type: 'doc',
  dir: 'guide',
  linkPrefix: '/guide',
  title: '使用指南',
})

const manager = defineCollection({
  type: 'doc',
  dir: 'manager',
  linkPrefix: '/manager',
  title: '管理器',
})

const builtin = defineCollection({
  type: 'doc',
  dir: 'builtin',
  linkPrefix: '/builtin',
  title: '内置能力',
})

const setting = defineCollection({
  type: 'doc',
  dir: 'setting',
  linkPrefix: '/setting',
  title: '设置',
})

const theme = defineCollection({
  type: 'doc',
  dir: 'theme',
  linkPrefix: '/theme',
  title: '主题',
})

const developer = defineCollection({
  type: 'doc',
  dir: 'developer',
  linkPrefix: '/developer',
  title: '开发',
})

const troubleshoot = defineCollection({
  type: 'doc',
  dir: 'troubleshoot',
  linkPrefix: '/troubleshoot',
  title: '排错',
})

export const zhCollections = defineCollections([
  guide,
  manager,
  builtin,
  setting,
  theme,
  developer,
  troubleshoot,
])

export const enDocsSidebar: ThemeSidebarItem[] = [
  {
    text: 'Start Here',
    icon: 'ri:book-open-line',
    collapsed: false,
    items: [
      { text: 'Introduction', link: '/en/guide/intro/', icon: 'ri:article-line' },
      {
        text: 'Quick Start',
        link: '/en/guide/get-started/',
        icon: 'ri:rocket-line',
        badge: { text: 'Must Read', type: 'warning' },
      },
      {
        text: 'Install & Flash',
        icon: 'ri:download-line',
        badge: { text: 'Recommended', type: 'tip' },
        collapsed: false,
        items: [
          {
            text: 'Installation Options',
            link: '/en/guide/install/',
            icon: 'ri:list-check',
            badge: { text: 'Start Here', type: 'info' },
          },
          {
            text: 'LKM',
            link: '/en/guide/install/lkm/',
            icon: 'ri:download-line',
            badge: { text: 'Recommended', type: 'tip' },
          },
          {
            text: 'Built-In',
            link: '/en/guide/install/built-in/',
            icon: 'ri:code-box-line',
            badge: { text: 'Advanced', type: 'info' },
          },
          {
            text: 'Jailbreak Mode',
            link: '/en/guide/install/jailbreak/',
            icon: 'ri:lock-unlock-line',
            badge: { text: 'Experimental', type: 'warning' },
          },
        ],
      },
      {
        text: 'Switching to FinaSU',
        icon: 'ri:swap-line',
        badge: { text: 'Optional', type: 'info' },
        collapsed: false,
        items: [
          {
            text: 'Magisk',
            link: '/en/guide/switch/magisk/',
            icon: 'material-symbols:security',
          },
          {
            text: 'KernelSU',
            link: '/en/guide/switch/kernelsu/',
            icon: 'material-symbols:developer-mode',
          },
          {
            text: 'APatch',
            link: '/en/guide/switch/apatch/',
            icon: 'material-symbols:extension',
          },
        ],
      },
      {
        text: 'Bootloop Rescue',
        link: '/en/guide/rescue-bootloop/',
        icon: 'ri:first-aid-kit-line',
        badge: { text: 'Important', type: 'danger' },
      },
      {
        text: 'Updating FinaSU',
        link: '/en/guide/update/',
        icon: 'ri:refresh-line',
        badge: { text: 'Maintenance', type: 'info' },
      },
      {
        text: 'FAQ',
        link: '/en/guide/faq/',
        icon: 'material-symbols:help-outline',
        badge: { text: 'Common', type: 'info' },
      },
      {
        text: 'Core Concepts',
        link: '/en/guide/information/',
        icon: 'material-symbols:menu-book',
        badge: { text: 'Beginner', type: 'tip' },
      },
    ],
  },
  {
    text: 'Settings',
    icon: 'ri:settings-3-line',
    collapsed: true,
    items: [
      {
        text: 'Settings Overview',
        link: '/en/setting/overview/',
        icon: 'ri:dashboard-line',
      },
      {
        text: 'General',
        icon: 'ri:settings-4-line',
        collapsed: true,
        items: [
          { text: 'General Settings', link: '/en/setting/general/', icon: 'ri:settings-4-line' },
          { text: 'Updates & Version', link: '/en/setting/update/', icon: 'ri:refresh-line' },
          { text: 'Language', link: '/en/setting/language/', icon: 'ri:translate-2' },
          {
            text: 'App Profile Templates',
            link: '/en/setting/profile-template/',
            icon: 'ri:file-user-line',
            badge: { text: 'Advanced', type: 'info' },
          },
        ],
      },
      {
        text: 'Appearance',
        icon: 'ri:palette-line',
        collapsed: true,
        items: [
          { text: 'Appearance Settings', link: '/en/setting/appearance/', icon: 'ri:palette-line' },
          { text: 'Theme & Color', link: '/en/setting/theme/', icon: 'ri:palette-line' },
          { text: 'Wallpaper', link: '/en/setting/wallpaper/', icon: 'ri:image-line' },
          { text: 'Font', link: '/en/setting/font/', icon: 'ri:font-size-2' },
          {
            text: 'Theme Store',
            link: '/en/setting/theme-store/',
            icon: 'ri:store-3-line',
            badge: { text: 'Recommended', type: 'tip' },
          },
          { text: 'Home & Layout', link: '/en/setting/home/', icon: 'ri:home-4-line' },
        ],
      },
      {
        text: 'Behavior',
        icon: 'ri:hand-coin-line',
        collapsed: true,
        items: [
          { text: 'Behavior Settings', link: '/en/setting/behavior/', icon: 'ri:hand-coin-line' },
          { text: 'Navigation', link: '/en/setting/navigation/', icon: 'ri:compass-3-line' },
          {
            text: 'Floating Navigation Bar',
            link: '/en/setting/floating-bar/',
            icon: 'ri:layout-bottom-line',
          },
          {
            text: 'Interaction & Compatibility',
            link: '/en/setting/interaction/',
            icon: 'ri:hand-coin-line',
          },
          {
            text: 'Module Cards & Banners',
            link: '/en/setting/module-cards/',
            icon: 'ri:image-edit-line',
          },
        ],
      },
      {
        text: 'Features',
        icon: 'ri:apps-2-line',
        collapsed: true,
        items: [
          { text: 'Feature Settings', link: '/en/setting/function/', icon: 'ri:apps-2-line' },
          {
            text: 'Root & Mount Policy',
            link: '/en/setting/root-policy/',
            icon: 'ri:shield-keyhole-line',
            badge: { text: 'Advanced', type: 'info' },
          },
          {
            text: 'Custom Unmount Mountpoints',
            link: '/en/setting/umount-paths/',
            icon: 'ri:attachment-line',
            badge: { text: 'Beginner', type: 'tip' },
          },
        ],
      },
      {
        text: 'Security',
        icon: 'ri:shield-keyhole-line',
        collapsed: true,
        items: [
          { text: 'Security Settings', link: '/en/setting/security/', icon: 'ri:shield-keyhole-line' },
          { text: 'Hiding & Kernel Security', link: '/en/setting/hiding/', icon: 'ri:eye-off-line' },
          {
            text: 'Access Control',
            link: '/en/setting/access/',
            icon: 'ri:lock-password-line',
            badge: { text: 'Caution', type: 'warning' },
          },
        ],
      },
      {
        text: 'Verification',
        link: '/en/setting/authentication/',
        icon: 'ri:fingerprint-line',
        badge: { text: 'Recommended', type: 'tip' },
      },
      {
        text: 'Multimedia',
        link: '/en/setting/multimedia/',
        icon: 'ri:volume-up-line',
      },
      {
        text: 'Backup',
        link: '/en/setting/backup/',
        icon: 'ri:archive-line',
        badge: { text: 'Recommended', type: 'tip' },
      },
      {
        text: 'Settings Home Entries',
        icon: 'ri:image-line',
        collapsed: true,
        items: [
          {
            text: 'Wallpaper Gallery',
            link: '/en/setting/wallpaper-gallery/',
            icon: 'ri:image-line',
          },
          {
            text: 'Diagnostics & Feedback',
            link: '/en/setting/diagnostics/',
            icon: 'ri:bug-line',
            badge: { text: 'Feedback', type: 'info' },
          },
          {
            text: 'Maintenance & Risky Actions',
            link: '/en/setting/maintenance/',
            icon: 'ri:tools-line',
            badge: { text: 'Danger', type: 'danger' },
          },
          { text: 'About', link: '/en/setting/about/', icon: 'ri:information-line' },
        ],
      },
    ],
  },
  {
    text: 'Manager',
    icon: 'ri:smartphone-line',
    collapsed: true,
    items: [
      {
        text: 'Manager Overview',
        link: '/en/manager/feature/',
        icon: 'ri:lightbulb-line',
      },
      {
        text: 'Apps & Permissions',
        icon: 'ri:admin-line',
        collapsed: true,
        items: [
          { text: 'Superuser', link: '/en/manager/superuser/', icon: 'ri:admin-line' },
          {
            text: 'App Profile',
            link: '/en/manager/app-profile/',
            icon: 'ri:user-settings-line',
            badge: { text: 'Advanced', type: 'info' },
          },
          {
            text: 'Dynamic Manager',
            link: '/en/manager/dynamic-manager/',
            icon: 'ri:shield-keyhole-line',
            badge: { text: 'Advanced', type: 'info' },
          },
        ],
      },
      {
        text: 'Modules & Extensions',
        icon: 'ri:apps-2-line',
        collapsed: true,
        items: [
          {
            text: 'Module Management',
            link: '/en/manager/module/',
            icon: 'ri:puzzle-line',
            badge: { text: 'Common', type: 'tip' },
          },
          {
            text: 'Module Repository',
            link: '/en/manager/module-store/',
            icon: 'ri:store-3-line',
            badge: { text: 'Recommended', type: 'tip' },
          },
          {
            text: 'Lua Plugins',
            link: '/en/manager/plugin/',
            icon: 'ri:braces-line',
            badge: { text: 'Advanced', type: 'info' },
          },
          {
            text: 'Kernel Modules',
            link: '/en/manager/kernel-module/',
            icon: 'ri:cpu-line',
            badge: { text: 'Danger', type: 'danger' },
          },
        ],
      },
      {
        text: 'Diagnostics & Data',
        icon: 'ri:database-2-line',
        collapsed: true,
        items: [
          {
            text: 'SU Logs',
            link: '/en/manager/su-log/',
            icon: 'ri:file-list-3-line',
            badge: { text: 'Diagnostic', type: 'info' },
          },
          {
            text: 'Backup & WebDAV',
            link: '/en/manager/backup/',
            icon: 'ri:cloud-line',
            badge: { text: 'Recommended', type: 'tip' },
          },
          {
            text: '/data/adb Storage Layout',
            link: '/en/manager/storage/',
            icon: 'ri:folder-settings-line',
            badge: { text: 'Do Not Edit', type: 'danger' },
          },
        ],
      },
    ],
  },
  {
    text: 'Built-In Capabilities',
    icon: 'ri:apps-2-line',
    badge: { text: 'Core', type: 'tip' },
    collapsed: true,
    items: [
      {
        text: 'Built-In Capabilities Overview',
        link: '/en/builtin/',
        icon: 'ri:apps-2-line',
      },
      {
        text: 'Folk Mount',
        link: '/en/builtin/folk-mount/',
        icon: 'ri:hard-drive-3-line',
      },
      {
        text: 'FolkZygisk',
        link: '/en/builtin/folkzygisk/',
        icon: 'ri:apps-2-line',
      },
      {
        text: 'Sui Service',
        link: '/en/builtin/sui/',
        icon: 'ri:water-flash-line',
        badge: { text: 'Requires Zygisk', type: 'warning' },
      },
      {
        text: 'UTS View',
        link: '/en/builtin/uts-view/',
        icon: 'ri:text',
      },
      {
        text: 'Network Isolation',
        link: '/en/builtin/net-isolate/',
        icon: 'ri:wifi-off-line',
        badge: { text: 'Caution', type: 'warning' },
      },
      {
        text: 'NoMount',
        link: '/en/builtin/nomount/',
        icon: 'ri:folder-forbid-line',
        badge: { text: 'Advanced', type: 'info' },
      },
      {
        text: 'SUSFS',
        link: '/en/builtin/susfs/',
        icon: 'ri:shield-keyhole-line',
        badge: { text: 'Kernel Required', type: 'warning' },
      },
    ],
  },
  {
    text: 'Themes',
    icon: 'ri:brush-3-line',
    badge: { text: 'Customizable', type: 'tip' },
    collapsed: true,
    items: [
      {
        text: 'Theme Basics',
        icon: 'ri:palette-line',
        collapsed: true,
        items: [
          {
            text: 'Theme Preview & Usage',
            link: '/en/theme/theme-use/',
            icon: 'ri:eye-line',
          },
          {
            text: 'Become a Theme Developer',
            link: '/en/theme/theme-dev/',
            icon: 'ri:brush-3-line',
            badge: { text: 'Advanced', type: 'info' },
          },
          {
            text: 'Export Theme',
            link: '/en/theme/export/info/',
            icon: 'material-symbols:save-as-outline',
          },
          {
            text: 'Share Theme',
            link: '/en/theme/export/share/',
            icon: 'material-symbols:share-outline',
          },
        ],
      },
      {
        text: 'Global Features',
        icon: 'ri:contrast-2-line',
        collapsed: true,
        items: [
          {
            text: 'Dark Mode, Colors & Titles',
            link: '/en/theme/global/theme-style/',
            icon: 'material-symbols:palette-outline',
          },
          {
            text: 'Background Blur',
            link: '/en/theme/global/blur/',
            icon: 'material-symbols:blur-on',
          },
          {
            text: 'Transparency & Surfaces',
            link: '/en/theme/global/transparency/',
            icon: 'material-symbols:opacity',
          },
          {
            text: 'Custom Fonts',
            link: '/en/theme/global/font/',
            icon: 'material-symbols:font-download-outline',
          },
          {
            text: 'Background Music',
            link: '/en/theme/global/music/',
            icon: 'material-symbols:headphones',
          },
          {
            text: 'Sound Effects',
            link: '/en/theme/global/sound/',
            icon: 'material-symbols:volume-up',
          },
          {
            text: 'Haptics',
            link: '/en/theme/global/haptic/',
            icon: 'material-symbols:vibration',
          },
          {
            text: 'Video Background',
            link: '/en/theme/global/video/',
            icon: 'material-symbols:movie',
          },
        ],
      },
      {
        text: 'CircleUI',
        icon: 'ri:circle-line',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/en/theme/theme-circle/start/', icon: 'ri:home-4-line' },
          {
            text: 'Basic Styling',
            link: '/en/theme/theme-circle/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          { text: 'Background', link: '/en/theme/theme-circle/background/', icon: 'ri:image-line' },
          {
            text: 'Working Card',
            link: '/en/theme/theme-circle/working-card/',
            icon: 'ri:empathize-line',
          },
          {
            text: 'Component Hiding',
            link: '/en/theme/theme-circle/component-hiding/',
            icon: 'ri:eye-off-line',
          },
          {
            text: 'Interaction',
            link: '/en/theme/theme-circle/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'GridUI',
        icon: 'ri:layout-grid-line',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/en/theme/theme-grid/', icon: 'ri:home-4-line' },
          {
            text: 'Basic Styling',
            link: '/en/theme/theme-grid/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          { text: 'Background', link: '/en/theme/theme-grid/background/', icon: 'ri:image-line' },
          {
            text: 'Working Card',
            link: '/en/theme/theme-grid/working-card/',
            icon: 'ri:empathize-line',
          },
          {
            text: 'Component Hiding',
            link: '/en/theme/theme-grid/component-hiding/',
            icon: 'ri:eye-off-line',
          },
          {
            text: 'Interaction',
            link: '/en/theme/theme-grid/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'FocusUI',
        icon: 'ri:focus-3-line',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/en/theme/theme-focus/', icon: 'ri:home-4-line' },
          {
            text: 'Basic Styling',
            link: '/en/theme/theme-focus/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          { text: 'Hardware Info', link: '/en/theme/theme-focus/hardware/', icon: 'ri:cpu-line' },
          { text: 'Tablet Layout', link: '/en/theme/theme-focus/tablet/', icon: 'ri:tablet-line' },
          {
            text: 'Interaction',
            link: '/en/theme/theme-focus/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'DashboardUI',
        icon: 'ri:dashboard-line',
        collapsed: true,
        items: [
          {
            text: 'Overview',
            link: '/en/theme/theme-dashboard/start/',
            icon: 'ri:home-4-line',
          },
          {
            text: 'Basic Styling',
            link: '/en/theme/theme-dashboard/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          {
            text: 'Status Info',
            link: '/en/theme/theme-dashboard/hardware/',
            icon: 'ri:cpu-line',
          },
          {
            text: 'Statistics Entry',
            link: '/en/theme/theme-dashboard/statistics/',
            icon: 'ri:pie-chart-line',
          },
          {
            text: 'Interaction',
            link: '/en/theme/theme-dashboard/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
      {
        text: 'StatsUI',
        icon: 'ri:bar-chart-2-line',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/en/theme/theme-statsui/start/', icon: 'ri:home-4-line' },
          {
            text: 'Basic Styling',
            link: '/en/theme/theme-statsui/basic/',
            icon: 'material-symbols:format-list-bulleted',
          },
          {
            text: 'Hardware Monitoring',
            link: '/en/theme/theme-statsui/hardware/',
            icon: 'ri:cpu-line',
          },
          {
            text: 'Statistics',
            link: '/en/theme/theme-statsui/statistics/',
            icon: 'ri:pie-chart-line',
          },
          {
            text: 'Interaction',
            link: '/en/theme/theme-statsui/interaction/',
            icon: 'ri:mouse-line',
          },
        ],
      },
    ],
  },
  {
    text: 'Development',
    icon: 'ri:code-s-slash-line',
    badge: { text: 'Advanced', type: 'info' },
    collapsed: true,
    items: [
      {
        text: 'Getting Started',
        icon: 'ri:rocket-2-line',
        collapsed: true,
        items: [
          { text: 'Development Overview', link: '/en/developer/', icon: 'ri:book-open-line' },
          { text: 'Build & Release', link: '/en/developer/build/', icon: 'ri:hammer-line' },
        ],
      },
      {
        text: 'Kernel & Interfaces',
        icon: 'ri:cpu-line',
        collapsed: true,
        items: [
          {
            text: 'Built-In Kernel Integration',
            link: '/en/developer/builtin-integration/',
            icon: 'ri:terminal-box-line',
            badge: { text: 'High Risk', type: 'danger' },
          },
          { text: 'SuperCall & UAPI', link: '/en/developer/supercall/', icon: 'ri:plug-line' },
        ],
      },
      {
        text: 'Standard Modules',
        icon: 'ri:box-3-line',
        collapsed: true,
        items: [
          { text: 'Standard Module Development', link: '/en/developer/module-development/', icon: 'ri:file-code-line' },
          { text: 'Module Configuration', link: '/en/developer/module-config/', icon: 'ri:settings-4-line' },
        ],
      },
      {
        text: 'Extension Development',
        icon: 'ri:puzzle-2-line',
        collapsed: true,
        items: [
          { text: 'Lua Plugin Package & Lifecycle', link: '/en/developer/lua-plugin/', icon: 'ri:braces-line' },
          { text: 'Lua API Reference', link: '/en/developer/lua-api/', icon: 'ri:code-line' },
          { text: 'Theme Package Structure', link: '/en/developer/theme-package/', icon: 'ri:folder-settings-line' },
        ],
      },
    ],
  },
  {
    text: 'Troubleshooting',
    icon: 'ri:first-aid-kit-line',
    badge: { text: 'Optional', type: 'info' },
    collapsed: true,
    items: [
      { text: 'Troubleshooting Overview', link: '/en/troubleshoot/', icon: 'ri:first-aid-kit-line' },
      {
        text: "Device Won't Boot",
        link: '/en/troubleshoot/bootloop/',
        icon: 'ri:error-warning-line',
        badge: { text: 'Urgent', type: 'danger' },
      },
      {
        text: 'Device Compatibility',
        link: '/en/troubleshoot/compatibility/',
        icon: 'ri:smartphone-line',
        badge: { text: 'Check First', type: 'info' },
      },
      {
        text: 'Detection & Hiding',
        link: '/en/troubleshoot/detection/',
        icon: 'ri:eye-off-line',
        badge: { text: 'Advanced', type: 'info' },
      },
      {
        text: 'Passing Detection',
        link: '/en/troubleshoot/pass-detection/',
        icon: 'ri:shield-check-line',
      },
      {
        text: 'Manager Authentication Failure',
        link: '/en/troubleshoot/manager-auth/',
        icon: 'ri:shield-keyhole-line',
      },
      {
        text: 'No Root Request Prompt',
        link: '/en/troubleshoot/root-request/',
        icon: 'ri:admin-line',
      },
      { text: 'Driver Runtime Errors', link: '/en/troubleshoot/runtime/', icon: 'ri:cpu-line' },
      {
        text: 'Logs & Feedback',
        link: '/en/troubleshoot/logs/',
        icon: 'ri:file-list-3-line',
        badge: { text: 'Feedback', type: 'info' },
      },
      { text: 'Reporting Issues', link: '/en/troubleshoot/report/', icon: 'ri:bug-line' },
    ],
  },
]

export const enCollections = defineCollections([
  defineCollection({
    type: 'doc',
    dir: 'en/guide',
    linkPrefix: '/en/guide',
    title: 'User Guide',
  }),
  defineCollection({
    type: 'doc',
    dir: 'en/manager',
    linkPrefix: '/en/manager',
    title: 'Manager',
  }),
  defineCollection({
    type: 'doc',
    dir: 'en/builtin',
    linkPrefix: '/en/builtin',
    title: 'Built-In Capabilities',
  }),
  defineCollection({
    type: 'doc',
    dir: 'en/setting',
    linkPrefix: '/en/setting',
    title: 'Settings',
  }),
  defineCollection({
    type: 'doc',
    dir: 'en/theme',
    linkPrefix: '/en/theme',
    title: 'Themes',
  }),
  defineCollection({
    type: 'doc',
    dir: 'en/developer',
    linkPrefix: '/en/developer',
    title: 'Development',
  }),
  defineCollection({
    type: 'doc',
    dir: 'en/troubleshoot',
    linkPrefix: '/en/troubleshoot',
    title: 'Troubleshooting',
  }),
])
