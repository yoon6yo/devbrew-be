# DevBrew FE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증 없는 관리자 전용 React SPA — DevBrew 파이프라인 아이디어 조회·거절·export 대시보드를 `devbrew-fe` 레포에 빌드하고 k8s devbrew 네임스페이스에 자동 배포

**Architecture:** React 19 + Vite SPA. TanStack Query v5로 서버 상태 및 낙관적 업데이트 관리. native fetch wrapper로 BE API 호출. nginx:alpine Docker 이미지로 정적 파일 서빙, devbrew k8s 네임스페이스에 BE와 공유 배포.

**Tech Stack:** React 19, TypeScript 5 (strict), Vite 6, TanStack Query v5, React Router v7, Tailwind CSS, shadcn/ui (New York), Vitest + React Testing Library, MSW v2

## Global Constraints

- Node.js ≥ 20
- TypeScript strict mode (`"strict": true`) — `any` 금지
- TanStack Query v5 flat options API — `cacheTime` → `gcTime`, `useQuery`의 `onSuccess`/`onError` 삭제됨 (mutation은 그대로)
- React Router v7 library mode — `createBrowserRouter` + `RouterProvider`
- k8s namespace: `devbrew` (BE와 공유)
- GHCR image: `ghcr.io/yoon6yo/devbrew-fe`
- 모든 컴포넌트: loading / empty / error 상태 필수
- `GET /api/ideas/**` 는 BE에서 이미 `permitAll()` — `POST /api/ideas/*/reject` 만 Task 11에서 추가 필요

---

## File Map

```
/home/yoon6yo/project/devbrew-fe/
├── src/
│   ├── api/
│   │   ├── client.ts           — fetch wrapper, ApiError 클래스
│   │   ├── client.test.ts
│   │   ├── ideas.ts            — getIdeas, getIdea, rejectIdea, getTopIdeas
│   │   └── ideas.test.ts
│   ├── components/
│   │   ├── StatusBadge.tsx     — IdeaStatus → 색상 배지
│   │   ├── StatusBadge.test.tsx
│   │   ├── TrackBadge.tsx      — SourceTrack → 배지
│   │   ├── TrackBadge.test.tsx
│   │   ├── ScoreBar.tsx        — 0–10 점수 시각화
│   │   ├── ScoreBar.test.tsx
│   │   ├── SummaryCards.tsx    — 상단 상태별 카운트 4개
│   │   ├── IdeaCard.tsx        — 그리드 카드
│   │   ├── IdeaCard.test.tsx
│   │   ├── IdeaModal.tsx       — 상세 모달 + 거절 버튼
│   │   ├── IdeaModal.test.tsx
│   │   ├── Pagination.tsx      — 페이지 네비게이션
│   │   ├── Pagination.test.tsx
│   │   └── ExportButton.tsx    — Top 5 JSON/CSV 다운로드
│   ├── hooks/
│   │   ├── useIdeas.ts
│   │   ├── useIdeas.test.ts
│   │   ├── useIdeaDetail.ts
│   │   ├── useRejectIdea.ts
│   │   └── useRejectIdea.test.ts
│   ├── pages/
│   │   ├── DashboardPage.tsx
│   │   └── DashboardPage.test.tsx
│   ├── types/
│   │   └── index.ts
│   ├── utils/
│   │   ├── dateFormat.ts
│   │   ├── exportJson.ts
│   │   ├── exportJson.test.ts
│   │   ├── exportCsv.ts
│   │   └── exportCsv.test.ts
│   ├── test/
│   │   ├── setup.ts            — MSW 서버 라이프사이클
│   │   ├── server.ts           — MSW 핸들러
│   │   └── fixtures.ts         — mock IdeaDto 데이터
│   ├── App.tsx
│   └── main.tsx
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
├── nginx.conf
├── Dockerfile
├── .github/workflows/
│   ├── ci.yml
│   └── cd.yml
├── vite.config.ts
├── tsconfig.json
└── package.json
```

---

### Task 1: Project Scaffold

**Files:**
- Create: `/home/yoon6yo/project/devbrew-fe/` (전체 디렉터리)
- Create: `package.json`, `vite.config.ts`, `tsconfig.json`
- Create: `src/main.tsx`, `src/App.tsx`, `src/index.css`
- Create: `src/test/setup.ts`, `src/test/server.ts`, `src/test/fixtures.ts`

**Interfaces:**
- Produces: `npm run dev` (localhost:5173), `npm test`, `npm run build` 동작

- [ ] **Step 1: Vite 프로젝트 초기화**

```bash
cd /home/yoon6yo/project
npm create vite@latest devbrew-fe -- --template react-ts
cd devbrew-fe
```

- [ ] **Step 2: 의존성 설치**

```bash
npm install @tanstack/react-query react-router-dom
npm install -D vitest @testing-library/react @testing-library/user-event @testing-library/jest-dom jsdom msw
```

- [ ] **Step 3: shadcn/ui 초기화**

```bash
npx shadcn@latest init
# 프롬프트: Style=New York, Base color=Zinc, CSS variables=yes
npx shadcn@latest add badge button card dialog skeleton tabs
```

- [ ] **Step 4: `vite.config.ts` 작성**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
})
```

- [ ] **Step 5: `tsconfig.json` path alias 추가**

`compilerOptions`에 다음 추가:
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] },
    "strict": true
  }
}
```

- [ ] **Step 6: `src/test/fixtures.ts` 작성**

```typescript
import type { IdeaDto, PageResponse } from '@/types'

export const mockIdea: IdeaDto = {
  id: 1,
  title: 'AI 기반 코드 리뷰 SaaS',
  description: '개발자를 위한 AI 코드 리뷰 플랫폼.',
  sourceTrack: 'SAAS',
  sourceUrl: 'https://example.com',
  score: 8,
  scoreReason: 'PMF 명확, 시장 규모 큼',
  status: 'SCORED',
  createdAt: '2026-07-26T09:00:00+09:00',
}

export const mockIdeas: IdeaDto[] = [
  mockIdea,
  { ...mockIdea, id: 2, title: 'GitHub 트렌드 분석 툴', sourceTrack: 'GITHUB', score: 6, status: 'PENDING' },
  { ...mockIdea, id: 3, title: '바이럴 마케팅 자동화', sourceTrack: 'VIRAL', score: 9, status: 'NOTIFIED' },
]

export const mockPage: PageResponse<IdeaDto> = {
  content: mockIdeas,
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
}
```

- [ ] **Step 7: `src/test/server.ts` 초기 작성**

```typescript
import { setupServer } from 'msw/node'
export const server = setupServer()
```

- [ ] **Step 8: `src/test/setup.ts` 작성**

```typescript
import '@testing-library/jest-dom'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
```

- [ ] **Step 9: `src/main.tsx` 작성**

```typescript
import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>
)
```

- [ ] **Step 10: `src/App.tsx` 작성**

```typescript
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import DashboardPage from '@/pages/DashboardPage'

const router = createBrowserRouter([
  { path: '/', element: <DashboardPage /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
```

- [ ] **Step 11: 빌드 확인**

```bash
npm run build
```
Expected: `dist/` 생성, TypeScript 에러 없음

- [ ] **Step 12: git init + 첫 커밋**

```bash
git init
echo "node_modules\ndist\n.env" > .gitignore
git add .
git commit -m "feat: project scaffold — Vite + React 19 + TanStack Query + shadcn/ui"
```

---

### Task 2: Types

**Files:**
- Create: `src/types/index.ts`

**Interfaces:**
- Produces: `IdeaDto`, `IdeaStatus`, `SourceTrack`, `PageResponse<T>` — 이후 모든 Task에서 사용

- [ ] **Step 1: `src/types/index.ts` 작성**

```typescript
export type IdeaStatus = 'PENDING' | 'SCORED' | 'NOTIFIED' | 'REJECTED'
export type SourceTrack = 'SAAS' | 'GITHUB' | 'VIRAL'

export interface IdeaDto {
  id: number
  title: string
  description: string
  sourceTrack: SourceTrack
  sourceUrl: string
  score: number | null
  scoreReason: string | null
  status: IdeaStatus
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
```

- [ ] **Step 2: TypeScript 컴파일 확인**

```bash
npx tsc --noEmit
```
Expected: 에러 없음

- [ ] **Step 3: 커밋**

```bash
git add src/types/index.ts
git commit -m "feat: add IdeaDto, IdeaStatus, SourceTrack, PageResponse types"
```

---

### Task 3: API Layer

**Files:**
- Create: `src/api/client.ts`, `src/api/client.test.ts`
- Create: `src/api/ideas.ts`, `src/api/ideas.test.ts`
- Modify: `src/test/server.ts` (MSW 핸들러 완성)

**Interfaces:**
- Consumes: `IdeaDto`, `IdeaStatus`, `PageResponse<T>` from `@/types`
- Produces:
  - `apiFetch<T>(path: string, init?: RequestInit): Promise<T>`
  - `getIdeas(params?: { status?: IdeaStatus; page?: number; size?: number }): Promise<PageResponse<IdeaDto>>`
  - `getIdea(id: number): Promise<IdeaDto>`
  - `rejectIdea(id: number): Promise<IdeaDto>`
  - `getTopIdeas(n?: number): Promise<IdeaDto[]>`

- [ ] **Step 1: 실패 테스트 작성 — `apiFetch` 에러 처리**

```typescript
// src/api/client.test.ts
import { describe, it, expect } from 'vitest'
import { server } from '@/test/server'
import { http, HttpResponse } from 'msw'
import { apiFetch } from './client'

describe('apiFetch', () => {
  it('throws ApiError on non-2xx response', async () => {
    server.use(
      http.get('/api/test', () => HttpResponse.json({ message: 'Not found' }, { status: 404 }))
    )
    await expect(apiFetch('/api/test')).rejects.toMatchObject({ status: 404 })
  })

  it('returns parsed JSON on 2xx', async () => {
    server.use(
      http.get('/api/test', () => HttpResponse.json({ ok: true }))
    )
    const result = await apiFetch<{ ok: boolean }>('/api/test')
    expect(result.ok).toBe(true)
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npx vitest run src/api/client.test.ts
```
Expected: FAIL (apiFetch not defined)

- [ ] **Step 3: `src/api/client.ts` 구현**

```typescript
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    const body = await res.text()
    throw new ApiError(res.status, `${res.status}: ${body}`)
  }
  return res.json() as Promise<T>
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
npx vitest run src/api/client.test.ts
```
Expected: PASS

- [ ] **Step 5: MSW 핸들러 완성 (`src/test/server.ts`)**

```typescript
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { mockPage, mockIdeas } from './fixtures'

export const server = setupServer(
  http.get('/api/ideas', ({ request }) => {
    const url = new URL(request.url)
    const size = Number(url.searchParams.get('size') ?? 20)
    const page = Number(url.searchParams.get('page') ?? 0)
    return HttpResponse.json({
      ...mockPage,
      content: mockIdeas.slice(page * size, page * size + size),
    })
  }),
  http.get('/api/ideas/:id', ({ params }) => {
    const idea = mockIdeas.find((i) => i.id === Number(params.id))
    if (!idea) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json(idea)
  }),
  http.post('/api/ideas/:id/reject', ({ params }) => {
    const idea = mockIdeas.find((i) => i.id === Number(params.id))
    if (!idea) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json({ ...idea, status: 'REJECTED' })
  })
)
```

- [ ] **Step 6: 실패 테스트 작성 — ideas API 함수**

```typescript
// src/api/ideas.test.ts
import { describe, it, expect } from 'vitest'
import { getIdeas, getIdea, rejectIdea, getTopIdeas } from './ideas'

describe('getIdeas', () => {
  it('fetches paginated ideas with score desc sort', async () => {
    const result = await getIdeas({ page: 0, size: 20 })
    expect(result.content).toHaveLength(3)
    expect(result.totalElements).toBe(3)
  })
})

describe('getIdea', () => {
  it('fetches a single idea by id', async () => {
    const idea = await getIdea(1)
    expect(idea.id).toBe(1)
    expect(idea.title).toBe('AI 기반 코드 리뷰 SaaS')
  })
})

describe('rejectIdea', () => {
  it('POSTs to reject endpoint and returns updated dto', async () => {
    const idea = await rejectIdea(1)
    expect(idea.status).toBe('REJECTED')
  })
})

describe('getTopIdeas', () => {
  it('returns array of ideas', async () => {
    const ideas = await getTopIdeas(5)
    expect(Array.isArray(ideas)).toBe(true)
  })
})
```

- [ ] **Step 7: 테스트 실패 확인**

```bash
npx vitest run src/api/ideas.test.ts
```
Expected: FAIL

- [ ] **Step 8: `src/api/ideas.ts` 구현**

```typescript
import { apiFetch } from './client'
import type { IdeaDto, IdeaStatus, PageResponse } from '@/types'

interface GetIdeasParams {
  status?: IdeaStatus
  page?: number
  size?: number
}

export function getIdeas({ status, page = 0, size = 20 }: GetIdeasParams = {}): Promise<PageResponse<IdeaDto>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'score,desc' })
  if (status) params.set('status', status)
  return apiFetch(`/api/ideas?${params}`)
}

export function getIdea(id: number): Promise<IdeaDto> {
  return apiFetch(`/api/ideas/${id}`)
}

export function rejectIdea(id: number): Promise<IdeaDto> {
  return apiFetch(`/api/ideas/${id}/reject`, { method: 'POST' })
}

export async function getTopIdeas(n = 5): Promise<IdeaDto[]> {
  const page = await getIdeas({ page: 0, size: n })
  return page.content
}
```

- [ ] **Step 9: 전체 API 테스트 통과 확인**

```bash
npx vitest run src/api/
```
Expected: PASS

- [ ] **Step 10: 커밋**

```bash
git add src/api/ src/test/server.ts
git commit -m "feat: add fetch wrapper and ideas API layer with MSW tests"
```

---

### Task 4: TanStack Query Hooks

**Files:**
- Create: `src/hooks/useIdeas.ts`, `src/hooks/useIdeas.test.ts`
- Create: `src/hooks/useIdeaDetail.ts`
- Create: `src/hooks/useRejectIdea.ts`, `src/hooks/useRejectIdea.test.ts`

**Interfaces:**
- Consumes: `getIdeas`, `getIdea`, `rejectIdea` from `@/api/ideas`; `IdeaStatus`, `IdeaDto`, `PageResponse` from `@/types`
- Produces:
  - `useIdeas({ status?: IdeaStatus; page?: number }): UseQueryResult<PageResponse<IdeaDto>>`
  - `useIdeaDetail(id: number | null): UseQueryResult<IdeaDto>` — `enabled: id !== null`
  - `useRejectIdea(): UseMutationResult` — `onMutate` 낙관적 업데이트, `onSettled` invalidate `['ideas']`

- [ ] **Step 1: 실패 테스트 — `useIdeas`**

```typescript
// src/hooks/useIdeas.test.ts
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect } from 'vitest'
import React from 'react'
import { useIdeas } from './useIdeas'

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useIdeas', () => {
  it('returns paginated idea list', async () => {
    const { result } = renderHook(() => useIdeas({}), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content).toHaveLength(3)
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npx vitest run src/hooks/useIdeas.test.ts
```
Expected: FAIL

- [ ] **Step 3: `src/hooks/useIdeas.ts` 구현**

```typescript
import { useQuery } from '@tanstack/react-query'
import { getIdeas } from '@/api/ideas'
import type { IdeaStatus } from '@/types'

interface UseIdeasParams {
  status?: IdeaStatus
  page?: number
}

export function useIdeas({ status, page = 0 }: UseIdeasParams) {
  return useQuery({
    queryKey: ['ideas', { status, page }],
    queryFn: () => getIdeas({ status, page }),
    staleTime: 30_000,
  })
}
```

- [ ] **Step 4: `src/hooks/useIdeaDetail.ts` 구현**

```typescript
import { useQuery } from '@tanstack/react-query'
import { getIdea } from '@/api/ideas'

export function useIdeaDetail(id: number | null) {
  return useQuery({
    queryKey: ['idea', id],
    queryFn: () => getIdea(id!),
    enabled: id !== null,
    staleTime: 30_000,
  })
}
```

- [ ] **Step 5: 실패 테스트 — `useRejectIdea` 낙관적 업데이트**

```typescript
// src/hooks/useRejectIdea.test.ts
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect } from 'vitest'
import React from 'react'
import { useRejectIdea } from './useRejectIdea'
import { mockPage } from '@/test/fixtures'

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  qc.setQueryData(['ideas', { status: undefined, page: 0 }], mockPage)
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useRejectIdea', () => {
  it('calls mutate without throwing', async () => {
    const { result } = renderHook(() => useRejectIdea(), { wrapper })
    act(() => { result.current.mutate(1) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })
})
```

- [ ] **Step 6: 테스트 실패 확인**

```bash
npx vitest run src/hooks/useRejectIdea.test.ts
```
Expected: FAIL

- [ ] **Step 7: `src/hooks/useRejectIdea.ts` 구현**

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { rejectIdea } from '@/api/ideas'
import type { IdeaDto, PageResponse } from '@/types'

export function useRejectIdea() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => rejectIdea(id),
    onMutate: async (id: number) => {
      await queryClient.cancelQueries({ queryKey: ['ideas'] })
      const snapshots = queryClient.getQueriesData<PageResponse<IdeaDto>>({ queryKey: ['ideas'] })
      queryClient.setQueriesData<PageResponse<IdeaDto>>(
        { queryKey: ['ideas'] },
        (old) => old
          ? { ...old, content: old.content.map((idea) => idea.id === id ? { ...idea, status: 'REJECTED' as const } : idea) }
          : old
      )
      return { snapshots }
    },
    onError: (_err, _id, context) => {
      context?.snapshots.forEach(([queryKey, data]) => {
        queryClient.setQueryData(queryKey, data)
      })
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['ideas'] })
    },
  })
}
```

- [ ] **Step 8: 모든 hook 테스트 통과 확인**

```bash
npx vitest run src/hooks/
```
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/hooks/
git commit -m "feat: add useIdeas, useIdeaDetail, useRejectIdea with optimistic update"
```

---

### Task 5: Atomic Components (StatusBadge, TrackBadge, ScoreBar, utils)

**Files:**
- Create: `src/components/StatusBadge.tsx`, `src/components/StatusBadge.test.tsx`
- Create: `src/components/TrackBadge.tsx`, `src/components/TrackBadge.test.tsx`
- Create: `src/components/ScoreBar.tsx`, `src/components/ScoreBar.test.tsx`
- Create: `src/utils/dateFormat.ts`

**Interfaces:**
- Consumes: `IdeaStatus`, `SourceTrack` from `@/types`
- Produces:
  - `StatusBadge({ status: IdeaStatus }): JSX.Element`
  - `TrackBadge({ track: SourceTrack }): JSX.Element`
  - `ScoreBar({ score: number | null }): JSX.Element`
  - `formatDate(iso: string): string`

- [ ] **Step 1: 실패 테스트 — StatusBadge**

```typescript
// src/components/StatusBadge.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('renders NOTIFIED with green class', () => {
    render(<StatusBadge status="NOTIFIED" />)
    expect(screen.getByText('NOTIFIED').className).toMatch(/green/)
  })
  it.each([['SCORED', 'blue'], ['PENDING', 'amber'], ['REJECTED', 'gray']] as const)(
    'renders %s with %s class', (status, color) => {
      render(<StatusBadge status={status} />)
      expect(screen.getByText(status).className).toMatch(color)
    }
  )
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npx vitest run src/components/StatusBadge.test.tsx
```
Expected: FAIL

- [ ] **Step 3: `src/components/StatusBadge.tsx` 구현**

```typescript
import type { IdeaStatus } from '@/types'

const colorMap: Record<IdeaStatus, string> = {
  NOTIFIED: 'bg-green-100 text-green-700 border-green-200',
  SCORED:   'bg-blue-100 text-blue-700 border-blue-200',
  PENDING:  'bg-amber-100 text-amber-700 border-amber-200',
  REJECTED: 'bg-gray-100 text-gray-500 border-gray-200',
}

export function StatusBadge({ status }: { status: IdeaStatus }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${colorMap[status]}`}>
      {status}
    </span>
  )
}
```

- [ ] **Step 4: `src/components/TrackBadge.tsx` 구현 + 테스트**

테스트 (`src/components/TrackBadge.test.tsx`):
```typescript
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TrackBadge } from './TrackBadge'

describe('TrackBadge', () => {
  it.each(['SAAS', 'GITHUB', 'VIRAL'] as const)('renders %s', (track) => {
    render(<TrackBadge track={track} />)
    expect(screen.getByText(track)).toBeInTheDocument()
  })
})
```

구현:
```typescript
import type { SourceTrack } from '@/types'

const trackStyle: Record<SourceTrack, string> = {
  SAAS:   'bg-purple-100 text-purple-700 border-purple-200',
  GITHUB: 'bg-neutral-900 text-white border-neutral-700',
  VIRAL:  'bg-orange-100 text-orange-700 border-orange-200',
}

export function TrackBadge({ track }: { track: SourceTrack }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${trackStyle[track]}`}>
      {track}
    </span>
  )
}
```

- [ ] **Step 5: `src/components/ScoreBar.tsx` 구현 + 테스트**

테스트 (`src/components/ScoreBar.test.tsx`):
```typescript
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { ScoreBar } from './ScoreBar'

describe('ScoreBar', () => {
  it('renders score number', () => {
    render(<ScoreBar score={8} />)
    expect(screen.getByText('8')).toBeInTheDocument()
  })
  it('renders em dash when score is null', () => {
    render(<ScoreBar score={null} />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })
})
```

구현:
```typescript
function barColor(score: number): string {
  if (score >= 8) return 'bg-green-500'
  if (score >= 6) return 'bg-blue-500'
  return 'bg-gray-300'
}

export function ScoreBar({ score }: { score: number | null }) {
  if (score === null) return <span className="text-gray-400 text-sm">—</span>
  const pct = Math.min(100, (score / 10) * 100)
  return (
    <div className="flex items-center gap-2">
      <div className="w-20 h-1.5 bg-gray-200 rounded-full overflow-hidden">
        <div className={`h-full rounded-full ${barColor(score)}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-sm font-semibold tabular-nums">{score}</span>
    </div>
  )
}
```

- [ ] **Step 6: `src/utils/dateFormat.ts` 작성**

```typescript
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    timeZone: 'Asia/Seoul',
  }).format(new Date(iso))
}
```

- [ ] **Step 7: 모든 atomic 테스트 통과 확인**

```bash
npx vitest run src/components/StatusBadge.test.tsx src/components/TrackBadge.test.tsx src/components/ScoreBar.test.tsx
```
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add src/components/StatusBadge.tsx src/components/TrackBadge.tsx src/components/ScoreBar.tsx src/utils/dateFormat.ts
git commit -m "feat: add StatusBadge, TrackBadge, ScoreBar atomic components and dateFormat util"
```

---

### Task 6: Dashboard Components (IdeaCard, IdeaModal, SummaryCards, Pagination)

**Files:**
- Create: `src/components/IdeaCard.tsx`, `src/components/IdeaCard.test.tsx`
- Create: `src/components/IdeaModal.tsx`, `src/components/IdeaModal.test.tsx`
- Create: `src/components/SummaryCards.tsx`
- Create: `src/components/Pagination.tsx`, `src/components/Pagination.test.tsx`

**Interfaces:**
- Consumes: `StatusBadge`, `TrackBadge`, `ScoreBar`; `useIdeaDetail`, `useRejectIdea`; `formatDate`; `IdeaDto`, `PageResponse`
- Produces:
  - `IdeaCard({ idea: IdeaDto; onClick: () => void }): JSX.Element`
  - `IdeaModal({ ideaId: number | null; onClose: () => void }): JSX.Element | null`
  - `SummaryCards({ data: PageResponse<IdeaDto> | undefined; isLoading: boolean }): JSX.Element`
  - `Pagination({ page: number; totalPages: number; onPageChange: (p: number) => void }): JSX.Element | null`

- [ ] **Step 1: 실패 테스트 — IdeaCard**

```typescript
// src/components/IdeaCard.test.tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { IdeaCard } from './IdeaCard'
import { mockIdea } from '@/test/fixtures'

describe('IdeaCard', () => {
  it('renders title and score', () => {
    render(<IdeaCard idea={mockIdea} onClick={() => {}} />)
    expect(screen.getByText(mockIdea.title)).toBeInTheDocument()
    expect(screen.getByText('8')).toBeInTheDocument()
  })
  it('calls onClick when clicked', async () => {
    const onClick = vi.fn()
    render(<IdeaCard idea={mockIdea} onClick={onClick} />)
    await userEvent.click(screen.getByRole('article'))
    expect(onClick).toHaveBeenCalledOnce()
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npx vitest run src/components/IdeaCard.test.tsx
```
Expected: FAIL

- [ ] **Step 3: `src/components/IdeaCard.tsx` 구현**

```typescript
import type { IdeaDto } from '@/types'
import { StatusBadge } from './StatusBadge'
import { TrackBadge } from './TrackBadge'
import { ScoreBar } from './ScoreBar'
import { formatDate } from '@/utils/dateFormat'

export function IdeaCard({ idea, onClick }: { idea: IdeaDto; onClick: () => void }) {
  return (
    <article
      role="article"
      onClick={onClick}
      className="cursor-pointer rounded-xl border bg-white p-4 hover:border-zinc-300 hover:shadow-sm transition-all"
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <h3 className="text-sm font-semibold text-zinc-900 line-clamp-2 flex-1">{idea.title}</h3>
        <TrackBadge track={idea.sourceTrack} />
      </div>
      <div className="flex items-center justify-between">
        <ScoreBar score={idea.score} />
        <StatusBadge status={idea.status} />
      </div>
      <p className="mt-2 text-xs text-zinc-400">{formatDate(idea.createdAt)}</p>
    </article>
  )
}
```

- [ ] **Step 4: `src/components/SummaryCards.tsx` 구현**

```typescript
import type { IdeaDto, IdeaStatus, PageResponse } from '@/types'

const STATUSES: { status: IdeaStatus; label: string; color: string }[] = [
  { status: 'NOTIFIED', label: '알림 완료', color: 'text-green-600' },
  { status: 'SCORED',   label: '채점 완료', color: 'text-blue-600' },
  { status: 'PENDING',  label: '대기 중',   color: 'text-amber-600' },
  { status: 'REJECTED', label: '거절됨',    color: 'text-gray-400' },
]

export function SummaryCards({ data, isLoading }: { data: PageResponse<IdeaDto> | undefined; isLoading: boolean }) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
      {STATUSES.map(({ status, label, color }) => {
        const count = data?.content.filter((i) => i.status === status).length ?? 0
        return (
          <div key={status} className="rounded-xl border bg-white p-4">
            <p className="text-xs text-zinc-500 mb-1">{label}</p>
            {isLoading
              ? <div className="h-7 w-12 bg-zinc-100 animate-pulse rounded" />
              : <p className={`text-2xl font-bold tabular-nums ${color}`}>{count}</p>}
          </div>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 5: 실패 테스트 — IdeaModal**

```typescript
// src/components/IdeaModal.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import React from 'react'
import { IdeaModal } from './IdeaModal'

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('IdeaModal', () => {
  it('renders nothing when ideaId is null', () => {
    const { container } = render(<IdeaModal ideaId={null} onClose={() => {}} />, { wrapper })
    expect(container.firstChild).toBeNull()
  })
  it('renders idea title when open', async () => {
    render(<IdeaModal ideaId={1} onClose={() => {}} />, { wrapper })
    await waitFor(() => expect(screen.getByText('AI 기반 코드 리뷰 SaaS')).toBeInTheDocument())
  })
  it('calls onClose on close button click', async () => {
    const onClose = vi.fn()
    render(<IdeaModal ideaId={1} onClose={onClose} />, { wrapper })
    await waitFor(() => screen.getByText('AI 기반 코드 리뷰 SaaS'))
    await userEvent.click(screen.getByRole('button', { name: /닫기/i }))
    expect(onClose).toHaveBeenCalledOnce()
  })
})
```

- [ ] **Step 6: 테스트 실패 확인**

```bash
npx vitest run src/components/IdeaModal.test.tsx
```
Expected: FAIL

- [ ] **Step 7: `src/components/IdeaModal.tsx` 구현**

```typescript
import { useIdeaDetail } from '@/hooks/useIdeaDetail'
import { useRejectIdea } from '@/hooks/useRejectIdea'
import { StatusBadge } from './StatusBadge'
import { TrackBadge } from './TrackBadge'
import { ScoreBar } from './ScoreBar'
import { formatDate } from '@/utils/dateFormat'
import { Button } from '@/components/ui/button'

export function IdeaModal({ ideaId, onClose }: { ideaId: number | null; onClose: () => void }) {
  const { data: idea, isLoading } = useIdeaDetail(ideaId)
  const reject = useRejectIdea()

  if (ideaId === null) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-2xl w-full max-w-lg mx-4 p-6 shadow-xl max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {isLoading ? (
          <div className="space-y-3">
            {[...Array(4)].map((_, i) => <div key={i} className="h-4 bg-zinc-100 animate-pulse rounded" />)}
          </div>
        ) : idea ? (
          <>
            <div className="flex items-start justify-between gap-3 mb-4">
              <h2 className="text-lg font-bold text-zinc-900 flex-1">{idea.title}</h2>
              <button aria-label="닫기" onClick={onClose} className="text-zinc-400 hover:text-zinc-600 text-2xl leading-none">×</button>
            </div>
            <div className="flex gap-2 mb-4">
              <StatusBadge status={idea.status} />
              <TrackBadge track={idea.sourceTrack} />
            </div>
            <div className="mb-4">
              <p className="text-xs text-zinc-500 mb-1">점수</p>
              <ScoreBar score={idea.score} />
            </div>
            {idea.scoreReason && (
              <div className="mb-4 p-3 bg-zinc-50 rounded-lg">
                <p className="text-xs text-zinc-500 mb-1">채점 이유</p>
                <p className="text-sm text-zinc-700 leading-relaxed">{idea.scoreReason}</p>
              </div>
            )}
            <div className="mb-4">
              <p className="text-xs text-zinc-500 mb-1">설명</p>
              <p className="text-sm text-zinc-700 leading-relaxed">{idea.description}</p>
            </div>
            <a href={idea.sourceUrl} target="_blank" rel="noopener noreferrer"
              className="text-sm text-blue-600 hover:underline break-all block mb-4">
              {idea.sourceUrl}
            </a>
            <p className="text-xs text-zinc-400 mb-4">{formatDate(idea.createdAt)}</p>
            {idea.status !== 'REJECTED' && (
              <Button variant="destructive" size="sm" disabled={reject.isPending}
                onClick={() => reject.mutate(idea.id, { onSuccess: onClose })}>
                {reject.isPending ? '처리 중…' : '거절'}
              </Button>
            )}
          </>
        ) : null}
      </div>
    </div>
  )
}
```

- [ ] **Step 8: 실패 테스트 — Pagination**

```typescript
// src/components/Pagination.test.tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('renders page numbers', () => {
    render(<Pagination page={0} totalPages={3} onPageChange={() => {}} />)
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })
  it('calls onPageChange with correct 0-based index', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={3} onPageChange={onPageChange} />)
    await userEvent.click(screen.getByText('2'))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })
  it('returns null when totalPages <= 1', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onPageChange={() => {}} />)
    expect(container.firstChild).toBeNull()
  })
})
```

- [ ] **Step 9: `src/components/Pagination.tsx` 구현**

```typescript
export function Pagination({ page, totalPages, onPageChange }: {
  page: number; totalPages: number; onPageChange: (p: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <div className="flex items-center justify-center gap-1 mt-6">
      <button onClick={() => onPageChange(page - 1)} disabled={page === 0}
        className="px-3 py-1.5 rounded-lg text-sm text-zinc-600 hover:bg-zinc-100 disabled:opacity-30">‹</button>
      {[...Array(totalPages)].map((_, i) => (
        <button key={i} onClick={() => onPageChange(i)}
          className={`w-8 h-8 rounded-lg text-sm font-medium ${i === page ? 'bg-zinc-900 text-white' : 'text-zinc-600 hover:bg-zinc-100'}`}>
          {i + 1}
        </button>
      ))}
      <button onClick={() => onPageChange(page + 1)} disabled={page === totalPages - 1}
        className="px-3 py-1.5 rounded-lg text-sm text-zinc-600 hover:bg-zinc-100 disabled:opacity-30">›</button>
    </div>
  )
}
```

- [ ] **Step 10: 모든 컴포넌트 테스트 통과 확인**

```bash
npx vitest run src/components/
```
Expected: PASS

- [ ] **Step 11: 커밋**

```bash
git add src/components/
git commit -m "feat: add IdeaCard, IdeaModal, SummaryCards, Pagination components"
```

---

### Task 7: Export Feature

**Files:**
- Create: `src/utils/exportJson.ts`, `src/utils/exportJson.test.ts`
- Create: `src/utils/exportCsv.ts`, `src/utils/exportCsv.test.ts`
- Create: `src/components/ExportButton.tsx`

**Interfaces:**
- Consumes: `getTopIdeas` from `@/api/ideas`; `IdeaDto` from `@/types`
- Produces:
  - `downloadJson(ideas: IdeaDto[], filename?: string): void`
  - `downloadCsv(ideas: IdeaDto[], filename?: string): void`
  - `ExportButton(): JSX.Element`

- [ ] **Step 1: 실패 테스트 — exportJson**

```typescript
// src/utils/exportJson.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { downloadJson } from './exportJson'
import { mockIdeas } from '@/test/fixtures'

describe('downloadJson', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() })
    const a = { href: '', download: '', click: vi.fn(), remove: vi.fn() } as unknown as HTMLAnchorElement
    vi.spyOn(document, 'createElement').mockReturnValue(a)
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => a)
  })
  it('creates a JSON blob and triggers download', () => {
    downloadJson(mockIdeas, 'test.json')
    expect(URL.createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npx vitest run src/utils/exportJson.test.ts
```
Expected: FAIL

- [ ] **Step 3: `src/utils/exportJson.ts` 구현**

```typescript
import type { IdeaDto } from '@/types'

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function downloadJson(ideas: IdeaDto[], filename = 'devbrew-top5.json'): void {
  const blob = new Blob([JSON.stringify(ideas, null, 2)], { type: 'application/json' })
  triggerDownload(blob, filename)
}
```

- [ ] **Step 4: 실패 테스트 — exportCsv**

```typescript
// src/utils/exportCsv.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { downloadCsv } from './exportCsv'
import { mockIdeas } from '@/test/fixtures'

describe('downloadCsv', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() })
    const a = { href: '', download: '', click: vi.fn(), remove: vi.fn() } as unknown as HTMLAnchorElement
    vi.spyOn(document, 'createElement').mockReturnValue(a)
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => a)
  })
  it('creates a CSV blob with correct mime type', () => {
    downloadCsv(mockIdeas)
    const [blob] = (URL.createObjectURL as ReturnType<typeof vi.fn>).mock.calls[0]
    expect((blob as Blob).type).toBe('text/csv;charset=utf-8;')
  })
})
```

- [ ] **Step 5: `src/utils/exportCsv.ts` 구현**

```typescript
import type { IdeaDto } from '@/types'

const COLS = ['id', 'title', 'sourceTrack', 'score', 'status', 'createdAt', 'sourceUrl'] as const

export function downloadCsv(ideas: IdeaDto[], filename = 'devbrew-top5.csv'): void {
  const escape = (val: string) => (val.includes(',') || val.includes('"') ? `"${val.replace(/"/g, '""')}"` : val)
  const rows = [
    COLS.join(','),
    ...ideas.map((idea) => COLS.map((col) => escape(String(idea[col] ?? ''))).join(',')),
  ]
  const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
```

- [ ] **Step 6: `src/components/ExportButton.tsx` 구현**

```typescript
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { getTopIdeas } from '@/api/ideas'
import { downloadJson } from '@/utils/exportJson'
import { downloadCsv } from '@/utils/exportCsv'

type Format = 'JSON' | 'CSV'

export function ExportButton() {
  const [loading, setLoading] = useState(false)
  const [format, setFormat] = useState<Format>('JSON')

  async function handleExport() {
    setLoading(true)
    try {
      const ideas = await getTopIdeas(5)
      format === 'JSON' ? downloadJson(ideas) : downloadCsv(ideas)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex items-center gap-2">
      <select value={format} onChange={(e) => setFormat(e.target.value as Format)}
        className="text-sm border rounded-lg px-2 py-1.5 text-zinc-600 bg-white">
        <option>JSON</option>
        <option>CSV</option>
      </select>
      <Button variant="outline" size="sm" onClick={handleExport} disabled={loading}>
        {loading ? '준비 중…' : 'Top 5 Export'}
      </Button>
    </div>
  )
}
```

- [ ] **Step 7: 모든 export 테스트 통과 확인**

```bash
npx vitest run src/utils/
```
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add src/utils/exportJson.ts src/utils/exportCsv.ts src/components/ExportButton.tsx
git commit -m "feat: add JSON/CSV export for top 5 ideas"
```

---

### Task 8: DashboardPage Assembly

**Files:**
- Create: `src/pages/DashboardPage.tsx`, `src/pages/DashboardPage.test.tsx`

**Interfaces:**
- Consumes: `useIdeas`, `IdeaCard`, `IdeaModal`, `SummaryCards`, `Pagination`, `ExportButton`, `Tabs` from shadcn/ui
- Produces: 완전히 동작하는 대시보드 — 필터 탭 → URL 쿼리, 카드 클릭 → 모달, 페이지네이션, Top 5 export

- [ ] **Step 1: 실패 테스트 — DashboardPage 통합**

```typescript
// src/pages/DashboardPage.test.tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect } from 'vitest'
import React from 'react'
import { DashboardPage } from './DashboardPage'

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
}

describe('DashboardPage', () => {
  it('renders idea cards after loading', async () => {
    render(<DashboardPage />, { wrapper })
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(3))
  })
  it('opens modal when card is clicked', async () => {
    render(<DashboardPage />, { wrapper })
    await waitFor(() => screen.getAllByRole('article'))
    await userEvent.click(screen.getAllByRole('article')[0])
    await waitFor(() => expect(screen.getByText('채점 이유')).toBeInTheDocument())
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npx vitest run src/pages/DashboardPage.test.tsx
```
Expected: FAIL

- [ ] **Step 3: `src/pages/DashboardPage.tsx` 구현**

```typescript
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useIdeas } from '@/hooks/useIdeas'
import { IdeaCard } from '@/components/IdeaCard'
import { IdeaModal } from '@/components/IdeaModal'
import { SummaryCards } from '@/components/SummaryCards'
import { Pagination } from '@/components/Pagination'
import { ExportButton } from '@/components/ExportButton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { IdeaStatus } from '@/types'

const TABS: { label: string; value: IdeaStatus | 'ALL' }[] = [
  { label: '전체', value: 'ALL' },
  { label: 'NOTIFIED', value: 'NOTIFIED' },
  { label: 'SCORED', value: 'SCORED' },
  { label: 'PENDING', value: 'PENDING' },
  { label: 'REJECTED', value: 'REJECTED' },
]

export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (searchParams.get('status') as IdeaStatus | null) ?? undefined
  const page = Number(searchParams.get('page') ?? 0)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const { data, isLoading, isError, refetch } = useIdeas({ status, page })

  function setStatus(value: IdeaStatus | 'ALL') {
    const p = new URLSearchParams()
    if (value !== 'ALL') p.set('status', value)
    p.set('page', '0')
    setSearchParams(p)
  }

  function setPage(p: number) {
    setSearchParams((prev) => { const n = new URLSearchParams(prev); n.set('page', String(p)); return n })
  }

  return (
    <div className="min-h-screen bg-zinc-50">
      <header className="border-b bg-white px-6 py-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-zinc-900">DevBrew</h1>
        <ExportButton />
      </header>
      <main className="max-w-5xl mx-auto px-6 py-6">
        <SummaryCards data={data} isLoading={isLoading} />
        <Tabs value={status ?? 'ALL'} onValueChange={(v) => setStatus(v as IdeaStatus | 'ALL')}>
          <TabsList className="mb-4">
            {TABS.map(({ label, value }) => (
              <TabsTrigger key={value} value={value}>{label}</TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        {isLoading && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[...Array(6)].map((_, i) => <div key={i} className="rounded-xl border bg-white p-4 h-28 animate-pulse" />)}
          </div>
        )}
        {isError && (
          <div className="text-center py-20">
            <p className="text-zinc-500 mb-3">데이터를 불러올 수 없습니다.</p>
            <button onClick={() => refetch()} className="text-sm text-blue-600 hover:underline">다시 시도</button>
          </div>
        )}
        {!isLoading && !isError && data?.content.length === 0 && (
          <p className="text-center py-20 text-zinc-400 text-sm">아직 아이디어가 없습니다.</p>
        )}
        {!isLoading && !isError && data && data.content.length > 0 && (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {data.content.map((idea) => (
                <IdeaCard key={idea.id} idea={idea} onClick={() => setSelectedId(idea.id)} />
              ))}
            </div>
            <Pagination page={data.number} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </main>
      <IdeaModal ideaId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  )
}

export default DashboardPage
```

- [ ] **Step 4: 모든 테스트 통과 확인**

```bash
npx vitest run
```
Expected: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/pages/DashboardPage.tsx
git commit -m "feat: assemble DashboardPage with filter tabs, grid, modal, pagination, export"
```

---

### Task 9: OMD Design System Integration

**Files:**
- Create: `DESIGN.md` (omd-init 생성)
- Modify: 각 컴포넌트의 Tailwind 클래스 (omd-apply 적용)

**Interfaces:**
- Produces: DESIGN.md brand token 적용 컴포넌트, omd-final-qa Impeccable audit 통과

- [ ] **Step 1: omd-init 실행**

```
/omd-init
```
인터뷰 중 전달할 컨텍스트:
- 관리자 전용 데이터 대시보드
- 클린 라이트 테마, 데이터 가독성 최우선
- Zinc/Neutral 기반 neutral palette
- 상태 색상: green(NOTIFIED), blue(SCORED), amber(PENDING), gray(REJECTED)
- 트랙 배지: purple(SAAS), near-black(GITHUB), orange(VIRAL)

- [ ] **Step 2: omd-apply 실행**

```
/omd-apply
```
DESIGN.md의 color/spacing/typography token을 각 컴포넌트 Tailwind 클래스에 적용.

- [ ] **Step 3: omd-final-qa 실행**

```
/omd-final-qa
```
BLOCK 항목이 있으면 수정 후 재실행. FYI는 선택 수정.

- [ ] **Step 4: 테스트 재실행 (회귀 확인)**

```bash
npx vitest run
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add DESIGN.md src/
git commit -m "design: apply OMD brand tokens — omd-final-qa passed"
```

---

### Task 10: CI/CD Infrastructure

**Files:**
- Create: `nginx.conf`, `Dockerfile`
- Create: `k8s/deployment.yaml`, `k8s/service.yaml`, `k8s/ingress.yaml`
- Create: `.github/workflows/ci.yml`, `.github/workflows/cd.yml`

**Interfaces:**
- Produces: `main` push → GHCR 이미지 → k8s devbrew 네임스페이스 자동 배포

- [ ] **Step 1: `nginx.conf` 작성**

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/javascript application/json;
    gzip_min_length 1024;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(js|css|png|jpg|gif|ico|svg|woff2?)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

- [ ] **Step 2: `Dockerfile` 작성**

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

- [ ] **Step 3: 로컬 Docker 빌드 확인**

```bash
docker build -t devbrew-fe:local .
docker run --rm -p 8081:80 devbrew-fe:local
```
Expected: `http://localhost:8081` 에서 대시보드 렌더링 확인 후 컨테이너 종료

- [ ] **Step 4: `k8s/deployment.yaml` 작성**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: devbrew-fe
  namespace: devbrew
spec:
  replicas: 1
  selector:
    matchLabels:
      app: devbrew-fe
  template:
    metadata:
      labels:
        app: devbrew-fe
    spec:
      containers:
        - name: devbrew-fe
          image: ghcr.io/yoon6yo/devbrew-fe:latest
          ports:
            - containerPort: 80
          resources:
            requests:
              memory: "32Mi"
              cpu: "10m"
            limits:
              memory: "64Mi"
              cpu: "100m"
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 30
```

- [ ] **Step 5: `k8s/service.yaml` 작성**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: devbrew-fe
  namespace: devbrew
spec:
  selector:
    app: devbrew-fe
  ports:
    - port: 80
      targetPort: 80
  type: ClusterIP
```

- [ ] **Step 6: `k8s/ingress.yaml` 작성**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: devbrew-fe
  namespace: devbrew
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
    - host: devbrew-fe.YOURDOMAIN.com   # ← 실제 도메인으로 교체
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: devbrew-fe
                port:
                  number: 80
```

- [ ] **Step 7: `.github/workflows/ci.yml` 작성**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
      - run: npm ci
      - run: npm test -- --run

  build-push:
    needs: test
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ghcr.io/yoon6yo/devbrew-fe:latest
            ghcr.io/yoon6yo/devbrew-fe:sha-${{ github.sha }}
```

- [ ] **Step 8: `.github/workflows/cd.yml` 작성**

```yaml
name: CD

on:
  workflow_run:
    workflows: [CI]
    types: [completed]
    branches: [main]
  workflow_dispatch:

jobs:
  deploy:
    if: ${{ github.event.workflow_run.conclusion == 'success' || github.event_name == 'workflow_dispatch' }}
    runs-on: ubuntu-latest
    steps:
      - uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.K8S_SSH_HOST }}
          port: ${{ secrets.K8S_SSH_PORT }}
          username: ${{ secrets.K8S_SSH_USER }}
          key: ${{ secrets.K8S_SSH_KEY }}
          script: |
            kubectl set image deployment/devbrew-fe \
              devbrew-fe=ghcr.io/yoon6yo/devbrew-fe:latest \
              -n devbrew
            kubectl rollout status deployment/devbrew-fe \
              -n devbrew --timeout=120s
```

- [ ] **Step 9: GitHub 레포 생성 + Secrets 설정 (수동 작업)**

GitHub에서 `devbrew-fe` 공개/비공개 레포 생성 후:
```
Settings → Secrets and variables → Actions → New repository secret:
- K8S_SSH_HOST  — k3s 서버 IP
- K8S_SSH_PORT  — SSH 포트 (보통 22)
- K8S_SSH_USER  — SSH 유저
- K8S_SSH_KEY   — SSH 프라이빗 키 (-----BEGIN OPENSSH PRIVATE KEY----- 포함)
```
`GITHUB_TOKEN`은 자동 주입되므로 별도 설정 불필요.

- [ ] **Step 10: 원격 레포 연결 및 첫 push**

```bash
git remote add origin https://github.com/yoon6yo/devbrew-fe.git
git push -u origin main
```
Expected: CI workflow 트리거 → test → build-push → CD 자동 실행

- [ ] **Step 11: 커밋**

```bash
git add nginx.conf Dockerfile k8s/ .github/
git commit -m "feat: add Dockerfile, nginx, k8s manifests, GitHub Actions CI/CD"
```

---

### Task 11: BE — rejectIdea 엔드포인트 인증 제거

**Files:**
- Modify: `/home/yoon6yo/project/DevBrew/src/main/kotlin/com/devbrew/config/SecurityConfig.kt`

**Interfaces:**
- Produces: `POST /api/ideas/*/reject` 인증 없이 호출 가능

현재 상태: `GET /api/ideas`, `GET /api/ideas/**`, `POST /api/ideas/*/star`, `DELETE /api/ideas/*/star`는 이미 `permitAll()`. `POST /api/ideas/*/reject`만 `.anyRequest().authenticated()` 에 걸림.

- [ ] **Step 1: SecurityConfig.kt에 reject 경로 추가**

`/home/yoon6yo/project/DevBrew/src/main/kotlin/com/devbrew/config/SecurityConfig.kt` 의 `authorizeHttpRequests` 블록에 한 줄 추가:

```kotlin
.requestMatchers(HttpMethod.DELETE, "/api/ideas/*/star").permitAll()
// 아래 줄 추가
.requestMatchers(HttpMethod.POST, "/api/ideas/*/reject").permitAll()
.anyRequest().authenticated()
```

- [ ] **Step 2: BE 테스트 실행**

```bash
cd /home/yoon6yo/project/DevBrew
./gradlew test
```
Expected: PASS

- [ ] **Step 3: 로컬에서 인증 없이 reject 호출 확인**

```bash
./gradlew bootRun &
# 잠시 대기 후
curl -X POST http://localhost:8080/api/ideas/1/reject
# Expected: 200 OK with updated IdeaDto (status: REJECTED) — 401 아님
```

- [ ] **Step 4: 커밋**

```bash
cd /home/yoon6yo/project/DevBrew
git add src/main/kotlin/com/devbrew/config/SecurityConfig.kt
git commit -m "feat: permit POST /api/ideas/*/reject without authentication"
```

---

## Self-Review

**Spec coverage:**
- [x] 인증 없이 즉시 접근 — Task 11 (BE reject permitAll) + FE 전체 인증 코드 없음
- [x] 상태별 요약 카드 — SummaryCards (Task 6)
- [x] 필터 탭 + URL 쿼리 파라미터 반영 — DashboardPage (Task 8)
- [x] 아이디어 카드 그리드 + 점수 내림차순 — IdeaCard + getIdeas `sort=score,desc` (Tasks 5, 6, 3)
- [x] 페이지네이션 20개씩 — Pagination + useIdeas (Tasks 4, 6)
- [x] 상세 모달 (제목·설명·점수·이유·링크·날짜) — IdeaModal (Task 6)
- [x] 거절 버튼 + 낙관적 업데이트 — useRejectIdea (Task 4)
- [x] 빈/로딩/에러 상태 — DashboardPage, IdeaModal (Tasks 6, 8)
- [x] Top 5 JSON/CSV export — ExportButton + utils (Task 7)
- [x] GHCR + k8s CI/CD — Task 10
- [x] OMD + omd-final-qa — Task 9
- [x] k8s namespace devbrew — Task 10 k8s manifests

**Placeholder scan:**
- `k8s/ingress.yaml`의 `YOURDOMAIN.com` — 의도적 placeholder, Step 6 주석에 명시됨 ✓
- Task 9 `omd-init` 인터뷰는 실행 시점에 진행 — 설명 충분 ✓

**Type consistency:**
- `IdeaStatus`, `SourceTrack`, `IdeaDto`, `PageResponse<T>` — Task 2에서 정의, 모든 Task에서 `@/types` import로 일관 ✓
- `useIdeas({ status?, page? })` — Task 4 정의, Task 8에서 동일 시그니처 사용 ✓
- `useRejectIdea().mutate(id: number)` — Task 4 정의, Task 6 IdeaModal에서 동일 사용 ✓
- `getTopIdeas(n = 5): Promise<IdeaDto[]>` — Task 3 정의, Task 7에서 동일 사용 ✓
