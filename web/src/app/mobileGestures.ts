import { closeSearchPanel, useSearchPanel } from '../components/search/searchPanelState.js'
import { closeSettings, useSettingsDialog } from '../components/settings/settingsDialogState.js'
import {
  closeMobileSider,
  openMobileSider,
  useMobileSider,
} from '../components/sider/mobileSiderState.js'

export const handleNativeBack = (): boolean => {
  if (useSettingsDialog.getState().open) {
    closeSettings()
    return true
  }
  if (useSearchPanel.getState().entry) {
    closeSearchPanel()
    return true
  }
  if (useMobileSider.getState().open) {
    closeMobileSider()
    return true
  }
  return false
}

export const bindNativeBack = () => {
  window.DeepSeekHandleBack = handleNativeBack
  return () => {
    delete window.DeepSeekHandleBack
  }
}

/** 主界面右滑打开历史，抽屉打开后左滑关闭；垂直滚动、输入和人物拖动不参与。 */
export const bindMobileGestures = (enabled: boolean) => {
  if (!enabled) {
    return
  }
  let startX = 0
  let startY = 0
  let startedAt = 0
  let horizontal = false
  let tracking = false

  const onTouchStart = (event: TouchEvent) => {
    if (event.touches.length !== 1 || useSettingsDialog.getState().open) {
      tracking = false
      return
    }
    const target = event.target instanceof Element ? event.target : null
    if (target?.closest('[data-mascot], input, textarea, select, pre, [contenteditable="true"]')) {
      tracking = false
      return
    }
    const touch = event.touches[0]
    startX = touch.clientX
    startY = touch.clientY
    startedAt = event.timeStamp
    horizontal = false
    tracking = true
  }

  const onTouchMove = (event: TouchEvent) => {
    if (!tracking || event.touches.length !== 1) {
      return
    }
    const touch = event.touches[0]
    const dx = touch.clientX - startX
    const dy = touch.clientY - startY
    const drawerOpen = useMobileSider.getState().open
    const allowed = drawerOpen ? dx < 0 : dx > 0
    if (allowed && Math.abs(dx) >= 12 && Math.abs(dx) > Math.abs(dy) * 1.3) {
      horizontal = true
    }
    if (horizontal && event.cancelable) {
      event.preventDefault()
    }
  }

  const onTouchEnd = (event: TouchEvent) => {
    if (!tracking) {
      return
    }
    const touch = event.changedTouches[0]
    const dx = touch.clientX - startX
    const distance = Math.abs(dx)
    const duration = Math.max(1, event.timeStamp - startedAt)
    const drawerOpen = useMobileSider.getState().open
    const thresholdReached = distance >= 64 || (distance >= 40 && distance / duration >= 0.45)
    if (horizontal && thresholdReached) {
      if (drawerOpen && dx < 0) {
        closeMobileSider()
      } else if (!drawerOpen && dx > 0 && !useSearchPanel.getState().entry) {
        openMobileSider()
      }
      if (event.cancelable) {
        event.preventDefault()
      }
    }
    tracking = false
    horizontal = false
  }

  const onTouchCancel = () => {
    tracking = false
    horizontal = false
  }

  document.addEventListener('touchstart', onTouchStart, { passive: true })
  document.addEventListener('touchmove', onTouchMove, { passive: false })
  document.addEventListener('touchend', onTouchEnd, { passive: false })
  document.addEventListener('touchcancel', onTouchCancel)
  return () => {
    document.removeEventListener('touchstart', onTouchStart)
    document.removeEventListener('touchmove', onTouchMove)
    document.removeEventListener('touchend', onTouchEnd)
    document.removeEventListener('touchcancel', onTouchCancel)
  }
}
