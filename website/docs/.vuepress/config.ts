/**
 * VuePress 与 Plume 的基础配置。
 */

import { viteBundler } from '@vuepress/bundler-vite'
import { defineUserConfig } from 'vuepress'
import { plumeTheme } from 'vuepress-theme-plume'

export default defineUserConfig({
  base: '/',
  lang: 'zh-CN',
  locales: {
    '/': {
      title: 'FinaSU',
      lang: 'zh-CN',
      description: '基于内核的 Android Root 方案',
    },
    // 英文站预留：页面就绪后再挂载 '/en/' locale。
  },

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/icon.png' }],
  ],

  bundler: viteBundler(),
  shouldPrefetch: false,

  docsRepo: 'https://github.com/LyraVoid/FinaSU',
  docsDir: 'website',
  docsBranch: 'refactor/docs',

  theme: plumeTheme({
    /* 版权信息 */
    copyright: true,

    /**
     * 编译缓存。
     */
    cache: 'filesystem',

    search: { provider: 'local' },

    codeHighlighter: {
      twoslash: false,
      whitespace: false,
      lineNumbers: true,
    },

    markdown: {
      collapse: true,
      table: true,
      mermaid: true,
      codeTree: true,
      fileTree: true,
      codeTabs: { icon: true },
      field: true,
      timeline: true,
      abbr: true,
      annotation: true,
      mark: 'eager',
      icon: { provider: 'iconify' },
      caniuse: true,
      qrcode: true,
      chat: true,
    },
  }),
})
