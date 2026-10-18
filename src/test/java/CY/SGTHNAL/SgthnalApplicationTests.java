package CY.SGTHNAL;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "app.scheduling.enabled=false")
@ActiveProfiles("local")
class SgthnalApplicationTests {

    @Test
    void contextLoads() {
    }
}
