#!/usr/bin/env python3
"""AndroidForge build runner: run requested variant first and report the true result."""
from __future__ import annotations
import argparse,json,os,subprocess,sys,time
from pathlib import Path

def run(cmd:list[str],cwd:Path,log_file:Path)->tuple[int,str]:
    log_file.parent.mkdir(parents=True,exist_ok=True)
    print(f"\n=== Running: {' '.join(cmd)}",flush=True)
    print(f"    cwd: {cwd}\n    log: {log_file}",flush=True)
    with log_file.open("w",encoding="utf-8",errors="replace") as f:
        f.write(f"$ {' '.join(cmd)}\n# cwd: {cwd}\n\n")
        p=subprocess.Popen(cmd,cwd=str(cwd),env=os.environ.copy(),stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT,text=True,bufsize=1)
        assert p.stdout is not None
        for line in p.stdout:
            sys.stdout.write(line); sys.stdout.flush(); f.write(line)
        return p.wait(),str(log_file)

def load_json(value:str)->dict:
    p=Path(value)
    return json.loads(p.read_text()) if p.exists() else json.loads(value)

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument("--root",required=True); ap.add_argument("--detect",required=True)
    ap.add_argument("--toolchain",required=True); ap.add_argument("--variant",default="auto",choices=["auto","debug","release","bundle"])
    ap.add_argument("--log-dir"); ap.add_argument("--output")
    a=ap.parse_args()
    detect=load_json(a.detect); tc=load_json(a.toolchain); root=Path(a.root).resolve()
    logs=Path(a.log_dir or root.parent/"androidforge-logs"); logs.mkdir(parents=True,exist_ok=True)
    all_commands=[list(c) for c in tc.get("build_commands",[])]
    def matches(cmd:list[str])->bool:
        s=" ".join(cmd).lower()
        if a.variant=="debug": return "debug" in s and "bundle" not in s
        if a.variant=="release": return "release" in s and "bundle" not in s and "assemble" in s
        if a.variant=="bundle": return "bundle" in s
        return True
    commands=[c for c in all_commands if matches(c)]
    # For auto, favor a directly installable APK, and only fall back to a bundle if needed.
    if a.variant=="auto":
        commands.sort(key=lambda c: 0 if "assembleRelease" in " ".join(c) else 1 if "assembleDebug" in " ".join(c) else 2)
    if not commands:
        commands=all_commands
    attempted=[]; success=False; code=1; last_log=""; successful=None
    for raw in commands:
        cmd=list(raw)
        if cmd and cmd[0].endswith("gradlew"):
            wrapper=Path(cmd[0])
            if wrapper.exists():
                wrapper.chmod(wrapper.stat().st_mode|0o111)
                cmd=["/bin/sh",str(wrapper)]+cmd[1:]
        attempted.append(cmd)
        code,last_log=run(cmd,root,logs/f"build-{int(time.time())}.log")
        if code==0:
            success=True; successful=cmd; break
    result={"project_root":str(root),"project_type":detect.get("project_type","unknown"),
            "build_succeeded":success,"successful_command":successful,"exit_code":code,
            "log_file":last_log,"build_commands_attempted":attempted,"requested_variant":a.variant}
    payload=json.dumps(result,indent=2)
    if a.output: Path(a.output).write_text(payload,encoding="utf-8")
    else: print(payload)
    gh=os.environ.get("GITHUB_OUTPUT")
    if gh:
        with open(gh,"a",encoding="utf-8") as f:
            f.write(f"build_succeeded={'true' if success else 'false'}\nexit_code={code}\nlog_file={last_log}\n")
            if successful: f.write(f"successful_command={' '.join(successful)}\n")
    return 0 if success else 1
if __name__=="__main__": sys.exit(main())
