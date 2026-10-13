"""Behavioral simulation. NEVER invokes real iptables or su. Python 3/Linux only."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

MOCK = r'''#!/usr/bin/env python3
import os,sys,json,fcntl
from pathlib import Path
p=Path(os.environ['MOCK_DIR'])
with open(p/'lock','w') as lock:
 fcntl.flock(lock,fcntl.LOCK_EX)
 state=json.loads((p/'state.json').read_text())
 family=Path(sys.argv[0]).name
 db=state[family]
 args=sys.argv[1:]
 if args[:2]==['-w','2']: args=args[2:]
 op,chain=args[:2]; rest=args[2:]; code=0; output=''
 if os.environ.get('FAIL_V6')=='1' and family=='ip6tables' and op=='-I':
  code=1
 elif op=='-N':
  if chain in db: code=1
  else: db[chain]=[]
 elif op in ('-A','-I'):
  if op=='-I' and rest[0]=='1': rest=rest[1:]
  if chain not in db: code=1
  elif op=='-I': db[chain].insert(0,rest)
  else: db[chain].append(rest)
 elif op=='-C': code=0 if rest in db.get(chain,[]) else 1
 elif op=='-D':
  if rest in db.get(chain,[]): db[chain].remove(rest)
  else: code=1
 elif op=='-F':
  if chain in db: db[chain]=[]
  else: code=1
 elif op=='-X':
  referenced=any(['-j',chain]==r[-2:] for rules in db.values() for r in rules)
  if chain not in db or db[chain] or referenced: code=1
  else: del db[chain]
 elif op in ('-S','-L'):
  if chain not in db: code=1
  else: output='\n'.join(' '.join(r) for r in db[chain])
 else: code=2
 (p/'state.json').write_text(json.dumps(state))
 print(output)
 sys.exit(code)
'''

class Harness:
    def __init__(self):
        self.root=Path(tempfile.mkdtemp(prefix='ndb-mock-'))
        self.bin=self.root/'bin'; self.bin.mkdir()
        self.script=self.root/'firewall-v3.sh'
        shutil.copyfile(Path(__file__).resolve().parents[1]/'app/src/main/assets/firewall-v3.sh', self.script)
        for name in ('iptables','ip6tables'):
            f=self.bin/name; f.write_text(MOCK); f.chmod(0o755)
        f=self.bin/'sleep'
        f.write_text('#!/usr/bin/env python3\nimport time,sys\ntime.sleep(float(sys.argv[1])*0.15)\n')
        f.chmod(0o755)
        self.original=[['-m','owner','--uid-owner','12345','-j','ACCEPT']]
        (self.root/'state.json').write_text(json.dumps({name:{'OUTPUT':self.original} for name in ('iptables','ip6tables')}))
        self.env=dict(os.environ,PATH=str(self.bin)+os.pathsep+os.environ['PATH'],MOCK_DIR=str(self.root))
    def run(self,mode,uid=11001,seconds=2,token='abcd',fail=False):
        env=dict(self.env)
        if fail: env['FAIL_V6']='1'
        return subprocess.run(['sh',str(self.script),mode,str(uid),str(seconds),token],env=env,capture_output=True,text=True,timeout=8)
    def state(self): return json.loads((self.root/'state.json').read_text())
    def clean(self):
        for db in self.state().values():
            assert db=={'OUTPUT':self.original},db
    def blocked(self,chain):
        for db in self.state().values():
            assert db[chain]==[['-j','DROP']],db
            assert ['-m','owner','--uid-owner','11001','-j',chain] in db['OUTPUT'],db

def main():
    h=Harness(); r=h.run('probe',seconds=0); assert r.returncode==0,r.stdout+r.stderr; h.clean()
    print('PASS root capability probe does not install blocking rules')
    h=Harness(); r=h.run('block'); assert r.returncode==0,r.stdout+r.stderr
    h.blocked('NDB3_11001_abcd'); time.sleep(1.2); h.clean()
    print('PASS both IP families blocked; normal expiry preserves unrelated rules')
    h=Harness(); r=h.run('block',fail=True); assert r.returncode!=0,r.stdout+r.stderr
    h.clean(); print('PASS IPv6 install failure rolls IPv4 back')
    h=Harness(); r=h.run('block',seconds=10); assert r.returncode==0,r.stdout+r.stderr
    r=h.run('clear',seconds=0); assert r.returncode==0,r.stdout+r.stderr; h.clean()
    print('PASS emergency clear removes only module rules and skips readiness files')
    h=Harness(); assert h.run('block',seconds=10,token='1111').returncode==0
    assert h.run('block',seconds=10,token='2222').returncode==0
    assert h.run('clear',seconds=0).returncode==0; h.clean()
    print('PASS repeated windows have independent chains; clear removes both')
    h=Harness(); assert h.run('block',uid='11001;false').returncode!=0; h.clean()
    assert h.run('block',token='a;false').returncode!=0; h.clean()
    assert h.run('block',uid=999).returncode!=0; h.clean()
    assert h.run('block',uid=101000).returncode!=0; h.clean()
    print('PASS malformed arguments and system UIDs rejected')
    # Let detached mock watchdogs finish before temporary test environments are removed.
    time.sleep(5)
    print('All behavioral simulations passed. No real phone/kernel firewall tested.')

if __name__=='__main__': main()
