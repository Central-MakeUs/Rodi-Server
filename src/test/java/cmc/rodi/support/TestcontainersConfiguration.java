package cmc.rodi.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostGIS 컨테이너. {@code @ServiceConnection}으로 스프링이 datasource를 자동 주입한다. 테스트 실행 시 컨테이너가 자동
 * 생성/삭제되므로 상시 DB가 필요 없다. (Docker 데몬만 실행 중이면 됨)
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("imresamu/postgis:16-3.4")
                        .asCompatibleSubstituteFor("postgres"));
    }
}
