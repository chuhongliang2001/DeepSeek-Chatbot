import clsx from 'clsx'
import { useEffect, useRef, useState, type PointerEvent } from 'react'
import { useTranslate } from '../../i18n/index.js'
import { useChatStore } from '../../services/chat/store.js'
import idleImage from '../../assets/mascot-idle.webp'
import workingImage from '../../assets/mascot-working.webp'
import styles from './Mascot.module.css'

type Point = { x: number; y: number }

const POSITION_KEY = 'chat-demo:mascot-position'
const defaultPosition = (): Point => ({ x: Math.max(12, window.innerWidth - 106), y: 88 })

const readPosition = (): Point => {
  try {
    const value = JSON.parse(localStorage.getItem(POSITION_KEY) ?? 'null')
    if (Number.isFinite(value?.x) && Number.isFinite(value?.y)) {
      return { x: value.x, y: value.y }
    }
  } catch {
    // 使用默认位置
  }
  return defaultPosition()
}

const savePosition = (point: Point) => {
  try {
    localStorage.setItem(POSITION_KEY, JSON.stringify(point))
  } catch {
    // 当前会话仍保留位置
  }
}

export const Mascot = () => {
  const t = useTranslate()
  const rootRef = useRef<HTMLButtonElement>(null)
  const anchorRef = useRef<Point>(readPosition())
  const positionRef = useRef<Point>(anchorRef.current)
  const [position, setPosition] = useState<Point>(positionRef.current)
  const [message, setMessage] = useState<string | null>(null)
  const [interacting, setInteracting] = useState(false)
  const interactionIndex = useRef(0)
  const interactionTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const suppressClick = useRef(false)
  const drag = useRef({
    pointerId: -1,
    startX: 0,
    startY: 0,
    originX: 0,
    originY: 0,
    moved: false,
  })
  const working = useChatStore(
    (state) => state.activeId !== null && Object.hasOwn(state.streaming, state.activeId)
  )

  const clamp = (point: Point): Point => {
    const element = rootRef.current
    const viewport = window.visualViewport
    const viewportLeft = viewport?.offsetLeft ?? 0
    const viewportTop = viewport?.offsetTop ?? 0
    const viewportWidth = viewport?.width ?? window.innerWidth
    const viewportHeight = viewport?.height ?? window.innerHeight
    const width = element?.offsetWidth ?? 94
    const height = element?.offsetHeight ?? 96
    const minX = viewportLeft + 8
    const minY = viewportTop + 72
    return {
      x: Math.min(Math.max(minX, point.x), Math.max(minX, viewportLeft + viewportWidth - width - 8)),
      y: Math.min(Math.max(minY, point.y), Math.max(minY, viewportTop + viewportHeight - height - 152)),
    }
  }

  const moveTo = (point: Point, remember = false) => {
    if (remember) {
      anchorRef.current = point
    }
    const next = clamp(point)
    positionRef.current = next
    setPosition(next)
  }

  useEffect(() => {
    const viewport = window.visualViewport
    let frame = 0
    moveTo(anchorRef.current)
    const onViewportChange = () => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => moveTo(anchorRef.current))
    }
    window.addEventListener('resize', onViewportChange)
    viewport?.addEventListener('resize', onViewportChange)
    viewport?.addEventListener('scroll', onViewportChange)
    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('resize', onViewportChange)
      viewport?.removeEventListener('resize', onViewportChange)
      viewport?.removeEventListener('scroll', onViewportChange)
      clearTimeout(interactionTimer.current)
    }
  }, [])

  const interact = () => {
    const messages = [
      t('mascotHello'),
      t('mascotDrag'),
      t('mascotFocus'),
      t('mascotCheer'),
    ]
    setMessage(messages[interactionIndex.current % messages.length])
    interactionIndex.current += 1
    setInteracting(false)
    requestAnimationFrame(() => setInteracting(true))
    clearTimeout(interactionTimer.current)
    interactionTimer.current = setTimeout(() => {
      setMessage(null)
      setInteracting(false)
    }, 5000)
  }

  const onPointerDown = (event: PointerEvent<HTMLButtonElement>) => {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return
    }
    event.currentTarget.setPointerCapture(event.pointerId)
    suppressClick.current = false
    drag.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: positionRef.current.x,
      originY: positionRef.current.y,
      moved: false,
    }
  }

  const onPointerMove = (event: PointerEvent<HTMLButtonElement>) => {
    const current = drag.current
    if (current.pointerId !== event.pointerId) {
      return
    }
    const dx = event.clientX - current.startX
    const dy = event.clientY - current.startY
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
      current.moved = true
    }
    if (current.moved) {
      event.preventDefault()
      moveTo({ x: current.originX + dx, y: current.originY + dy }, true)
    }
  }

  const onPointerUp = (event: PointerEvent<HTMLButtonElement>) => {
    if (drag.current.pointerId !== event.pointerId) {
      return
    }
    if (drag.current.moved) {
      savePosition(anchorRef.current)
      suppressClick.current = true
    }
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    drag.current.pointerId = -1
  }

  const bubble = working ? t('mascotWorking') : message
  const bubbleRight = position.x > window.innerWidth / 2

  return (
    <button
      ref={rootRef}
      type="button"
      className={clsx(styles.root, working && styles.working, interacting && styles.interacting)}
      style={{ transform: `translate3d(${position.x}px, ${position.y}px, 0)` }}
      aria-label={t('mascotLabel')}
      data-mascot
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={() => {
        drag.current.pointerId = -1
      }}
      onClick={() => {
        if (suppressClick.current) {
          suppressClick.current = false
        } else {
          interact()
        }
      }}
    >
      {bubble ? (
        <div
          className={clsx(
            styles.bubble,
            bubbleRight ? styles.bubbleRight : styles.bubbleLeft,
            working && styles.workingBubble
          )}
          role="status"
        >
          <div className={styles.bubbleText}>
            <span>{bubble}</span>
            {working ? (
              <span className={styles.dots} aria-hidden>
                <i />
                <i />
                <i />
              </span>
            ) : null}
          </div>
          {working ? <span className={styles.progress} aria-hidden /> : null}
        </div>
      ) : null}
      <div className={styles.character}>
        <img className={styles.idleImage} src={idleImage} alt="" draggable={false} />
        <img className={styles.workingImage} src={workingImage} alt="" draggable={false} />
      </div>
    </button>
  )
}
