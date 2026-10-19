package com.racas.black;

public class ReleaseVersionTest {
    private static int cases;
    private static void compare(String a,String b,int expected){
        int actual=Integer.signum(ReleaseVersion.compare(a,b));cases++;
        if(actual!=expected)throw new AssertionError(a+" versus "+b+": "+actual+", expected "+expected);
    }
    public static void main(String[] args){
        compare("v1.3.2-launcher-teste.5","1.3.2-launcher-teste.5",0);
        compare("v1.3.2-launcher-teste.6","1.3.2-launcher-teste.5",1);
        compare("v1.3.2-launcher-teste.5","1.3.2-launcher-teste.6",-1);
        compare("v1.3.2-launcher-teste.10","1.3.2-launcher-teste.9",1);
        compare("v1.3.3","1.3.2-launcher-teste.999",1);
        compare("v1.3.1","1.3.2-launcher-teste.5",-1);
        compare("1.3.2","1.3.2-launcher-teste.5",1);
        compare("1.3.2-launcher-teste.5","1.3.2",-1);
        compare("1.3.2-launcher.7","1.3.2-launcher-teste.6",1);
        compare("  V1.3.2-launcher-teste.0006  ","1.3.2-launcher-teste.6",0);
        compare("1.3.2-launcher-teste.6+metadata","1.3.2-launcher-teste.6",0);
        compare("1.3.2-launcher-teste.99999999999999999999","1.3.2-launcher-teste.9999999999999999999",1);
        compare("1.3.2.0","1.3.2",0);
        compare("unknown-release","1.3.2-launcher-teste.5",0);
        compare(null,"1.3.2-launcher-teste.5",0);
        System.out.println(cases+" update version cases passed (preview builds, stable versions and downgrade prevention)");
    }
}
