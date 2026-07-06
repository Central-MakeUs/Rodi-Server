package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.service.RefreshTokenService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks MemberWithdrawalService service;

    @Test
    @DisplayName("탈퇴 요청: soft delete + 회원 전체 세션 폐기")
    void 탈퇴_요청() {
        Member member = Member.createBySocial("user@kakao.com");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        service.withdraw(1L);

        assertThat(member.isWithdrawn()).isTrue();
        verify(refreshTokenService).revokeAll(member);
    }

    @Test
    @DisplayName("회원이 없으면 ENTITY_NOT_FOUND")
    void 회원_없음() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ENTITY_NOT_FOUND));
    }
}
