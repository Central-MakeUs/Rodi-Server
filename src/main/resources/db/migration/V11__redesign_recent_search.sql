-- 최근 검색어 재설계(스펙 008): 검색 시 자동저장 → 연관검색(스펙 009)에서 선택한 지역/장소를 등록.
-- 항목에 type(REGION/PLACE)·place_id(PLACE만) 추가. 기존 자동저장 데이터는 의미가 달라져 비운다.
TRUNCATE TABLE member_recent_search RESTART IDENTITY;

ALTER TABLE member_recent_search
    ADD COLUMN type     VARCHAR(20) NOT NULL,       -- REGION | PLACE
    ADD COLUMN place_id BIGINT,                     -- PLACE만(상세 직행용). 스냅샷이라 place FK 없음
    ALTER COLUMN keyword TYPE VARCHAR(100);         -- 장소명 대비 50 → 100

-- 단일 유니크 제거 → 종류별 부분 유니크(지역=이름, 장소=placeId)
ALTER TABLE member_recent_search DROP CONSTRAINT uq_recent_search_member_keyword;
CREATE UNIQUE INDEX uq_recent_search_region
    ON member_recent_search (member_id, keyword)  WHERE type = 'REGION';
CREATE UNIQUE INDEX uq_recent_search_place
    ON member_recent_search (member_id, place_id) WHERE type = 'PLACE';
