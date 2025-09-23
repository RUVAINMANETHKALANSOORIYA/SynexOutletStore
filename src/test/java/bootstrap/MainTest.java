package bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    @DisplayName("Main class exposes a public static void main(String[]) entry point")
    void main_method_exists() throws Exception {
        Class<?> clazz = Main.class;
        Method m = clazz.getDeclaredMethod("main", String[].class);
        assertNotNull(m);
        assertTrue(Modifier.isPublic(m.getModifiers()));
        assertTrue(Modifier.isStatic(m.getModifiers()));
        assertEquals(void.class, m.getReturnType());
    }
}
