"""Check class DEFINITIONS (not references) so legitimate Xposed imports pass."""
import struct
import sys
import zipfile

def defined_classes(dex):
    if not dex.startswith(b'dex\n'):
        raise ValueError('Unsupported DEX')
    u32=lambda offset: struct.unpack_from('<I',dex,offset)[0]
    strings=u32(60)
    types=u32(68)
    count=u32(96)
    classes=u32(100)
    for index in range(count):
        type_index=u32(classes+index*32)
        string_index=u32(types+type_index*4)
        pos=u32(strings+string_index*4)
        while dex[pos]&0x80: pos+=1
        pos+=1
        end=dex.index(b'\0',pos)
        yield dex[pos:end].decode('utf-8')

def check(path):
    with zipfile.ZipFile(path) as apk:
        dexes=[n for n in apk.namelist() if n.startswith('classes') and n.endswith('.dex')]
        if not dexes: raise ValueError('APK contains no classes.dex')
        classes=[c for name in dexes for c in defined_classes(apk.read(name))]
        forbidden=[c for c in classes if c.startswith('Lde/robv/android/xposed/') or c=='Landroid/app/AndroidAppHelper;']
        if forbidden: raise ValueError('Xposed API bundled into APK: '+', '.join(forbidden))
        if 'Lcom/example/netdelay/DelayHook;' not in classes: raise ValueError('Missing module entry point')
        if apk.read('assets/xposed_init').strip()!=b'com.example.netdelay.DelayHook':
            raise ValueError('Invalid xposed_init')
        if not apk.read('assets/firewall-v3.sh'): raise ValueError('Missing firewall script')
    print('PASS:',len(classes),'defined classes; no bundled Xposed API; module entry/assets present')

if __name__=='__main__':
    check(sys.argv[1])
