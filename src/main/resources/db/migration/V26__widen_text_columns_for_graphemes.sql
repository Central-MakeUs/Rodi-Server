-- 텍스트 길이 제한 기준을 grapheme cluster로 바꾼다(스펙 018).
-- 사용자가 한 글자로 세는 이모지가 코드포인트로는 여러 개라(👨‍👩‍👧‍👦 = 7) VARCHAR(N)으로는
-- 적법한 입력을 DB가 거절한다. grapheme 하나의 코드포인트 수에는 상한이 없어 안전한 N 자체가
-- 존재하지 않으므로 TEXT로 열고, 길이는 애플리케이션의 @GraphemeSize가 책임진다
-- (저장 크기는 같은 애노테이션의 코드유닛 상한으로 막는다).
-- Postgres에서 TEXT와 VARCHAR(N)은 저장 방식·성능이 같고, 둘 다 확장 방향이라 데이터 손실이 없다.
ALTER TABLE member ALTER COLUMN driving_goal TYPE TEXT;
ALTER TABLE review ALTER COLUMN content TYPE TEXT;
