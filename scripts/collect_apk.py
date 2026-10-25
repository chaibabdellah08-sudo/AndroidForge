#!/usr/bin/env python3
"""Collect distributable Android build outputs; ignore intermediates and deduplicate by bytes."""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, sys
from pathlib import Path

def find_outputs(root: Path) -> list[Path]:
    items=[]
    for pattern in ("*.apk","*.aab"):
        for p in root.rglob(pattern):
            if not p.is_file() or p.stat().st_size == 0: continue
            parts=set(p.parts)
            if "intermediates" in parts or "tmp" in parts or "baselineProfiles" in parts: continue
            if "outputs" not in parts: continue
            items.append(p)
    return sorted(set(items), key=lambda p:str(p))

def variant_of(path: Path) -> str:
    n=path.name.lower()
    return "debug" if "debug" in n else "release" if "release" in n else "unknown"

def score(path: Path, variant: str) -> int:
    ext=path.suffix.lower(); v=variant_of(path)
    if variant=="bundle": return 0 if ext==".aab" and v=="release" else 10 if ext==".aab" else 50
    if variant=="debug": return 0 if ext==".apk" and v=="debug" else 10 if ext==".apk" else 50
    if variant=="release": return 0 if ext==".apk" and v=="release" else 10 if ext==".apk" else 50
    return 0 if ext==".apk" and v=="release" else 1 if ext==".apk" and v=="debug" else 20

def main() -> int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--root",required=True)
    parser.add_argument("--variant",default="auto",choices=["auto","debug","release","bundle"])
    parser.add_argument("--output-dir")
    parser.add_argument("--output-json")
    args=parser.parse_args()
    root=Path(args.root).resolve()
    output=Path(args.output_dir) if args.output_dir else Path(__file__).resolve().parent.parent/"output"
    output.mkdir(parents=True,exist_ok=True)
    for f in list(output.glob("*.apk"))+list(output.glob("*.aab")): f.unlink()
    unique=[]; seen=set()
    for p in find_outputs(root):
        try: digest=hashlib.sha256(p.read_bytes()).hexdigest()
        except OSError as exc:
            print(f"WARNING: cannot read {p}: {exc}",file=sys.stderr); continue
        if digest in seen: continue
        seen.add(digest); unique.append(p)
    primary=min(unique,key=lambda p:(score(p,args.variant),str(p))) if unique else None
    collected=[]
    for p in unique:
        ext=p.suffix.lower(); variant=variant_of(p); kind="aab" if ext==".aab" else "apk"
        base=f"AndroidForge-app-{variant}.{kind}" if variant!="unknown" else f"AndroidForge-app.{kind}"
        name=base
        if (output/name).exists():
            name=f"AndroidForge-app-{variant}-{hashlib.sha256(p.read_bytes()).hexdigest()[:8]}.{kind}"
        dest=output/name
        try: shutil.copy2(p,dest)
        except OSError as exc:
            print(f"WARNING: copy failed for {p}: {exc}",file=sys.stderr); continue
        collected.append({"source_path":str(p),"output_path":str(dest),"output_name":name,
                          "size_kb":round(dest.stat().st_size/1024,1),"is_primary":p==primary})
    primary_info=next((item for item in collected if item["is_primary"]),None)
    result={"project_root":str(root),"apk_count":len(collected),"primary":primary_info,
            "all_apks":collected,"output_directory":str(output)}
    payload=json.dumps(result,indent=2)
    if args.output_json: Path(args.output_json).write_text(payload,encoding="utf-8")
    else: print(payload)
    gh=os.environ.get("GITHUB_OUTPUT")
    if gh:
        with open(gh,"a",encoding="utf-8") as f:
            f.write(f"apk_count={len(collected)}\napk_directory={output}\n")
            if primary_info:
                f.write(f"primary_apk={primary_info['output_path']}\nprimary_apk_name={primary_info['output_name']}\n")
            f.write(f"json<<EOF\n{json.dumps(result)}\nEOF\n")
    return 0 if collected else 1
if __name__=="__main__": sys.exit(main())
