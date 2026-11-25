package academy.llavor.academia.sso;
import academy.llavor.academia.sso.crypto.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SsoCryptoTest {
    final UUID p=SsoConfigurationTest.P,a=SsoConfigurationTest.A,op=UUID.fromString("44444444-4444-4444-8444-444444444444");
    final Instant now=Instant.parse("2026-01-01T12:00:00Z");
    SsoCipher.Context context(){return new SsoCipher.Context(p,a,"TEST","S01",op,1,now);}
    static class SyntheticRandom extends SecureRandom { int value;public void nextBytes(byte[] b){Arrays.fill(b,(byte)++value);} }
    @Test void authenticatedRoundTripAndNonceUniqueness(){
        var cipher=new SsoCipher(()->new byte[32],new SyntheticRandom());char[] token="A".repeat(43).toCharArray();
        String one=cipher.encrypt(context(),token),two=cipher.encrypt(context(),token);
        assertEquals(103,one.length());assertNotEquals(one,two);assertArrayEquals(token,cipher.decrypt(context(),one));
        assertFalse(one.contains(new String(token)));assertEquals(64,SsoCipher.digest(token).length());
    }
    @Test void aadBindsEveryIdentityPurposeAndImmutableMetadata(){
        var cipher=new SsoCipher(()->new byte[32]);String envelope=cipher.encrypt(context(),"A".repeat(43).toCharArray());
        for(var bad:List.of(new SsoCipher.Context(a,a,"TEST","S01",op,1,now),new SsoCipher.Context(p,p,"TEST","S01",op,1,now),
            new SsoCipher.Context(p,a,"OTHER","S01",op,1,now),new SsoCipher.Context(p,a,"TEST","S01",a,1,now),
            new SsoCipher.Context(p,a,"TEST","S01",op,1,now.plusSeconds(1))))assertThrows(SsoCipher.Failure.class,()->cipher.decrypt(bad,envelope));
        assertThrows(SsoCipher.Failure.class,()->new SsoCipher.Context(p,a,"TEST","K01",op,1,now));
        assertThrows(SsoCipher.Failure.class,()->new SsoCipher.Context(p,a,"TEST","S01",op,2,now));
        assertThrows(SsoCipher.Failure.class,()->new SsoCipher(()->new byte[31]).decrypt(context(),envelope));
    }
    @Test void malformedOrNoncanonicalEnvelopesAndWrongKeysRejected(){
        var cipher=new SsoCipher(()->new byte[32]);String envelope=cipher.encrypt(context(),"A".repeat(43).toCharArray());
        byte[] other=new byte[32];other[0]=1;
        assertThrows(SsoCipher.Failure.class,()->new SsoCipher(()->other.clone()).decrypt(context(),envelope));
        for(String bad:List.of("","v2."+envelope.substring(3),envelope.replace("M01","M02"),envelope+"=",envelope+"CANARY")){
            var e=assertThrows(SsoCipher.Failure.class,()->cipher.decrypt(context(),bad));assertNull(e.getCause());assertEquals(0,e.getStackTrace().length);assertFalse(e.toString().contains("CANARY"));
        }
    }
    @Test void externalMasterIsPrivateCanonicalAndNeverFallsBack()throws Exception{
        Path parent=Files.createTempDirectory(Path.of(System.getProperty("user.home")),".llavor-master-test-");
        Path file=parent.resolve("synthetic.key");
        try{
            if(Files.getFileAttributeView(parent,PosixFileAttributeView.class)!=null){
                Files.setPosixFilePermissions(parent,PosixFilePermissions.fromString("rwx------"));
                Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            }else{
                var user=parent.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
                var acl=List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
                Files.getFileAttributeView(parent,AclFileAttributeView.class).setAcl(acl);
                Files.createFile(file,new java.nio.file.attribute.FileAttribute<List<AclEntry>>(){public String name(){return "acl:acl";}public List<AclEntry> value(){return acl;}});
            }
            Files.writeString(file,"A".repeat(43));
            assertEquals(32,new ExternalMasterKey(file).load().length);
            Files.writeString(file,"A".repeat(43)+"\n");assertThrows(SsoCipher.Failure.class,()->new ExternalMasterKey(file).load());
            Files.writeString(file,"A".repeat(43));
            if(Files.getFileAttributeView(file,PosixFileAttributeView.class)!=null)Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-r--r--"));
            else{
                var lookup=file.getFileSystem().getUserPrincipalLookupService();UserPrincipal everyone=null;
                for(String name:List.of("Everyone","Todos"))try{everyone=lookup.lookupPrincipalByName(name);break;}catch(UserPrincipalNotFoundException ignored){}
                assertNotNull(everyone);var view=Files.getFileAttributeView(file,AclFileAttributeView.class);var acl=new ArrayList<>(view.getAcl());
                acl.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build());view.setAcl(acl);
            }
            assertThrows(SsoCipher.Failure.class,()->new ExternalMasterKey(file).load());
            assertThrows(SsoCipher.Failure.class,()->new ExternalMasterKey(Path.of("relative.key")));
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(parent);}
    }
}
