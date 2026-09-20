#!/usr/bin/env python3
"""Mutation coverage: each source contract must reject a removed repair."""
from pathlib import Path
import json
from check_recent_regressions import CONTRACTS, JAVA, clean, evaluate, load, region

root=Path(__file__).resolve().parents[1]
files=load(root)
failures=evaluate(files)
if failures:
    raise AssertionError('Baseline failed: '+str(failures))
results=[]
for ident,path,marker,required,forbidden in CONTRACTS:
    mutated=dict(files)
    src=clean(files[path])
    start=src.index(marker)
    opening=src.index('{',start)
    body=region(src,marker)
    poison=''
    if not required:
        poison='lastPowerMode = 0;'
    mutated[path]=src[:opening+1]+poison+src[opening+1+len(body):]
    caught=ident in evaluate(mutated)
    results.append({'id':ident,'caught':caught})
for path in ('web/src/App.vue','app/src/main/assets/toolbox_ui.html'):
    mutated=dict(files);mutated[path]='<style>button {color:white}</style>'
    results.append({'id':'button-focus:'+path,'caught':'button-focus:'+path in evaluate(mutated)})
mutated=dict(files);mutated[JAVA+'utils/AppLogger.java']=files[JAVA+'utils/AppLogger.java'].replace('lastLogTime.clear();','')
results.append({'id':'log-dedup-bounded','caught':'log-dedup-bounded' in evaluate(mutated)})
mutated=dict(files);mutated[JAVA+'utils/CarGearHALMonitor.java']='class CarGearHALMonitor {}'
results.append({'id':'retired-hal-listeners','caught':'retired-hal-listeners' in evaluate(mutated)})
# 发布说明：回退成硬编码固定文案必须被拦截
mutated=dict(files);mutated['scripts/publish_r2.py']=files['scripts/publish_r2.py'].replace('build_changelog(','hardcoded_changelog(')
results.append({'id':'changelog-from-vcs','caught':'changelog-from-vcs' in evaluate(mutated)})
mutated=dict(files)
mutated['scripts/publish_r2.py']=files['scripts/publish_r2.py']+'\nHARDCODED = "【测试通道优先体验 beta-v1.7.26.1】"\n'
results.append({'id':'changelog-no-hardcoded-version','caught':'changelog-no-hardcoded-version' in evaluate(mutated)})
mutated=dict(files);mutated['scripts/changelog_builder.py']=files['scripts/changelog_builder.py'].replace('def read_override(','def read_override_removed(')
results.append({'id':'changelog-builder-contract','caught':'changelog-builder-contract' in evaluate(mutated)})
print(json.dumps(results,ensure_ascii=False,indent=2))
assert all(r['caught'] for r in results), 'Some mutations escaped'
print(f'PASS {len(results)}/{len(results)} targeted mutations rejected')
