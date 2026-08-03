-- 연관 검색어(스펙 009) 지역 데이터 정합성: place.address의 "세종특별자치시"를
-- 시군구 표준 표기 "세종특별자치시 세종시"로 정규화한다(세종은 산하에 세종시 하나뿐).
UPDATE place SET address = '세종특별자치시 세종시' WHERE address = '세종특별자치시';
