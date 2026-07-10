package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 닉네임 부여. 후보 풀은 리소스 파일(한 줄에 하나)을 기동 시 메모리에 로드해 불변으로 보관하고, 가입 시 미사용 후보에서 무작위로 하나 고른다. 유일성의 최종 방어선은
 * {@code member.nickname} UNIQUE 제약이다(별도 후보 테이블 없음).
 */
@Component
public class NicknameAssigner {

    private final MemberRepository memberRepository;
    private final List<String> pool;

    public NicknameAssigner(
            MemberRepository memberRepository,
            @Value("${nickname.pool-path:nickname/nicknames.txt}") String poolPath) {
        this.memberRepository = memberRepository;
        this.pool = loadPool(poolPath);
    }

    /** 미사용 후보에서 무작위 1개를 고른다. 소진(>후보 수) 시 예외(다음 업데이트에서 접미사 등 처리). */
    public String assign() {
        Set<String> used = new HashSet<>(memberRepository.findUsedNicknames());
        List<String> available =
                pool.stream().filter(nickname -> !used.contains(nickname)).toList();
        if (available.isEmpty()) {
            throw new BusinessException(MemberErrorCode.NICKNAME_POOL_EXHAUSTED);
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private List<String> loadPool(String path) {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource(path).getInputStream(),
                                StandardCharsets.UTF_8))) {
            List<String> names =
                    reader.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
            if (names.isEmpty()) {
                throw new IllegalStateException("닉네임 후보 풀이 비어 있습니다: " + path);
            }
            return names;
        } catch (IOException e) {
            throw new IllegalStateException("닉네임 후보 풀 로드 실패: " + path, e);
        }
    }
}
