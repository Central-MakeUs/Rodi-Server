package cmc.rodi.global.auth.jwt;

/**
 * access token 발급·검증. 현재 구현은 HS256({@link JwtTokenProvider}). 추후 MSA 전환 시 RS256 구현으로 교체할 수 있도록
 * 인터페이스로 분리한다(ADR 0009).
 */
public interface TokenProvider {

    String createAccessToken(Long memberId);

    /** access token 검증 후 memberId 추출. 유효하지 않으면 예외. */
    Long getMemberId(String accessToken);
}
