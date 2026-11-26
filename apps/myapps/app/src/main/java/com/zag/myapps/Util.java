package com.zag.myapps;

final class Util {
    private Util() {}

    /** Only plain file paths ever reach the shell. */
    static boolean safePath(String p) {
        return p != null && p.matches("[A-Za-z0-9._/-]+");
    }
}
