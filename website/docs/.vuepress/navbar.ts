import { defineNavbarConfig } from 'vuepress-theme-plume'

export const zhNavbar = defineNavbarConfig([
  { text: '首页', link: '/', icon: 'ri:home-2-line' },
  { text: '文档', link: '/guide/intro/', icon: 'ri:book-open-line' },
])
