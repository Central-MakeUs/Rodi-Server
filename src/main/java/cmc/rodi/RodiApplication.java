package cmc.rodi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RodiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RodiApplication.class, args);
    }
}
