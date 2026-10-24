import { defineNavbarConfig } from 'vuepress-theme-plume'

export const zhNavbar = defineNavbarConfig([
  { text: '首页', link: '/', icon: 'ri:home-2-line' },
  { text: '指南', link: '/guide/intro/', icon: 'ri:book-open-line' },
  { text: '管理器', link: '/manager/feature/', icon: 'ri:smartphone-line' },
  { text: '内置能力', link: '/builtin/folkzygisk/', icon: 'ri:apps-2-line' },
  { text: '设置', link: '/setting/overview/', icon: 'ri:settings-3-line' },
  { text: '主题', link: '/theme/theme-use/', icon: 'ri:palette-line' },
  { text: '开发', link: '/developer/build/', icon: 'ri:code-s-slash-line' },
  { text: '排错', link: '/troubleshoot/superuser/faq/', icon: 'ri:question-line' },
])
