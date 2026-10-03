import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tiny reflective runner: scans a compiled test directory for classes whose name ends with "Test",
 * runs every public no-arg method annotated with org.junit.Test and prints a summary.
 * Usage: java ShimRunner <classesDir> [classNameFilter]
 */
public final class ShimRunner {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        String filter = args.length > 1 ? args[1] : "";
        List<String> classNames = new ArrayList<>();
        collect(dir, dir, classNames);
        Collections.sort(classNames);

        URLClassLoader loader = new URLClassLoader(new URL[] {dir.toURI().toURL()}, ShimRunner.class.getClassLoader());
        int passed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        for (String name : classNames) {
            if (!name.endsWith("Test") || !name.contains(filter)) continue;
            Class<?> cls = Class.forName(name, true, loader);
            List<Method> methods = new ArrayList<>();
            for (Method m : cls.getMethods()) {
                if (m.isAnnotationPresent(org.junit.Test.class) && m.getParameterCount() == 0) methods.add(m);
            }
            methods.sort((a, b) -> a.getName().compareTo(b.getName()));
            for (Method m : methods) {
                String label = cls.getSimpleName() + "." + m.getName();
                try {
                    Object instance = cls.getDeclaredConstructor().newInstance();
                    m.invoke(instance);
                    passed++;
                    System.out.println("PASS  " + label);
                } catch (InvocationTargetException e) {
                    failed++;
                    Throwable t = e.getCause();
                    System.out.println("FAIL  " + label + "  ->  " + t);
                    StackTraceElement[] st = t.getStackTrace();
                    for (int i = 0; i < Math.min(4, st.length); i++) System.out.println("        at " + st[i]);
                    failures.add(label);
                }
            }
        }
        System.out.println();
        System.out.println("Tests: " + (passed + failed) + ", passed: " + passed + ", failed: " + failed);
        if (failed > 0) {
            System.out.println("Failed: " + failures);
            System.exit(1);
        }
    }

    private static void collect(File root, File dir, List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collect(root, f, out);
            } else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String rel = root.toURI().relativize(f.toURI()).getPath();
                out.add(rel.substring(0, rel.length() - ".class".length()).replace('/', '.'));
            }
        }
    }
}
