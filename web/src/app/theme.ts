import { useSyncExternalStore } from 'react'
import { useSettings, type ThemePreference } from '../services/settings.js'

export type Theme = 'light' | 'dark'

const query = window.matchMedia('(prefers-color-scheme: dark)')
const systemThemeEvent = 'deepseek-system-theme'

const subscribeOsTheme = (listener: () => void) => {
  query.addEventListener('change', listener)
  window.addEventListener(systemThemeEvent, listener)
  return () => {
    query.removeEventListener('change', listener)
    window.removeEventListener(systemThemeEvent, listener)
  }
}

const getOsTheme = (): Theme => {
  const androidTheme = window.DeepSeekAndroid?.getSystemTheme()
  if (androidTheme === 'dark' || androidTheme === 'light') {
    return androidTheme
  }
  return query.matches ? 'dark' : 'light'
}

const resolveTheme = (preference: ThemePreference, osTheme: Theme): Theme =>
  preference === 'system' ? osTheme : preference

/** 当前实际生效的深浅色：手动选择优先，「跟随系统」时取系统设置 */
export const useTheme = (): Theme => {
  const preference = useSettings((state) => state.themePreference)
  const osTheme = useSyncExternalStore(subscribeOsTheme, getOsTheme)
  return resolveTheme(preference, osTheme)
}

const syncPageBackground = () => {
  const backgroundColor = getComputedStyle(document.body).backgroundColor
  document.documentElement.style.backgroundColor = backgroundColor
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', backgroundColor)
}

/** 同步页面、原生系统栏和支持 theme-color 的浏览器栏。 */
const applyTheme = (theme: Theme) => {
  const body = document.body
  body.classList.add('change-theme')
  body.classList.toggle('dark', theme === 'dark')
  body.classList.toggle('light', theme === 'light')
  if (theme === 'dark') {
    body.setAttribute('data-ds-dark-theme', 'dark')
  } else {
    body.removeAttribute('data-ds-dark-theme')
  }
  document.documentElement.style.colorScheme = theme
  window.DeepSeekAndroid?.setTheme(theme)
  if (document.readyState === 'complete') {
    syncPageBackground()
  }
  setTimeout(() => {
    body.classList.remove('change-theme')
  }, 0)
}

let appliedTheme: Theme | null = null

const syncTheme = () => {
  const theme = resolveTheme(useSettings.getState().themePreference, getOsTheme())
  if (theme !== appliedTheme) {
    appliedTheme = theme
    applyTheme(theme)
  }
}

/** 首屏渲染前同步应用一次，之后随应用设置与系统深浅色变化更新 */
export const setupTheme = () => {
  syncTheme()
  if (document.readyState !== 'complete') {
    window.addEventListener('load', syncPageBackground, { once: true })
  }
  query.addEventListener('change', syncTheme)
  window.addEventListener(systemThemeEvent, syncTheme)
  useSettings.subscribe(syncTheme)
}
