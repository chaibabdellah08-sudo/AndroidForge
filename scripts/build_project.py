#!/usr/bin/env python3
"""AndroidForge build runner."""
from __future__ import annotations
import argparse,json,os,subprocess,sys,time
from pathlib import Path
from typing import Any

def run(cmd:list[str],cwd:Path,log_file:Path,env:dict[str,str]|None=None)->tuple[int,str]:
    log_file.parent.mkdir(parents=True,exist_ok=True)
    print(f"\n=== Running: {' '.join(cmd)}",flush=True)
    print(f"    cwd: {cwd}\n    log: {log_file}",flush=True)
    full_env=os.environ.copy(); full_env.update(env or {})
    with log_file.open("w",encoding="utf-8",errors="replace") as f:
        f.write(f"$ {' '.join(cmd)}\n# cwd: {cwd}\n\n")
        p=subprocess.Popen(cmd,cwd=str(cwd),env=full_env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        assert p.stdout is not None
        for line in p.stdout:
            sys.stdout.write(line); sys.stdout.flush(); f.write(line)
        rc=p.wait()
    return rc,str(log_file)

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument("--root",required=True); ap.add_argument("--detect",required=True); ap.add_argument("--toolchain",required=True); ap.add_argument("--variant",default="auto",choices=["auto","debug","release","bundle"]); ap.add_argument("--log-dir"); ap.add_argument("--output")
    a=ap.parse_args()
    def load(s): return json.loads(Path(s).read_text()) if Path(s).exists() else json.loads(s)
    detect=load(a.detect); toolchain=load(a.toolchain); root=Path(a.root).resolve(); log_dir=Path(a.log_dir or root.parent/"androidforge-logs"); log_dir.mkdir(parents=True,exist_ok=True)
    commands=toolchain.get("build_commands",[])
    if a.variant!="auto":
        filtered=[c for c in commands if ((a.variant=="debug" and "debug" in " ".join(c).lower()) or (a.variant=="release" and "release" in " ".join(c).lower()) or (a.variant=="bundle" and "bundle" in " ".join(c).lower()))]
        if filtered: commands=filtered
    success=False; last_rc=1; last_log=""; successful=None
    for cmd in commands:
        cmd=list(cmd)
        if cmd and cmd[0].endswith("gradlew"):
            gp=Path(cmd[0]);
            if gp.exists(): gp.chmod(gp.stat().st_mode|0o111); cmd=["/bin/sh",str(gp)]+cmd[1:]
        rc,last_log=run(cmd,root,log_dir/f"build-{int(time.time())}.log"); last_rc=rc
        if rc==0: success=True; successful=cmd; break
    result={"project_root":str(root),"project_type":detect.get("project_type","unknown"),"build_succeeded":success,"successful_command":successful,"exit_code":last_rc,"log_file":last_log,"build_commands_attempted":commands}
    out=json.dumps(result,indent=2); Path(a.output).write_text(out) if a.output else print(out)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"],"a",encoding="utf-8") as f:
            f.write(f"build_succeeded={'true' if success else 'false'}\nexit_code={last_rc}\nlog_file={last_log}\n")
            if successful: f.write(f"successful_command={' '.join(successful)}\n")
    return 0 if success else 1
if __name__=="__main__": sys.exit(main())
