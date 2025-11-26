package util;

import java.util.*;

public final class CsvUtils {
    private CsvUtils(){}

    public static String esc(String s){
        return s.replace("\\","\\\\").replace(";","\\;").replace("\n","\\n");
    }
    public static String unesc(String s){
        StringBuilder out = new StringBuilder(); boolean esc=false;
        for (char c: s.toCharArray()) {
            if (esc) { out.append(c=='n'?'\n':c); esc=false; }
            else if (c=='\\') esc=true; else out.append(c);
        }
        return out.toString();
    }
    public static java.util.List<String> split(String line){
        java.util.List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder(); boolean esc=false;
        for (char c: line.toCharArray()){
            if (esc){ cur.append(c=='n'?'\n':c); esc=false; continue; }
            if (c=='\\'){ esc=true; continue; }
            if (c==';'){ cols.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        cols.add(cur.toString()); return cols;
    }
}
