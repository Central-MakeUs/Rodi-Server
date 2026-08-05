package cmc.rodi.domain.place.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 연관 검색어(스펙 009)의 지역 자동완성 인덱스. 정규 지역 목록(regions.csv)과 매칭용 별칭(aliases.csv)을 기동 시 메모리에 로드해 불변으로
 * 보관한다(닉네임 풀과 동일 패턴). 응답 표기(display_name)는 {@code place.address}와 일치하도록 정규화돼 있어, 지역 선택 후 그 문자열로 주소
 * 검색(스펙 007)이 그대로 연결된다.
 */
@Component
public class RegionSearchIndex {

    private static final int MAX_RESULTS = 4;

    private final Map<Long, String> displayNameByCode;
    private final List<Alias> aliases;

    public RegionSearchIndex(
            @Value("${region.regions-path:region/regions.csv}") String regionsPath,
            @Value("${region.aliases-path:region/aliases.csv}") String aliasesPath) {
        this.displayNameByCode = loadRegions(regionsPath);
        this.aliases = loadAliases(aliasesPath, displayNameByCode.keySet());
    }

    /**
     * 키워드를 포함하는 지역을 관련도순(접두 일치 우선, 그다음 부분 일치, priority)으로 최대 4개 반환. 여러 별칭이 같은 지역을 가리키면 region_code로
     * 중복 제거한다.
     */
    public List<String> search(String keyword) {
        String normalized = normalize(keyword);
        if (normalized.isEmpty()) {
            return List.of();
        }

        Map<Long, Alias> bestByCode = new LinkedHashMap<>();
        for (Alias alias : aliases) {
            int matchPos = alias.normalizedAlias().indexOf(normalized);
            if (matchPos < 0) {
                continue;
            }
            Alias candidate = alias.withMatchPos(matchPos);
            bestByCode.merge(alias.regionCode(), candidate, RegionSearchIndex::better);
        }

        return bestByCode.values().stream()
                .sorted(
                        Comparator.comparingInt(Alias::matchPos)
                                .thenComparingInt(a -> a.normalizedAlias().length())
                                .thenComparingInt(Alias::priority))
                .limit(MAX_RESULTS)
                .map(a -> displayNameByCode.get(a.regionCode()))
                .toList();
    }

    /**
     * 두 후보 중 더 관련도 높은 쪽. 접두 일치 우선 → 별칭이 짧을수록 우선("강남"이 "강원강릉"보다 검색어에 가까움, 시도 결합형 별칭이 밀리게 함) →
     * priority 낮은 값 우선(동률 tie-break).
     */
    private static Alias better(Alias a, Alias b) {
        int byPos = Integer.compare(a.matchPos(), b.matchPos());
        if (byPos != 0) {
            return byPos < 0 ? a : b;
        }
        int byLength = Integer.compare(a.normalizedAlias().length(), b.normalizedAlias().length());
        if (byLength != 0) {
            return byLength < 0 ? a : b;
        }
        return a.priority() <= b.priority() ? a : b;
    }

    /** 검색 정규화: 공백 제거·소문자화(한글은 영향 없음, 영문 지역명 대비). */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.replace(" ", "").toLowerCase();
    }

    private Map<Long, String> loadRegions(String path) {
        Map<Long, String> map = new LinkedHashMap<>();
        try (BufferedReader reader = openResource(path)) {
            String header = reader.readLine(); // region_code,display_name
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", 2);
                map.put(Long.parseLong(cols[0]), cols[1]);
            }
        } catch (IOException e) {
            throw new IllegalStateException("지역 목록 로드 실패: " + path, e);
        }
        if (map.isEmpty()) {
            throw new IllegalStateException("지역 목록이 비어 있습니다: " + path);
        }
        return map;
    }

    /**
     * 별칭을 로드하되, canonical 목록({@code validCodes})에 없는 region_code(시도 등 상위 레벨)는 제외한다. 이렇게 걸러야
     * search()에서 "정렬 후 limit → null 필터"로 인해 최종 개수가 상한보다 줄어드는 문제를 피한다.
     */
    private List<Alias> loadAliases(String path, Set<Long> validCodes) {
        List<Alias> list = new ArrayList<>();
        try (BufferedReader reader = openResource(path)) {
            String header = reader.readLine(); // region_code,alias,normalized_alias,priority
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                // normalized_alias는 한글/숫자만이라 콤마를 포함하지 않음 — 단순 split으로 충분
                String[] cols = line.split(",");
                long regionCode = Long.parseLong(cols[0]);
                if (!validCodes.contains(regionCode)) {
                    continue;
                }
                String normalizedAlias = normalize(cols[2]);
                int priority = Integer.parseInt(cols[3]);
                list.add(new Alias(regionCode, normalizedAlias, priority, Integer.MAX_VALUE));
            }
        } catch (IOException e) {
            throw new IllegalStateException("지역 별칭 로드 실패: " + path, e);
        }
        if (list.isEmpty()) {
            throw new IllegalStateException("지역 별칭이 비어 있습니다: " + path);
        }
        return list;
    }

    private BufferedReader openResource(String path) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8));
    }

    /** 별칭 1건. matchPos는 검색 시점에 계산되는 값이라 로드 시엔 MAX_VALUE(미매칭)로 둔다. */
    private record Alias(long regionCode, String normalizedAlias, int priority, int matchPos) {
        Alias withMatchPos(int pos) {
            return new Alias(regionCode, normalizedAlias, priority, pos);
        }
    }
}
