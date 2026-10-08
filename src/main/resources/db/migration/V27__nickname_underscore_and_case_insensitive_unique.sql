-- 닉네임 형식 변경과 대소문자 무시 유일성(스펙 020, ADR 0014).
--
-- 1) 단어 구분을 공백에서 _로 바꾼다. 후보 풀 닉네임("차근차근 토끼")은 모두 단어 사이 공백 1칸
--    형식이라 변환 결과가 새 형식 규칙을 통과하고, 지금까지 _가 든 닉네임은 없었으므로 충돌도 없다.
--    익명화 회원(nickname IS NULL)은 대상이 아니다.
UPDATE member SET nickname = replace(nickname, ' ', '_') WHERE nickname LIKE '% %';

-- 2) Kim과 kim을 같은 닉네임으로 본다(대소문자만 바꾼 사칭 방지). 기존 제약은 대소문자를 구분하므로
--    lower(nickname) 유니크 인덱스로 교체한다. 기존 닉네임은 모두 한글이라 인덱스 생성 시 충돌이 없다.
--    NULL은 여러 개여도 된다(기존과 같다).
ALTER TABLE member DROP CONSTRAINT uq_member_nickname;
CREATE UNIQUE INDEX uq_member_nickname_lower ON member (lower(nickname));
