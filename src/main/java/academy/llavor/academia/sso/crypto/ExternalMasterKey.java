package academy.llavor.academia.sso.crypto;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Only a nonsecret path may come from the environment. Key bytes have no fallback. */
public final class ExternalMasterKey implements SsoCipher.Keys {
    public static final String PATH_VARIABLE="LLAVOR_ACADEMIA_MASTER_KEY_DIR";
    private final Path path;
    public ExternalMasterKey(Path path){
        if(path==null||!path.isAbsolute()||!path.normalize().equals(path))throw new SsoCipher.Failure();
        this.path=path;
    }
    public static ExternalMasterKey configured(UUID academy){
        try{
            if(academy==null||academy.equals(new UUID(0,0)))throw new SsoCipher.Failure();
            Path directory=Path.of(System.getenv(PATH_VARIABLE));
            return new ExternalMasterKey(directory.resolve(academy+"-M01.key"));
        }catch(Exception e){throw new SsoCipher.Failure();}
    }
    public byte[] load(){
        byte[] text=null;
        try{
            check();
            try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){text=in.readNBytes(44);}
            if(text.length!=43)throw new SsoCipher.Failure();
            check();
            return SsoCipher.master(new String(text,StandardCharsets.US_ASCII));
        }catch(Exception e){throw new SsoCipher.Failure();}finally{if(text!=null)Arrays.fill(text,(byte)0);}
    }
    private void check()throws Exception{
        for(Path p=path;p!=null;p=p.getParent()){
            var basic=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            boolean file=p.equals(path);
            if(basic.isSymbolicLink()||basic.isOther()||(file?!basic.isRegularFile():!basic.isDirectory()))throw new SsoCipher.Failure();
            if(!file&&(Files.exists(p.resolve(".git"),LinkOption.NOFOLLOW_LINKS)||Files.exists(p.resolve("pom.xml"),LinkOption.NOFOLLOW_LINKS)))throw new SsoCipher.Failure();
            var lookup=p.getFileSystem().getUserPrincipalLookupService();
            var own=lookup.lookupPrincipalByName(System.getProperty("user.name"));
            var posix=Files.getFileAttributeView(p,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            if(posix!=null){
                var attrs=posix.readAttributes();boolean root=attrs.owner().getName().equals("root");
                if(!root&&!attrs.owner().equals(own))throw new SsoCipher.Failure();
                var forbidden=file?EnumSet.of(PosixFilePermission.GROUP_READ,PosixFilePermission.GROUP_WRITE,PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,PosixFilePermission.OTHERS_WRITE,PosixFilePermission.OTHERS_EXECUTE,PosixFilePermission.OWNER_EXECUTE):
                    EnumSet.of(PosixFilePermission.GROUP_WRITE,PosixFilePermission.OTHERS_WRITE);
                if(!Collections.disjoint(attrs.permissions(),forbidden))throw new SsoCipher.Failure();
                if(file&&((Number)Files.getAttribute(p,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).intValue()!=1)throw new SsoCipher.Failure();
            }else{
                var acl=Files.getFileAttributeView(p,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
                if(acl==null)throw new SsoCipher.Failure();
                Set<UserPrincipal> trusted=new HashSet<>();trusted.add(own);
                for(String name:List.of("NT AUTHORITY\\SYSTEM","BUILTIN\\Administrators","BUILTIN\\Administradores","NT SERVICE\\TrustedInstaller"))
                    try{trusted.add(lookup.lookupPrincipalByName(name));}catch(UserPrincipalNotFoundException ignored){}
                if(!trusted.contains(acl.getOwner()))throw new SsoCipher.Failure();
                var forbidden=EnumSet.of(AclEntryPermission.DELETE,AclEntryPermission.DELETE_CHILD,AclEntryPermission.WRITE_ACL,AclEntryPermission.WRITE_OWNER);
                if(file)forbidden.addAll(EnumSet.of(AclEntryPermission.READ_DATA,AclEntryPermission.WRITE_DATA,AclEntryPermission.APPEND_DATA));
                for(var entry:acl.getAcl())if(entry.type()==AclEntryType.ALLOW&&!entry.flags().contains(AclEntryFlag.INHERIT_ONLY)
                        &&!trusted.contains(entry.principal())&&!Collections.disjoint(entry.permissions(),forbidden))throw new SsoCipher.Failure();
            }
        }
    }
    public String toString(){return "ExternalMasterKey[redacted]";}
}
