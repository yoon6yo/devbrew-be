# DevBrew FE 구현 킥오프 프롬프트

아래 내용을 FE 레포를 시작할 때 Claude에게 전달하세요.

---

## 프로젝트 개요

**DevBrew**는 스타트업 아이디어를 자동으로 수집·생성·채점하여 Slack으로 알림을 보내는 백엔드 파이프라인입니다.  
이 FE는 **관리자 전용 대시보드**로, 파이프라인이 생성한 아이디어를 조회·관리하는 인터페이스를 제공합니다.

---

## 백엔드 API 스펙

**Base URL**: `https://<서버 도메인>/`  
**인증 방식**: JWT Bearer Token — 모든 `/api/**` 요청에 `Authorization: Bearer <token>` 헤더 필요  
**Swagger UI**: `GET /swagger-ui/index.html` (개발 시 로컬 확인용)

### 인증

| Method | Endpoint | Body | Response |
|--------|----------|------|----------|
| POST | `/api/auth/login` | `{"username":"admin","password":"..."}` | `{"token":"<jwt>"}` |

- 실패 시: `401 Unauthorized`

### 아이디어

| Method | Endpoint | Query Params | Response |
|--------|----------|-------------|----------|
| GET | `/api/ideas` | `status`, `page`, `size`, `sort` | `Page<IdeaDto>` |
| GET | `/api/ideas/{id}` | — | `IdeaDto` |
| POST | `/api/ideas/{id}/reject` | — | `IdeaDto` |

**IdeaDto 스키마:**
```json
{
  "id": 1,
  "title": "아이디어 제목",
  "description": "상세 설명",
  "sourceTrack": "SAAS | GITHUB | VIRAL",
  "sourceUrl": "https://...",
  "score": 8,
  "scoreReason": "LLM이 평가한 이유",
  "status": "PENDING | SCORED | NOTIFIED | REJECTED",
  "createdAt": "2026-07-26T09:00:00+09:00"
}
```

**Page 응답 구조:**
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

**주요 쿼리 패턴:**
- 알림된 아이디어 점수순: `GET /api/ideas?status=NOTIFIED&page=0&size=20&sort=score,desc`
- 전체 아이디어: `GET /api/ideas?page=0&size=20`

### 헬스체크

| Method | Endpoint | 인증 필요 |
|--------|----------|---------|
| GET | `/actuator/health` | 불필요 |

---

## 구현해야 할 화면

### 1. 로그인 페이지 (`/login`)
- username / password 입력 폼
- 로그인 성공 시 JWT를 localStorage 또는 httpOnly 쿠키에 저장 후 대시보드로 이동
- 실패 시 에러 메시지 표시

### 2. 대시보드 (`/`)
- 상단: 상태별 카운트 요약 (NOTIFIED / SCORED / PENDING / REJECTED)
- 아이디어 목록 테이블:
  - 컬럼: 제목, 출처 트랙, 점수, 상태, 생성일
  - 페이지네이션 (20개씩)
  - 상태 필터 탭 (전체 / NOTIFIED / SCORED / PENDING / REJECTED)
  - 점수 내림차순 정렬 기본
- 각 행 클릭 시 상세 모달 또는 사이드패널

### 3. 아이디어 상세 (`/ideas/:id` 또는 모달)
- 제목, 설명 전문
- 점수 + 채점 이유
- 출처 링크 (sourceUrl)
- 출처 트랙 배지 (SAAS / GITHUB / VIRAL)
- 상태 표시
- "거절" 버튼 → `POST /api/ideas/{id}/reject` → 상태 업데이트

---

## 기술 스택 권장

```
React 19 + TypeScript
Vite (빌드 도구)
TanStack Query v5 (서버 상태 관리)
React Router v7 (라우팅)
Tailwind CSS + shadcn/ui (UI 컴포넌트)
Axios (HTTP 클라이언트, 인터셉터로 JWT 자동 주입)
```

---

## 인증 구현 가이드

```typescript
// axios 인터셉터 예시
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 401 인터셉터 — 자동 로그아웃
axios.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);
```

---

## 보안 주의사항

1. **JWT 저장**: localStorage는 XSS에 취약. 가능하면 httpOnly 쿠키 사용 (BFF 패턴) 또는 최소한 메모리 저장 + refresh token 고려
2. **CORS**: 백엔드에서 허용된 origin만 허용됨. 개발 시 Vite proxy 설정 사용
3. **민감 데이터 노출 금지**: rawSignal 필드는 API 응답에 포함되지 않음 (백엔드 DTO에서 제외)
4. **인증 없는 페이지**: `/login`, `/actuator/health` 외 모든 경로는 인증 필요 — ProtectedRoute 컴포넌트로 래핑

---

## Vite 개발 프록시 설정

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    }
  }
})
```

---

## 상태(IdeaStatus) 색상 가이드

| Status | 색상 제안 |
|--------|---------|
| NOTIFIED | 초록 (green) — 알림 완료 |
| SCORED | 파랑 (blue) — 채점 완료 |
| PENDING | 노랑 (yellow) — 대기 중 |
| REJECTED | 빨강/회색 (red/gray) — 거절됨 |

---

## 출처 트랙(SourceTrack) 아이콘 제안

| SourceTrack | 아이콘/설명 |
|-------------|---------|
| SAAS | 💼 Reddit 기반 SaaS 아이디어 |
| GITHUB | 🐙 GitHub 트렌드 기반 |
| VIRAL | 🔥 바이럴 시드 기반 |

---

## 디렉터리 구조 제안

```
src/
  api/          # axios 인스턴스, API 함수
  components/   # 공통 컴포넌트 (IdeaBadge, StatusBadge, Pagination 등)
  pages/        # 라우트 단위 페이지 (LoginPage, DashboardPage, IdeaDetailPage)
  hooks/        # TanStack Query 훅 (useIdeas, useIdeaDetail 등)
  types/        # IdeaDto, IdeaStatus, SourceTrack 등 타입 정의
  utils/        # 날짜 포맷, 점수 포맷 등
```
