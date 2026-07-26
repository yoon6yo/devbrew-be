# DevBrew FE — 설계 스펙

**날짜**: 2026-07-26  
**대상 레포**: `devbrew-fe` (신규 생성)  
**목적**: DevBrew 파이프라인이 생성한 스타트업 아이디어를 조회·관리하는 관리자 전용 대시보드 (인증 없음, UX 최우선)

---

## 1. 아키텍처

### 스택

| 역할 | 라이브러리 |
|------|-----------|
| 빌드 | React 19 + TypeScript + Vite |
| 서버 상태 | TanStack Query v5 |
| 라우팅 | React Router v7 |
| UI | Tailwind CSS + shadcn/ui |
| HTTP | native fetch (thin wrapper) |
| 디자인 시스템 | OMD (DESIGN.md → omd-harness → omd-apply) |

### 디렉터리 구조

```
devbrew-fe/
├── src/
│   ├── api/          — fetch wrapper + API 함수 (getIdeas, getIdea, rejectIdea, exportTopIdeas)
│   ├── components/   — StatusBadge, TrackBadge, ScoreBar, IdeaCard, IdeaModal, Pagination
│   ├── pages/        — DashboardPage, IdeaDetailPage (또는 모달)
│   ├── hooks/        — useIdeas, useIdeaDetail, useRejectIdea
│   ├── types/        — IdeaDto, IdeaStatus, SourceTrack, PageResponse
│   ├── utils/        — dateFormat, scoreColor, exportJson, exportCsv
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
├── nginx.conf
├── Dockerfile
└── .github/workflows/
    ├── ci.yml
    └── cd.yml
```

---

## 2. 화면 구성

### 2-1. 메인 대시보드 (`/`)

**상단 요약 카드 (4개)**
- NOTIFIED (초록) / SCORED (파랑) / PENDING (노랑) / REJECTED (회색) — 각 상태 건수
- 총 아이디어 수

**필터 탭**
- 전체 / NOTIFIED / SCORED / PENDING / REJECTED
- 선택된 탭에 따라 `?status=` 쿼리 파라미터 반영

**아이디어 카드 그리드**
- 컬럼: 제목, 출처 트랙 배지(SAAS/GITHUB/VIRAL), 점수 바, 상태 배지, 생성일
- 기본 정렬: 점수 내림차순 (`sort=score,desc`)
- 페이지네이션: 20개씩, 총 페이지 표시
- 각 카드 클릭 → 상세 모달 오픈

**상위 5개 Export 버튼**
- 대시보드 우상단 고정 버튼 "Top 5 Export"
- `GET /api/ideas?sort=score,desc&page=0&size=5` 호출 → JSON 또는 CSV 파일로 즉시 다운로드
- 포맷 선택: JSON (기본) / CSV (토글 또는 드롭다운)

**빈 상태 / 로딩 / 에러 처리**
- 로딩: 스켈레톤 카드
- 빈 결과: "아직 아이디어가 없습니다" 일러스트 + 메시지
- 에러: 재시도 버튼

### 2-2. 아이디어 상세 모달

- 제목 (헤딩)
- 상태 배지 + 출처 트랙 배지
- 점수 (숫자 + 10점 만점 시각화)
- 채점 이유 (LLM 텍스트, 가독성 강조)
- 설명 전문 (스크롤)
- 출처 링크 (sourceUrl, 새 탭으로)
- 생성일
- "거절" 버튼 → `POST /api/ideas/{id}/reject` → 낙관적 업데이트 후 목록 갱신
- ESC / 오버레이 클릭으로 닫기

---

## 3. 데이터 플로우

### API 레이어

```typescript
// src/api/client.ts — fetch wrapper (공통 에러 처리)
async function apiFetch<T>(path: string, init?: RequestInit): Promise<T>

// src/api/ideas.ts
GET  /api/ideas?status=&page=&size=20&sort=score,desc  → PageResponse<IdeaDto>
GET  /api/ideas?sort=score,desc&page=0&size=5           → PageResponse<IdeaDto>  // export용
GET  /api/ideas/:id                                     → IdeaDto
POST /api/ideas/:id/reject                              → IdeaDto
```

**인증 없음** — BE의 `/api/**` 인증이 제거되어 Authorization 헤더 불필요.

### TanStack Query 패턴

```typescript
// useIdeas: 필터/페이지 파라미터를 queryKey에 포함
queryKey: ['ideas', { status, page }]
staleTime: 30_000  // 30초 캐시

// useRejectIdea: 낙관적 업데이트
onMutate → 캐시 내 해당 아이디어 status → REJECTED
onError  → 롤백
onSettled → invalidateQueries(['ideas'])
```

---

## 4. OMD 디자인 시스템

### 적용 순서

1. `omd-init` — DESIGN.md 생성 (admin dashboard 테마: 클린 라이트, 데이터 중심)
2. `omd-harness` — 화면 단위 컴포넌트 빌드
3. `omd-apply` — 컴포넌트별 brand token 적용
4. `omd-final-qa` — Impeccable-style audit gate (deterministic pass/fail)

### 주요 토큰 방향

- **Status 색상**: NOTIFIED→green-500, SCORED→blue-500, PENDING→amber-400, REJECTED→gray-400
- **Track 배지**: SAAS→보라, GITHUB→검정, VIRAL→주황
- **점수**: 0-5 회색, 6-7 파랑, 8-10 초록
- **타이포**: 제목 heavy, 채점 이유 읽기 편한 본문 크기

---

## 5. CI/CD

### BE 패턴 복제 (radius-be)

**CI** (`.github/workflows/ci.yml`):
```
trigger: push to main
jobs:
  build-push:
    - npm ci
    - npm run build          # vite build → dist/
    - docker build (nginx:alpine, COPY dist/ /usr/share/nginx/html/)
    - docker push GHCR (태그: sha-<short>, latest)
```

**CD** (`.github/workflows/cd.yml`):
```
trigger: workflow_run (CI 성공) + workflow_dispatch
jobs:
  deploy:
    - SSH → kubectl set image deployment/devbrew-fe devbrew-fe=$IMAGE -n devbrew
    - kubectl rollout status --timeout=120s -n devbrew
```

**필요한 GitHub Secrets**:
`K8S_SSH_HOST`, `K8S_SSH_PORT`, `K8S_SSH_USER`, `K8S_SSH_KEY`, `GHCR_TOKEN`

### nginx.conf 핵심

```nginx
location / {
    try_files $uri $uri/ /index.html;  # SPA fallback
}
```

### k8s 리소스 (`k8s/`)

| 파일 | 내용 |
|------|------|
| `deployment.yaml` | devbrew 네임스페이스, nginx image, liveness probe |
| `service.yaml` | ClusterIP, port 80 |
| `ingress.yaml` | 호스트 기반 라우팅 (도메인 미정 시 placeholder) |

---

## 6. BE 변경 사항

FE 로그인 제거에 따라 BE에서 `/api/**` 인증 설정을 제거하거나 permitAll()로 변경 필요.  
**보안 보완**: k8s Ingress에서 허용 IP 대역 제한 또는 클러스터 내부 접근만 허용.

---

## 7. 성공 기준

- [ ] 로그인 없이 `/`에서 아이디어 목록 즉시 조회
- [ ] 상태 필터 탭 전환 시 URL 쿼리 파라미터 반영
- [ ] 카드 클릭 → 모달에서 전체 정보 확인
- [ ] 거절 버튼 클릭 → 낙관적 업데이트, 목록 즉시 반영
- [ ] 빈/로딩/에러 상태 모두 처리
- [ ] "Top 5 Export" 버튼 → JSON/CSV 파일 다운로드
- [ ] main push → CI → CD 자동 배포
- [ ] omd-final-qa Impeccable audit 통과
