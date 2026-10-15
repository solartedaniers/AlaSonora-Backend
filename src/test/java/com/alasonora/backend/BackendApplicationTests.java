package com.alasonora.backend;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

// Este test arranca contra la base configurada en .env: con ddl-auto=none nunca altera su esquema.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@EnabledIf(value = "databaseConfigured",
	disabledReason = "Sin DB_URL (variable de entorno o .env) no hay base de datos contra la cual levantar el contexto")
class BackendApplicationTests {

	private static final String DB_URL = "DB_URL";
	private static final Path ENV_FILE = Path.of(".env");

	@Test
	void contextLoads() {
	}

	// Leo DB_URL igual que Spring: primero el entorno y luego el .env (spring.config.import).
	static boolean databaseConfigured() throws IOException {
		if (hasText(System.getenv(DB_URL))) {
			return true;
		}
		if (!Files.isRegularFile(ENV_FILE)) {
			return false;
		}
		Properties env = new Properties();
		try (Reader reader = Files.newBufferedReader(ENV_FILE)) {
			env.load(reader);
		}
		return hasText(env.getProperty(DB_URL));
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
