package cmc.rodi.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 연관 검색어(스펙 009) 지역 인덱스 — 실제 번들 CSV(regions.csv·aliases.csv)로 매칭·정렬·상한 4개·dedup을 검증. */
class RegionSearchIndexTest {

    private final RegionSearchIndex index =
            new RegionSearchIndex("region/regions.csv", "region/aliases.csv");

    @Test
    @DisplayName("'강' 검색: 정확히 4개, 짧고 정확한 로컬 별칭(강남·강동·강릉·강북)이 시도 결합형보다 우선")
    void 강_검색() {
        List<String> result = index.search("강");

        // matchPos=0(접두) 동률에서 별칭 길이가 짧은 순 → 2자 로컬 별칭들이 4자+ 시도결합형(강원 X)보다 우선.
        // 삽입 순서(가나다순 alias 파일)상 강남·강동·강릉·강북이 강서·강화보다 앞선다.
        assertThat(result).containsExactly("서울특별시 강남구", "서울특별시 강동구", "강원특별자치도 강릉시", "서울특별시 강북구");
    }

    @Test
    @DisplayName("짧은 별칭('춘천')으로 정규 지역명('강원특별자치도 춘천시') 매칭")
    void 짧은_별칭_매칭() {
        assertThat(index.search("춘천")).contains("강원특별자치도 춘천시");
    }

    @Test
    @DisplayName("공백 포함 키워드('강원도 춘천')도 정규화 후 매칭")
    void 공백_정규화_매칭() {
        assertThat(index.search("강원도 춘천")).contains("강원특별자치도 춘천시");
    }

    @Test
    @DisplayName("동일 지역을 가리키는 여러 별칭은 dedup — 결과에 중복 지역명 없음")
    void 중복_지역_dedup() {
        List<String> result = index.search("강남");
        assertThat(result).doesNotHaveDuplicates();
        assertThat(result).contains("서울특별시 강남구");
    }

    @Test
    @DisplayName("여러 지역이 같은 별칭을 공유(강서구: 서울·부산)해도 각각 후보로 나온다")
    void 동명_다지역_후보() {
        List<String> result = index.search("강서구");
        assertThat(result).contains("서울특별시 강서구", "부산광역시 강서구");
    }

    @Test
    @DisplayName("장소가 없어도 지역만 있으면 노출(광주·전남 재정규화 검증)")
    void 광주_전남_재정규화() {
        assertThat(index.search("광주 동구")).contains("광주광역시 동구");
        assertThat(index.search("전남 영광")).contains("전라남도 영광군");
    }

    @Test
    @DisplayName("인천 중구 수동 복원 검증")
    void 인천_중구_복원() {
        assertThat(index.search("인천 중구")).contains("인천광역시 중구");
    }

    @Test
    @DisplayName("매칭 결과 없으면 빈 목록")
    void 매칭_없음() {
        assertThat(index.search("존재하지않는지역명123")).isEmpty();
    }

    @Test
    @DisplayName("빈 키워드는 빈 목록")
    void 빈_키워드() {
        assertThat(index.search("")).isEmpty();
        assertThat(index.search("   ")).isEmpty();
    }
}
