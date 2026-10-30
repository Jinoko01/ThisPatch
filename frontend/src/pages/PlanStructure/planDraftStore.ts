import { create } from "zustand"
import { createJSONStorage, persist } from "zustand/middleware"
import type { PlanSlot, PlanStructure } from "@/types"

interface PlanDraft {
  text: string
  excludedGenreIds: number[]
  structure: PlanStructure | null
  editedSlots: PlanSlot[] | null
}

const EMPTY_DRAFT: PlanDraft = {
  text: "",
  excludedGenreIds: [],
  structure: null,
  editedSlots: null,
}

interface PlanDraftState {
  drafts: Record<number, PlanDraft>
  updateDraft: (gameId: number, changes: Partial<PlanDraft>) => void
}

/** 새로고침해도 같은 탭에서는 작성 중인 기획안이 남도록 sessionStorage에 보관한다. */
const usePlanDraftStore = create<PlanDraftState>()(
  persist(
    (set) => ({
      drafts: {},
      updateDraft: (gameId, changes) =>
        set((state) => ({
          drafts: {
            ...state.drafts,
            [gameId]: { ...(state.drafts[gameId] ?? EMPTY_DRAFT), ...changes },
          },
        })),
    }),
    {
      name: "plan-drafts",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ drafts: state.drafts }),
    },
  ),
)

/** 유사 사례 검색 화면을 오가거나 새로고침한 뒤에도 남아 있어야 하는 기획안 입력값. */
export function usePlanDraft(gameId: number): PlanDraft {
  return usePlanDraftStore((state) => state.drafts[gameId] ?? EMPTY_DRAFT)
}

/** 같은 탭에서 다른 계정이 로그인해도 이전 계정의 기획안이 보이지 않도록 로그아웃 시 비운다. */
export function clearPlanDrafts() {
  usePlanDraftStore.setState({ drafts: {} })
}

export function useUpdatePlanDraft(): PlanDraftState["updateDraft"] {
  return usePlanDraftStore((state) => state.updateDraft)
}
