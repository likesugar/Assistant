import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.*;

/**
 * 预处理 media3 的 jar：剥离会触发 d8 8.2.2 内部 NPE 的编译期元数据属性
 * （InnerClasses / EnclosingMethod / NestHost / NestMember / MethodParameters / 未知属性）。
 * 这些是编译期元数据，运行期不需要，也不影响 d8 的 lambda 脱糖。
 * 字节码本身逐字节复制，不改变逻辑。
 */
public class StripAttrs {
    public static void main(String[] a) throws Exception {
        Path inDir = Paths.get(a[0]);
        Path outDir = Paths.get(a[1]);
        try (Stream<Path> s = Files.walk(inDir)) {
            for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".jar"))::iterator) {
                Path rel = inDir.relativize(p);
                processJar(p, outDir.resolve(rel.toString()));
            }
        }
        System.out.println("StripAttrs done.");
    }

    static void processJar(Path jar, Path outJar) throws IOException {
        Files.createDirectories(outJar.getParent());
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar));
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outJar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                byte[] data = readAll(zis);
                if (name.endsWith(".class") && !name.equals("module-info.class")
                        && !name.startsWith("META-INF/versions/")) {
                    data = strip(data);
                }
                ZipEntry ne = new ZipEntry(name);
                zos.putNextEntry(ne);
                zos.write(data);
                zos.closeEntry();
            }
        }
    }

    static byte[] strip(byte[] b) {
        ClassReader cr = new ClassReader(b);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override public void visitInnerClass(String n, String o, String i, int acc) { /* drop */ }
            @Override public void visitNestHost(String n) { /* drop */ }
            @Override public void visitNestMember(String n) { /* drop */ }
            @Override public void visitAttribute(Attribute a) { /* drop unknown attrs */ }
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                MethodVisitor mv = super.visitMethod(a, n, d, s, e);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override public void visitParameter(String name, int access) { /* drop MethodParameters */ }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
