package cmc.rodi.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberProfileService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 닉네임 변경 경합(스펙 020) — 중복 확인과 저장 사이에 다른 회원이 같은 닉네임을 먼저 저장한 상황.
 *
 * <p>실제 동시 요청 대신 중복 조회가 "없음"을 돌려주게 만들어, 저장 시 유니크 인덱스가 막는 경로를 재현한다. 서비스 트랜잭션이 실제로 커밋·롤백되도록 테스트 트랜잭션은
 * 두지 않고 직접 정리한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NicknameChangeRaceIntegrationTest {

    @Autowired MemberProfileService memberProfileService;
    @MockitoSpyBean MemberRepository memberRepository;

    @AfterEach
    void 정리() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("중복 확인을 통과해도 저장 시 유니크 인덱스에 걸리면 500이 아니라 409(MEMBER_409_3)")
    void 유니크_위반은_409() {
        Member other = Member.createBySocial("race-other@kakao.com");
        other.assignNickname("Drive_Kim");
        memberRepository.save(other);
        Member me = Member.createBySocial("race-me@kakao.com");
        me.assignNickname("차근차근_토끼");
        Long meId = memberRepository.save(me).getId();
        doReturn(false).when(memberRepository).existsByNicknameIgnoreCaseExcluding(any(), any());

        assertThatThrownBy(
                        () ->
                                memberProfileService.update(
                                        meId, MemberUpdateRequest.ofNickname("drive_kim")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.NICKNAME_DUPLICATED);

        assertThat(memberRepository.findById(meId).orElseThrow().getNickname())
                .isEqualTo("차근차근_토끼");
    }
}
