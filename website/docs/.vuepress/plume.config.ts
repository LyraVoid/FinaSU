/**
 * Plume 主题配置。
 */

import { defineThemeConfig } from 'vuepress-theme-plume'
import { zhCollections } from './collections'
import { zhNavbar } from './navbar'

/**
 */
export default defineThemeConfig({
  logo: '/icon.png',

  social: [
    { icon: 'github', link: 'https://github.com/LyraVoid/FinaSU' },
    { icon: 'telegram', link: 'https://t.me/FinaSU' },
  ],

  appearance: true,

  locales: {
    '/': {
      profile: {
        avatar: '/icon.png',
        name: 'FinaSU',
        description: '基于内核的 Android Root 方案',
      },

      navbar: zhNavbar,
      collections: zhCollections,
    },
    // 英文站预留：页面就绪后再挂载 '/en/' locale。
  },

  footer: {
    message: false,
    copyright: 'Copyright © 2026-present LyraVoid/FinaSU',
  },

  transition: {
    page: true,
    appearance: 'fade',
  },
})
