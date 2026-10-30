import { useEffect, useState } from "react"

/** 화면에 노출한 페이지보다 몇 페이지 앞까지 캐시에 채워 둘지 */
const PREFETCH_PAGE_COUNT = 2
const STORAGE_PREFIX = "visible-pages:"

interface InfinitePagesSource {
  hasNextPage: boolean
  isFetchingNextPage: boolean
  isFetchNextPageError: boolean
  fetchNextPage: () => unknown
}

function readSavedPageCount(storageKey: string) {
  const saved = Number(sessionStorage.getItem(STORAGE_PREFIX + storageKey))
  return Number.isInteger(saved) && saved >= 1 ? saved : null
}

/**
 * 무한 스크롤에서 화면에 노출할 페이지 수를 직접 관리하고,
 * 캐시가 항상 PREFETCH_PAGE_COUNT만큼 앞서도록 다음 페이지를 미리 받아 둔다.
 * 스크롤이 바닥에 닿으면 이미 받아 둔 페이지를 곧바로 보여 준다.
 * 노출 페이지 수는 storageKey(목록·검색 조건)별로 sessionStorage에 남겨,
 * 뒤로가기·새로고침·캐시 만료 뒤에도 그 페이지 수까지 다시 받아 스크롤을 복원할 수 있게 한다.
 */
export function useBufferedPages(
  loadedPageCount: number,
  query: InfinitePagesSource,
  storageKey: string,
) {
  const [visiblePageCount, setVisiblePageCount] = useState(
    () =>
      readSavedPageCount(storageKey) ??
      Math.max(1, loadedPageCount - (query.hasNextPage ? PREFETCH_PAGE_COUNT : 0)),
  )
  const [appliedStorageKey, setAppliedStorageKey] = useState(storageKey)
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = query

  if (appliedStorageKey !== storageKey) {
    setAppliedStorageKey(storageKey)
    setVisiblePageCount(readSavedPageCount(storageKey) ?? 1)
  }

  useEffect(() => {
    sessionStorage.setItem(STORAGE_PREFIX + storageKey, String(visiblePageCount))
  }, [storageKey, visiblePageCount])

  useEffect(() => {
    if (loadedPageCount === 0 || !hasNextPage) return
    if (isFetchingNextPage || isFetchNextPageError) return
    if (loadedPageCount >= visiblePageCount + PREFETCH_PAGE_COUNT) return
    fetchNextPage()
  }, [
    loadedPageCount,
    visiblePageCount,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    fetchNextPage,
  ])

  return {
    visiblePageCount,
    hasMore: visiblePageCount < loadedPageCount || hasNextPage,
    /** 노출을 요청한 페이지가 아직 도착하지 않은 상태 */
    isWaitingNextPage: visiblePageCount > loadedPageCount,
    showNextPage: () => setVisiblePageCount((count) => Math.min(count + 1, loadedPageCount + 1)),
  }
}
