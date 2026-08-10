package cmc.rodi.domain.practice.entity;

/**
 * 방문 인증 기준(스펙 011). 코스 경로선 150m 이내에서 이동한 "인정 주행거리"가 필요 거리에 도달하면 인증된다.
 *
 * <p>필요 거리 = {@code min(코스 전체 거리 × 40%, 5km)}. 코스가 12.5km를 넘어서면 상한(5km)이 걸린다. 판정은 서버가 한다 — 앱은 측정한
 * 인정 주행거리만 보내고, 인증 여부를 스스로 정하지 않는다.
 */
public final class VisitCertification {

    /** 코스 전체 거리 중 이만큼 달리면 인증. */
    private static final double REQUIRED_RATIO = 0.4;

    /** 아무리 긴 코스여도 이 거리까지만 요구한다. */
    private static final int MAX_REQUIRED_METERS = 5_000;

    private VisitCertification() {}

    /** 인증에 필요한 인정 주행거리(m). 주행거리가 없는 장소(주차장 등)는 인증 개념이 없어 0을 돌려주고, 이 경우 인증도 성립하지 않는다. */
    public static int requiredMeters(Integer courseDistanceMeters) {
        if (courseDistanceMeters == null || courseDistanceMeters <= 0) {
            return 0;
        }
        return (int)
                Math.min(Math.round(courseDistanceMeters * REQUIRED_RATIO), MAX_REQUIRED_METERS);
    }

    /**
     * 이번 방문에서 <b>레벨 누적에 반영할 수 있는 거리</b>(m) — 코스 전체 거리를 넘지 않는다(스펙 012).
     *
     * <p>인증 필요 거리가 <i>하한</i>이라면 이건 <i>상한</i>이다. 레벨은 내려가지 않아 한 번 잘못 오르면 되돌릴 수 없는데 측정값은 앱이 보내므로, 서버가
     * 아는 값(코스 전체 거리)으로 잘라 낸다. 왕복해서 실제로 더 달렸더라도 한 방문의 값어치는 코스 한 바퀴까지다.
     *
     * <p>주행거리가 없는 장소(주차장)는 0 — 레벨에 기여하지 않는다.
     */
    public static long accruableMeters(Integer courseDistanceMeters, int certifiedDistanceMeters) {
        if (courseDistanceMeters == null || courseDistanceMeters <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(certifiedDistanceMeters, courseDistanceMeters));
    }

    /** 인정 주행거리가 필요 거리에 도달했는지. 필요 거리가 0인 장소(주차장)는 인증되지 않는다. */
    public static boolean isCertified(Integer courseDistanceMeters, int certifiedDistanceMeters) {
        int required = requiredMeters(courseDistanceMeters);
        return required > 0 && certifiedDistanceMeters >= required;
    }
}
