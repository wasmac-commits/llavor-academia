package academy.llavor.academia.sso.browser;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.*;
import java.util.*;
/** Bounded ephemeral contexts; only hashes of browser secrets. No timers or database. */
@ApplicationScoped
public class PendingBrowserContexts {
    public static final int TTL_SECONDS=120,LIMIT=4096;
    public record Context(UUID id,BrowserSettings settings,String bindingHash,Instant issuedAt,Instant expiresAt){
        public String toString(){return "BrowserContext[redacted]";}
    }
    private final Map<UUID,Context> entries=new HashMap<>();private final Clock clock;
    public PendingBrowserContexts(){this(Clock.systemUTC());}
    PendingBrowserContexts(Clock clock){this.clock=clock;}
    public synchronized Context issue(BrowserSettings settings,String hash){
        Instant now=clock.instant();entries.values().removeIf(c->!c.expiresAt().isAfter(now)||now.isBefore(c.issuedAt()));
        // A browser has one outstanding attempt. Starting again replaces only its matching hash.
        if(entries.size()>=LIMIT)throw new BrowserFailure("CONTEXT_CAPACITY");
        UUID id;do{id=UUID.randomUUID();}while(entries.containsKey(id));
        var context=new Context(id,settings,hash,now,now.plusSeconds(TTL_SECONDS));entries.put(id,context);return context;
    }
    public synchronized Context peek(UUID id){
        var c=entries.get(id);if(c==null)throw new BrowserFailure("CONTEXT_UNAVAILABLE");
        Instant now=clock.instant();if(!c.expiresAt().isAfter(now)||now.isBefore(c.issuedAt())){entries.remove(id);throw new BrowserFailure("CONTEXT_UNAVAILABLE");}
        return c;
    }
    public synchronized Context claim(UUID id,String bindingHash,BrowserSettings current){
        var c=peek(id);
        if(!BrowserTokens.same(c.bindingHash(),bindingHash)||!c.settings().equals(current))throw new BrowserFailure("CONTEXT_MISMATCH");
        entries.remove(id);return c;
    }
    public synchronized void replaceBrowser(String oldHash){if(oldHash!=null)entries.values().removeIf(c->BrowserTokens.same(c.bindingHash(),oldHash));}
}
