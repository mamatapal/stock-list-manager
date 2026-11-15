package app.stocklist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StockListApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockListApplication.class, args);
    }
}
