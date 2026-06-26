package com.csembstu.mbstubitbye;

//import com.csembstu.mbstubitbye.config.EnvConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MbstubitbyeApplication {

	public static void main(String[] args) {

//		EnvConfig.loadEnv();
		SpringApplication.run(MbstubitbyeApplication.class, args);
	}

}
