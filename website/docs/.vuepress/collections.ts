import {
  defineCollection,
  defineCollections,
  type ThemeSidebarItem,
} from 'vuepress-theme-plume'

const docsSidebar: ThemeSidebarItem[] = [
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
          },
          {
            text: 'LKM',
            link: '/guide/install/lkm/',
            icon: 'ri:download-line',
          },
          {
            text: 'Built-In',
            link: '/guide/install/built-in/',
            icon: 'ri:code-box-line',
          },
          {
            text: '越狱模式',
            link: '/guide/install/jailbreak/',
            icon: 'ri:lock-unlock-line',
          },
        ],
      },
      {
        text: '切换到 FinaSU',
        icon: 'ri:swap-line',
        badge: { text: '推荐', type: 'tip' },
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
        text: 'Sui 服务',
        link: '/guide/sui/',
        icon: 'ri:water-flash-line',
        badge: { text: '重磅', type: 'danger' },
      },
      {
        text: '更新 FinaSU',
        link: '/guide/update/',
        icon: 'ri:refresh-line',
        badge: { text: '重要', type: 'danger' },
      },
      {
        text: '常见问题与解答',
        link: '/guide/faq/',
        icon: 'material-symbols:help-outline',
        badge: { text: '必看', type: 'warning' },
      },
      {
        text: '基础概念',
        link: '/guide/information/',
        icon: 'material-symbols:menu-book',
        badge: { text: '必看', type: 'warning' },
      },
    ],
  },
  {
    text: '设置',
    icon: 'ri:settings-3-line',
    badge: { text: 'V3', type: 'danger' },
    collapsed: true,
    items: [
      {
        text: '设置总览',
        link: '/setting/overview/',
        icon: 'ri:dashboard-line',
      },
      {
        text: '基础设置',
        icon: 'ri:list-check',
        collapsed: true,
        items: [
          { text: '更新与版本', link: '/setting/update/', icon: 'ri:refresh-line' },
          { text: '语言', link: '/setting/language/', icon: 'ri:translate-2' },
          {
            text: 'App Profile 模板',
            link: '/setting/profile-template/',
            icon: 'ri:file-user-line',
          },
        ],
      },
      {
        text: '外观',
        icon: 'ri:palette-line',
        collapsed: true,
        items: [
          { text: '主题与颜色', link: '/setting/theme/', icon: 'ri:palette-line' },
          { text: '壁纸', link: '/setting/wallpaper/', icon: 'ri:image-line' },
          { text: '字体', link: '/setting/font/', icon: 'ri:font-size-2' },
          { text: '主题商店', link: '/setting/theme-store/', icon: 'ri:store-3-line' },
          { text: '首页与布局', link: '/setting/home/', icon: 'ri:home-4-line' },
        ],
      },
      {
        text: '行为',
        icon: 'ri:hand-coin-line',
        collapsed: true,
        items: [
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
        text: '功能与安全',
        icon: 'ri:shield-keyhole-line',
        collapsed: true,
        items: [
          {
            text: 'Root 与挂载策略',
            link: '/setting/root-policy/',
            icon: 'ri:shield-keyhole-line',
          },
          { text: '隐藏与内核安全', link: '/setting/hiding/', icon: 'ri:eye-off-line' },
          { text: '访问控制', link: '/setting/access/', icon: 'ri:lock-password-line' },
          { text: '内置能力', link: '/setting/builtin/', icon: 'ri:apps-2-line' },
          {
            text: '身份验证',
            link: '/setting/authentication/',
            icon: 'ri:fingerprint-line',
          },
        ],
      },
      {
        text: '数据与维护',
        icon: 'ri:database-2-line',
        collapsed: true,
        items: [
          { text: '多媒体', link: '/setting/multimedia/', icon: 'ri:volume-up-line' },
          { text: '备份', link: '/setting/backup/', icon: 'ri:archive-line' },
          {
            text: '维护与危险操作',
            link: '/setting/maintenance/',
            icon: 'ri:tools-line',
          },
          { text: '诊断与反馈', link: '/setting/diagnostics/', icon: 'ri:bug-line' },
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
      { text: '超级用户', link: '/manager/superuser/', icon: 'ri:admin-line' },
      { text: 'App Profile', link: '/manager/app-profile/', icon: 'ri:user-settings-line' },
      { text: '模块管理', link: '/manager/module/', icon: 'ri:puzzle-line' },
      { text: '模块仓库', link: '/manager/module-store/', icon: 'ri:store-3-line' },
      { text: 'Lua 插件', link: '/manager/plugin/', icon: 'ri:braces-line' },
      { text: '内核模块', link: '/manager/kernel-module/', icon: 'ri:cpu-line' },
      {
        text: '数据与权限',
        icon: 'ri:database-2-line',
        collapsed: true,
        items: [
          { text: 'SU 日志', link: '/manager/su-log/', icon: 'ri:file-list-3-line' },
          {
            text: '备份与 WebDAV',
            link: '/manager/backup/',
            icon: 'ri:cloud-line',
          },
          {
            text: '动态管理器',
            link: '/manager/dynamic-manager/',
            icon: 'ri:smartphone-line',
          },
          {
            text: '/data/adb 存储结构',
            link: '/manager/storage/',
            icon: 'ri:folder-settings-line',
          },
        ],
      },
    ],
  },
  {
    text: '内置能力',
    icon: 'ri:apps-2-line',
    badge: { text: '重磅', type: 'danger' },
    collapsed: true,
    items: [
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
      { text: 'NoMount', link: '/builtin/nomount/', icon: 'ri:folder-forbid-line' },
      {
        text: 'SUSFS 与内核级隐藏',
        link: '/builtin/susfs/',
        icon: 'ri:shield-keyhole-line',
        badge: { text: '进阶', type: 'info' },
      },
    ],
  },
  {
    text: '主题',
    icon: 'ri:brush-3-line',
    badge: { text: '推荐', type: 'tip' },
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
            badge: { text: '推荐', type: 'tip' },
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
            text: '夜间模式与颜色',
            link: '/theme/global/theme-style/',
            icon: 'material-symbols:palette-outline',
          },
          {
            text: '背景模糊',
            link: '/theme/global/blur/',
            icon: 'material-symbols:blur-on',
          },
          {
            text: '透明度',
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
      { text: '构建 FinaSU', link: '/developer/build/', icon: 'ri:hammer-line' },
      {
        text: '内核集成',
        link: '/developer/builtin-integration/',
        icon: 'ri:terminal-box-line',
        badge: { text: '进阶', type: 'info' },
      },
      {
        text: 'SuperCall 与 UAPI',
        link: '/developer/supercall/',
        icon: 'ri:plug-line',
      },
      {
        text: '模块配置',
        link: '/developer/module-config/',
        icon: 'ri:settings-4-line',
      },
      {
        text: 'Lua 插件接口',
        link: '/developer/lua-plugin/',
        icon: 'ri:braces-line',
      },
      {
        text: '主题包结构',
        link: '/developer/theme-package/',
        icon: 'ri:folder-settings-line',
      },
    ],
  },
  {
    text: '排错',
    icon: 'ri:first-aid-kit-line',
    badge: { text: '必看', type: 'danger' },
    collapsed: true,
    items: [
      { text: '无法开机', link: '/troubleshoot/bootloop/', icon: 'ri:error-warning-line' },
      {
        text: '设备兼容性',
        link: '/troubleshoot/compatibility/',
        icon: 'ri:smartphone-line',
      },
      { text: '检测与隐藏', link: '/troubleshoot/detection/', icon: 'ri:eye-off-line' },
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
      { text: '日志与反馈', link: '/troubleshoot/logs/', icon: 'ri:file-list-3-line' },
      { text: '提交问题报告', link: '/troubleshoot/report/', icon: 'ri:bug-line' },
    ],
  },
]

const guide = defineCollection({
  type: 'doc',
  dir: 'guide',
  linkPrefix: '/guide',
  title: '使用指南',
  sidebar: docsSidebar,
})

const manager = defineCollection({
  type: 'doc',
  dir: 'manager',
  linkPrefix: '/manager',
  title: '管理器',
  sidebar: docsSidebar,
})

const builtin = defineCollection({
  type: 'doc',
  dir: 'builtin',
  linkPrefix: '/builtin',
  title: '内置能力',
  sidebar: docsSidebar,
})

const setting = defineCollection({
  type: 'doc',
  dir: 'setting',
  linkPrefix: '/setting',
  title: '设置',
  sidebar: docsSidebar,
})

const theme = defineCollection({
  type: 'doc',
  dir: 'theme',
  linkPrefix: '/theme',
  title: '主题',
  sidebar: docsSidebar,
})

const developer = defineCollection({
  type: 'doc',
  dir: 'developer',
  linkPrefix: '/developer',
  title: '开发',
  sidebar: docsSidebar,
})

const troubleshoot = defineCollection({
  type: 'doc',
  dir: 'troubleshoot',
  linkPrefix: '/troubleshoot',
  title: '排错',
  sidebar: docsSidebar,
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
