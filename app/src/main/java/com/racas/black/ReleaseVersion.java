package com.racas.black;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Preview builds share the same core version; their final build number still matters.
final class ReleaseVersion {
    private static final Pattern CORE=Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([^+]+))?(?:\\+.*)?$");
    private static final Pattern NUMBER=Pattern.compile("\\d+");
    private static String normalized(String n){String s=n.replaceFirst("^0+(?!$)","");return s.isEmpty()?"0":s;}
    private static int numbers(List<String> a,List<String> b){
        for(int i=0;i<Math.max(a.size(),b.size());i++){
            String x=normalized(i<a.size()?a.get(i):"0"),y=normalized(i<b.size()?b.get(i):"0");
            int n=Integer.compare(x.length(),y.length());if(n!=0)return n;
            n=x.compareTo(y);if(n!=0)return n;
        }
        return 0;
    }
    private static List<String> digits(String s){
        List<String> out=new ArrayList<>();Matcher m=NUMBER.matcher(s==null?"":s);
        while(m.find())out.add(m.group());return out;
    }
    static int compare(String a,String b){
        Matcher x=CORE.matcher(a==null?"":a.trim()),y=CORE.matcher(b==null?"":b.trim());
        if(!x.matches()||!y.matches())return 0;
        int n=numbers(digits(x.group(1)),digits(y.group(1)));if(n!=0)return n;
        String sx=x.group(2),sy=y.group(2);
        if(sx==null&&sy!=null)return 1;if(sx!=null&&sy==null)return -1;
        return numbers(digits(sx),digits(sy));
    }
}
