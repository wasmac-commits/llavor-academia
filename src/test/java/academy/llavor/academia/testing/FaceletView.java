package academy.llavor.academia.testing;
import java.nio.file.*;
import java.io.StringWriter;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
/** Resolves local template/define/insert/include structure, never evaluates EL or remote content. */
public final class FaceletView {
 private static final Path WEB=Path.of("src/main/webapp").toAbsolutePath().normalize();
 private static final String UI="jakarta.faces.facelets";
 private FaceletView(){}
 public static Document parse(Path file)throws Exception{
  var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);
  f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
  return f.newDocumentBuilder().parse(file.toFile());
 }
 private static Path local(Path parent,String file){
  Path p=(file.startsWith("/")?WEB.resolve(file.substring(1)):parent.resolve(file)).normalize();
  if(!p.startsWith(WEB))throw new IllegalArgumentException("Nonlocal Facelet");return p;
 }
 public static Document composed(String file)throws Exception{return compose(local(WEB,file),new HashSet<>());}
 private static Document compose(Path path,Set<Path> visiting)throws Exception{
  if(!visiting.add(path))throw new IllegalArgumentException("Cyclic Facelet");
  try{
   Document view=parse(path);Element root=view.getDocumentElement();
   if(root.hasAttribute("template")){
    Document template=compose(local(path.getParent(),root.getAttribute("template")),visiting);
    var definitions=new HashMap<String,Element>();
    for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&UI.equals(e.getNamespaceURI())&&"define".equals(e.getLocalName()))definitions.put(e.getAttribute("name"),e);
    inserts(template.getDocumentElement(),template,definitions);view=template;
   }
   includes(view.getDocumentElement(),view,path.getParent(),visiting);return view;
  }finally{visiting.remove(path);}
 }
 private static void inserts(Node parent,Document owner,Map<String,Element> definitions){
  for(Node n=parent.getFirstChild();n!=null;){Node next=n.getNextSibling();
   if(n instanceof Element e&&UI.equals(e.getNamespaceURI())&&"insert".equals(e.getLocalName())){
    Node source=definitions.getOrDefault(e.getAttribute("name"),e);
    for(Node c=source.getFirstChild();c!=null;c=c.getNextSibling())parent.insertBefore(owner.importNode(c,true),n);
    parent.removeChild(n);
   }else inserts(n,owner,definitions);n=next;
  }
 }
 private static void includes(Node parent,Document owner,Path directory,Set<Path> visiting)throws Exception{
  for(Node n=parent.getFirstChild();n!=null;){Node next=n.getNextSibling();
   if(n instanceof Element e&&UI.equals(e.getNamespaceURI())&&"include".equals(e.getLocalName())){
    Element root=compose(local(directory,e.getAttribute("src")),visiting).getDocumentElement();
    for(Node c=root.getFirstChild();c!=null;c=c.getNextSibling())parent.insertBefore(owner.importNode(c,true),n);
    parent.removeChild(n);
   }else includes(n,owner,directory,visiting);n=next;
  }
 }
 public static String text(String file)throws Exception{
  var output=new StringWriter();TransformerFactory.newInstance().newTransformer().transform(new DOMSource(composed(file)),new StreamResult(output));return output.toString();
 }
}
