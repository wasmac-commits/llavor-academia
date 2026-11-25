package academy.llavor.academia.sso;
import java.util.*;
/** Bounded wire input, never used as a user message or log object. */
public record SsoWireResponse(int status,Map<String,List<String>> headers,byte[] body) {
    public static final int LIMIT=4096;
    public SsoWireResponse{
        if(body==null||body.length>LIMIT)throw new SsoClientFailure(SsoClientFailure.Code.INVALID_RESPONSE);
        var copy=new HashMap<String,List<String>>();headers.forEach((k,v)->copy.put(k,List.copyOf(v)));headers=Map.copyOf(copy);body=body.clone();
    }
    public byte[] body(){return body.clone();}
    public String header(String name){
        var values=headers.entrySet().stream().filter(e->name.equalsIgnoreCase(e.getKey())).flatMap(e->e.getValue().stream()).toList();
        return values.size()==1?values.getFirst():"";
    }
    public String toString(){return "SsoWireResponse[redacted]";}
}
