package cmc.rodi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import cmc.rodi.support.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RodiApplicationTests {

    @Test
    void contextLoads() {
    }

}
