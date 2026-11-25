package academy.llavor.academia.web.https;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.json.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** External deployment policy; identical behavior in every environment. No default proxy trust. */
@ApplicationScoped
public class TrustedProxyConfiguration {
 @Inject @ConfigProperty(name="llavor.http.proxy.config",defaultValue="") Provider<String> configPath;
 private volatile Settings cached;
 public record Settings(URI origin,String token,Set<String> trustedPeers,Set<String> directHttpPeers,URI portalBase,URI academiaBase){public String toString(){return "TrustedProxySettings[redacted]";}}
 private static URI httpsBase(String text,boolean originOnly){
  URI uri=URI.create(text);String path=uri.getRawPath();
  if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getPort()==0||uri.getPort()>65535||!uri.toASCIIString().equals(text)||!(originOnly?path.isEmpty():path.matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")))throw new IllegalArgumentException();return uri;
 }
 private static Set<String> peers(JsonArray array){
  var result=new HashSet<String>();for(var value:array){String peer=((JsonString)value).getString();if(!(peer.contains(":")?peer.matches("[0-9a-fA-F:]{2,64}"):peer.matches("[0-9.]{3,15}")))throw new IllegalArgumentException();try{result.add(java.net.InetAddress.getByName(peer).getHostAddress());}catch(Exception invalid){throw new IllegalArgumentException();}}return Set.copyOf(result);
 }
 public Settings settings(){
  var current=cached;if(current!=null)return current;
  String file="";try{if(configPath!=null)file=configPath.get();}catch(NoSuchElementException absent){}
  if(file==null||file.isBlank())return null;
  synchronized(this){if(cached!=null)return cached;
   try(var reader=Json.createReader(Files.newInputStream(Path.of(file)))){
    var json=reader.readObject();String token=json.getString("proxyToken");
    if(json.getInt("version")!=1||!token.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
    var trusted=peers(json.getJsonArray("trustedPeers"));if(trusted.isEmpty())throw new IllegalArgumentException();
    cached=new Settings(httpsBase(json.getString("origin"),true),token,trusted,peers(json.getJsonArray("directHttpPeers")),httpsBase(json.getString("portalBaseUrl"),false),httpsBase(json.getString("academiaBaseUrl"),false));return cached;
   }catch(Exception invalid){throw new IllegalStateException("Proxy configuration unavailable");}
  }
 }
 public boolean enabled(){return settings()!=null;}
 public URI base(String application){var s=settings();if(s==null)throw new IllegalStateException("Proxy configuration unavailable");return switch(application){case "llavor-portal"->s.portalBase();case "llavor-academia"->s.academiaBase();default->throw new IllegalArgumentException("Unknown application");};}
 public boolean authentic(String value){var s=settings();return s!=null&&value!=null&&value.length()==64&&MessageDigest.isEqual(s.token().getBytes(StandardCharsets.US_ASCII),value.getBytes(StandardCharsets.US_ASCII));}
 public static boolean containsPeer(Set<String> peers,String actual){if(actual==null||!actual.matches("[0-9a-fA-F:.]{2,64}"))return false;try{return peers.contains(java.net.InetAddress.getByName(actual).getHostAddress());}catch(Exception invalid){return false;}}
}
