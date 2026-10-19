import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.util.*;
public class CompareClasses {
 static String normalized(byte[] bytes) {
  StringWriter s=new StringWriter();
  ClassVisitor v=new ClassVisitor(Opcodes.ASM9,new TraceClassVisitor(new PrintWriter(s))) {
   @Override public AnnotationVisitor visitAnnotation(String d,boolean visible) {
    return d.equals("Lkotlin/jvm/internal/SourceDebugExtension;") ? null : super.visitAnnotation(d,visible);
   }
  };
  new ClassReader(bytes).accept(v,ClassReader.SKIP_DEBUG);
  return s.toString();
 }
 public static void main(String[] args) throws Exception {
  int raw=0,norm=0,total=0; Set<String> names=new TreeSet<>();
  try(ZipFile z=new ZipFile(args[0])) {
   for(ZipEntry e: Collections.list(z.entries())) {
    if(!e.getName().endsWith(".class"))continue;
    names.add(e.getName());total++;
    Path p=Path.of(args[1],e.getName());
    if(!Files.exists(p)){System.out.println("MISSING "+e.getName());continue;}
    byte[] a=z.getInputStream(e).readAllBytes(),b=Files.readAllBytes(p);
    if(Arrays.equals(a,b))raw++;
    if(normalized(a).equals(normalized(b)))norm++;
    else System.out.println("DIFFERENT "+e.getName());
   }
  }
  boolean[] extra = {false};
  try(var files=Files.walk(Path.of(args[1]))) {
   files.filter(p->p.toString().endsWith(".class")).forEach(p->{String n=Path.of(args[1]).relativize(p).toString(); if(!names.contains(n)){extra[0]=true;System.out.println("EXTRA "+n);}});
  }
  System.out.println("TOTAL="+total+" RAW_IDENTICAL="+raw+" NORMALIZED_IDENTICAL="+norm);
  if(norm != total || extra[0]) System.exit(1);
 }
}
