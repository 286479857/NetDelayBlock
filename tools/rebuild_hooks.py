"""Rebuild the included hook JAR from complete source. Requires desktop JDK/Python."""
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

root=Path(__file__).resolve().parents[1]
android=Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix='netdelay-hook-') as tmp:
    subprocess.run(['java','com.sun.tools.javac.Main','-source','7','-target','7',
        '-bootclasspath',str(android),'-classpath',str(root/'hook-source/api/xposed-api-82.jar'),
        '-d',tmp,str(root/'hook-source/src/com/example/netdelay/DelayHook.java')],check=True)
    with zipfile.ZipFile(root/'app/libs/netdelay-hooks.jar','w',zipfile.ZIP_DEFLATED) as jar:
        for f in sorted(Path(tmp).rglob('*.class')):
            name=f.relative_to(tmp).as_posix()
            if not name.startswith('com/example/netdelay/DelayHook'):
                raise ValueError('Unexpected class: '+name)
            jar.write(f,name)
print('Rebuilt app/libs/netdelay-hooks.jar; no Xposed API definitions included')
