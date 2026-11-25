package academy.llavor.academia.sso.browser;
/** Request-only display model; never a technical credential or a session grant. */
public record HumanView(String displayName,String academyCode,String classroomCode,String classroomName,String csrf) {
    public String toString(){return "HumanView[redacted]";}
}
