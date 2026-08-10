package cmc.rodi.domain.member.entity;

/**
 * 회원 레벨. 최초 배정은 온보딩에서 클라이언트가 운전 경험 점수를 변환해 보내고, <b>그 이후 승급은 서버가 누적 주행거리로</b> 판단한다(스펙 012).
 *
 * <p>임계값은 여기 한 곳에만 둔다 — 화면과 서버가 같은 표를 봐야 하므로 서버가 단일 출처다. 저장 단위는 미터.
 */
public enum Level {
    SEED(0),
    ROOKIE(50_000),
    OWNER(150_000),
    EXPLORER(300_000),
    NAVIGATOR(600_000);

    /** 이 레벨이 시작되는 누적 주행거리(m). */
    private final long startMeters;

    Level(long startMeters) {
        this.startMeters = startMeters;
    }

    public long getStartMeters() {
        return startMeters;
    }

    /** 다음 레벨(최상위면 null). */
    public Level next() {
        return isTop() ? null : values()[ordinal() + 1];
    }

    public boolean isTop() {
        return ordinal() == values().length - 1;
    }

    /** 다음 레벨에 필요한 누적 주행거리(m). 최상위면 null — 목표가 없다. */
    public Long nextStartMeters() {
        Level next = next();
        return next == null ? null : next.startMeters;
    }

    /**
     * 누적 주행거리로 도달한 레벨. 기준을 만족하는 가장 높은 레벨을 돌려준다.
     *
     * <p>거리가 음수일 리는 없지만, 어떤 값이 와도 최소 {@link #SEED}는 보장한다.
     */
    public static Level of(long totalMeters) {
        Level reached = SEED;
        for (Level level : values()) {
            if (totalMeters >= level.startMeters) {
                reached = level;
            }
        }
        return reached;
    }

    /**
     * 현재 레벨 구간의 진행률(0~100, 내림). 최상위는 목표가 없어 항상 100이다.
     *
     * <p>구간을 벗어난 값(승급 직전·직후의 과도기)이 들어와도 0~100으로 잘라 낸다.
     */
    public int progressPercent(long totalMeters) {
        Long goal = nextStartMeters();
        if (goal == null) {
            return 100;
        }
        long span = goal - startMeters;
        long done = totalMeters - startMeters;
        return (int) Math.max(0, Math.min(100, done * 100 / span));
    }
}
