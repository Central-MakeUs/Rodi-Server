-- 홈 정렬 기준 컬럼 이름 변경(스펙 019, ADR 0016).
-- 저장된 연습유형은 이제 매칭 우선 "정렬" 기준으로만 쓰이고, 필터는 요청 파라미터(practiceTypes)로 받는다.
-- 이름만 바꾸며 데이터·타입(jsonb List<PracticeType>)은 그대로다.
ALTER TABLE member RENAME COLUMN filter_tags TO sort_tags;
