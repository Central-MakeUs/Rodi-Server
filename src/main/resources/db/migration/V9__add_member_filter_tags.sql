-- 홈 정렬 필터(스펙 007): 회원이 선택한 연습유형 집합을 저장한다.
-- 카테고리(기초 주행 등)는 클라 전용 UX 개념이라 서버는 풀린 PracticeType 리스트만 보관한다.
-- NULL = 필터 미설정(온보딩 전/미전송) → 정렬 시 필터 없음(거리순). backfill 없음(클라가 다음 홈 진입 시 PUT).
ALTER TABLE member ADD COLUMN filter_tags jsonb;
