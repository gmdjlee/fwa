package dev.dj.foldwindow;

interface IShellExec {
    String run(in String[] argv, long timeoutMs);
}
